"""Evidence and recovery diagnostics over an already validated APK archive."""
import posixpath
import re
from collections import Counter, deque
from html.parser import HTMLParser
from urllib.parse import unquote, urljoin, urlsplit, urlunsplit

from android_formats import decode_manifest, manifest_metadata, dex_strings

TEXT_LIMIT = 1024 * 1024
TEXT_BUDGET = 16 * 1024 * 1024
DEX_BUDGET = 64 * 1024 * 1024
MAX_REFERENCES = 10000
MAX_CANDIDATES = 200
MAX_EVIDENCE = 500
TOTAL_REFERENCES = 20000
URL_PATTERN = re.compile(r'https?://[^\s<>"\'\\]+')
CSS_REFS = re.compile(r'url\(\s*["\']?([^)"\']+)["\']?\s*\)|@import\s+["\']([^"\']+)["\']', re.I)
JS_REFS = re.compile(r'(?:\bimport\s*(?:[^;\n]{0,200}?\bfrom\s*)?|\bexport\s+[^;\n]{0,200}?\bfrom\s*|\bimport\s*\(\s*|\brequire\s*\(\s*)["\']([^"\']+)["\']')
FETCH_REFS = re.compile(r'\b(?:fetch|importScripts)\s*\(\s*["\']([^"\']+)["\']')
BRIDGES = {'Cordova': re.compile(r'\bcordova\.exec\s*\('),
           'Capacitor': re.compile(r'\bCapacitor\.(?:Plugins|registerPlugin|isNativePlatform)\b'),
           'Android JavaScript bridge': re.compile(r'\b(?:window\.)?Android\.[A-Za-z_$]\w*\s*\('),
           'React Native WebView bridge': re.compile(r'\bReactNativeWebView\.postMessage\s*\(')}
CLASS_MARKERS = {'Flutter': 'Lio/flutter/', 'React Native': 'Lcom/facebook/react/',
                 'Cordova': 'Lorg/apache/cordova/', 'Capacitor': 'Lcom/getcapacitor/'}


class References(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs, self.base, self.inline, self.inside = [], None, [], None
        self.truncated = False

    def add(self, value, kind='asset'):
        if not value:
            return
        if len(self.refs) >= MAX_REFERENCES:
            self.truncated = True
        else:
            self.refs.append((value.strip(), kind))

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'base' and self.base is None:
            self.base = attrs.get('href')
        if tag in {'script', 'style'}:
            self.inside = tag
        for key in ('src', 'poster'):
            self.add(attrs.get(key))
        if tag == 'link':
            self.add(attrs.get('href'))
        if tag in {'a', 'area'}:
            self.add(attrs.get('href'), 'navigation')
        if tag in {'object', 'embed'}:
            self.add(attrs.get('data'))
        # The data URL form of srcset contains commas; leave it to the runtime.
        if attrs.get('srcset') and 'data:' not in attrs['srcset']:
            for value in attrs['srcset'].split(','):
                self.add(value.strip().split()[0] if value.strip() else '')
        for match in CSS_REFS.finditer(attrs.get('style', '')):
            self.add(match.group(1) or match.group(2))

    def handle_endtag(self, tag):
        if tag == self.inside:
            self.inside = None

    def handle_data(self, data):
        if self.inside:
            self.inline.append(data)


def references(text, suffix):
    base, refs, truncated = None, [], False
    if suffix in {'.html', '.htm'}:
        parser = References()
        parser.feed(text)
        base, refs, truncated = parser.base, parser.refs, parser.truncated
        script_text = '\n'.join(parser.inline)
    else:
        script_text = text
    for pattern, kind in ((CSS_REFS, 'asset'), (JS_REFS, 'module'), (FETCH_REFS, 'runtime')):
        for match in pattern.finditer(script_text):
            if len(refs) >= MAX_REFERENCES:
                truncated = True
                break
            refs.append((next(g for g in match.groups() if g is not None).strip(), kind))
    return refs, base, truncated


def public_url(value):
    try:
        url = urlsplit(value)
        if url.scheme not in {'http', 'https'} or not url.hostname:
            return None
        # Endpoint hints are static strings; omit credentials and query values.
        host = '[' + url.hostname + ']' if ':' in url.hostname else url.hostname
        if url.port:
            host += ':' + str(url.port)
        return urlunsplit((url.scheme, host, url.path, '', ''))
    except ValueError:
        return None


def evidence_analysis(archive, files, html):
    names = {f['path'] for f in files}
    warnings, evidence, dex_reports, endpoints, texts = [], [], [], {}, {}
    truncated_outputs = set()
    framework = {}

    def signal(label, path, basis, detail):
        item = {'framework': label, 'path': path, 'basis': basis, 'detail': detail}
        framework.setdefault(label, set()).add(basis)
        if len(evidence) < MAX_EVIDENCE:
            evidence.append(item)
        else:
            truncated_outputs.add('framework evidence')

    def endpoint(value, path, basis):
        value = public_url(value.rstrip(').,;'))
        if value and (value in endpoints or len(endpoints) < 500):
            item = endpoints.setdefault(value, {'url': value, 'evidence': []})
            if len(item['evidence']) < 5:
                item['evidence'].append({'path': path, 'basis': basis})
        elif value:
            truncated_outputs.add('endpoint hints')

    manifest = {'status': 'unavailable'}
    manifest_info = next(f for f in files if f['path'] == 'AndroidManifest.xml')
    try:
        if manifest_info['bytes'] > 4 * 1024 * 1024:
            raise ValueError('Manifest exceeds the 4 MB analysis limit')
        root = decode_manifest(archive.read('AndroidManifest.xml'))
        manifest = {'status': 'decoded', **manifest_metadata(root)}
        for component in manifest['components']:
            for label, prefix in CLASS_MARKERS.items():
                if (component['name'] or '').startswith(prefix[1:].replace('/', '.')):
                    signal(label, 'AndroidManifest.xml', 'manifest-component', component['name'])
    except ValueError as exc:
        manifest['error'] = str(exc)
        warnings.append({'code': 'manifest-unavailable', 'path': 'AndroidManifest.xml', 'message': str(exc)})

    text_budget, dex_budget = TEXT_BUDGET, DEX_BUDGET
    text_paths = sorted(files, key=lambda f: (f['path'] not in html, f['path']))
    for f in text_paths:
        path = f['path']
        suffix = posixpath.splitext(path)[1].lower()
        if 'flutter_assets/' in path or path.endswith('/libflutter.so'):
            signal('Flutter', path, 'archive-marker', 'Flutter asset or native library marker')
        if path.endswith(('index.android.bundle', '/libreactnative.so', '/libreactnativejni.so')):
            signal('React Native', path, 'archive-marker', 'React Native bundle or library marker')
        if path.endswith('/cordova.js'):
            signal('Cordova', path, 'archive-marker', 'Cordova bridge filename')
        if path.endswith(('/capacitor.config.json', '/capacitor.js')):
            signal('Capacitor', path, 'archive-marker', 'Capacitor configuration or bridge filename')
        if suffix == '.dex':
            result = {'path': path}
            if f['bytes'] > dex_budget:
                result.update(status='skipped', reason='64 MB aggregate DEX analysis budget')
            else:
                dex_budget -= f['bytes']
                try:
                    strings = dex_strings(archive.read(path))
                    result.update(status='decoded', stringCount=len(strings))
                    matched = set()
                    for value in strings:
                        for label, prefix in CLASS_MARKERS.items():
                            if label not in matched and value.startswith(prefix) and value.endswith(';'):
                                signal(label, path, 'dex-class-reference', value[:300])
                                matched.add(label)
                        for match in URL_PATTERN.finditer(value):
                            endpoint(match.group(), path, 'dex-string')
                except ValueError as exc:
                    result.update(status='unavailable', reason=str(exc))
            dex_reports.append(result)
            if result['status'] != 'decoded':
                warnings.append({'code': 'dex-unavailable', 'path': path, 'message': result['reason']})
        if path.startswith('assets/') and (suffix in {'.html', '.htm', '.js', '.mjs', '.css', '.json'} or path.endswith('index.android.bundle')):
            if f['bytes'] > TEXT_LIMIT or f['bytes'] > text_budget:
                warnings.append({'code': 'text-skipped', 'path': path, 'message': '1 MB per-file / 16 MB total text analysis limit'})
                continue
            text_budget -= f['bytes']
            text = archive.read(path).decode('utf-8-sig', errors='replace')
            texts[path] = text
            for match in URL_PATTERN.finditer(text):
                endpoint(match.group(), path, 'asset-string')

    parsed = {p: references(t, posixpath.splitext(p)[1].lower()) for p, t in texts.items()
              if posixpath.splitext(p)[1].lower() in {'.html', '.htm', '.js', '.mjs', '.css'}}
    candidates = []
    reference_budget = TOTAL_REFERENCES
    traversal_budget = 5000
    if len(html) > MAX_CANDIDATES:
        warnings.append({'code': 'entries-truncated', 'message': f'Analyzing first {MAX_CANDIDATES} of {len(html)} HTML entries'})
    for entry in html[:MAX_CANDIDATES]:
        # Conventional roots are assumptions, and are explicitly exposed in the report.
        parts = entry.split('/')
        root = '/'.join(parts[:2]) if len(parts) > 2 and parts[1] in {'www', 'public', 'web'} else posixpath.dirname(entry)
        queue, visited, dependencies, bridges, gaps = deque([entry]), set(), [], [], []
        while queue and len(dependencies) < MAX_REFERENCES and reference_budget and traversal_budget:
            path = queue.popleft()
            if path in visited:
                continue
            visited.add(path)
            traversal_budget -= 1
            if path not in parsed:
                if path in texts:
                    continue
                gaps.append(path)
                continue
            refs, base, truncated = parsed[path]
            if truncated:
                gaps.append(path + ' (reference limit)')
            for label, pattern in BRIDGES.items():
                if pattern.search(texts[path]):
                    bridges.append({'bridge': label, 'path': path, 'basis': 'static-call-pattern'})
            source = 'https://apkforge.invalid/' + path
            if base:
                source = urljoin(source, base)
            for raw, kind in refs:
                if len(dependencies) >= MAX_REFERENCES or not reference_budget:
                    break
                if not raw or raw.startswith(('#', 'data:', 'blob:', 'javascript:', 'mailto:', 'tel:')):
                    continue
                item = {'source': path, 'reference': raw[:1000], 'kind': kind}
                try:
                    url = urlsplit(urljoin(source, raw))
                    if urlsplit(raw).netloc or url.hostname != 'apkforge.invalid' or url.scheme not in {'http', 'https'}:
                        item['status'] = 'external'
                    elif kind == 'module' and not raw.startswith(('.', '/')) and not urlsplit(raw).scheme:
                        item['status'] = 'unresolved-module'
                    else:
                        if raw.startswith('/') and not raw.startswith('//') and not base:
                            target = posixpath.normpath(root + '/' + unquote(urlsplit(raw).path).lstrip('/'))
                            item['assumption'] = 'Root-relative URL resolved against inferred web root'
                        else:
                            target = posixpath.normpath(unquote(url.path).lstrip('/'))
                        item['target'] = target
                        if not target.startswith('assets/'):
                            item['status'] = 'outside-assets'
                        elif target in names:
                            item['status'] = 'present'
                            if target in parsed:
                                queue.append(target)
                        else:
                            item['status'] = 'runtime-dependent' if kind == 'runtime' else 'missing'
                except ValueError:
                    item['status'] = 'invalid-url'
                dependencies.append(item)
                reference_budget -= 1
        if queue or not reference_budget or not traversal_budget:
            gaps.append('Dependency traversal stopped at the reference or file visit limit')
        present = [d['target'] for d in dependencies if d['status'] == 'present']
        # Keep containing folders intact, widening to include referenced sibling assets.
        export_root = posixpath.commonpath([root, *(posixpath.dirname(p) for p in present)])
        if not export_root.startswith('assets'):
            export_root = 'assets'
        if export_root != root and any('assumption' in d for d in dependencies):
            gaps.append('Export root widened; root-relative references may require adaptation')
        states = dict(Counter(d['status'] for d in dependencies))
        blocked = bool(gaps or bridges or any(d['status'] != 'present' or 'assumption' in d for d in dependencies))
        candidates.append({
            'entry': entry, 'root': export_root, 'inferredWebRoot': root,
            'mode': 'web-assets', 'status': 'needs-review' if blocked else 'static-dependencies-present',
            'dependencies': dependencies, 'dependencyCounts': states, 'bridges': bridges,
            'analysisGaps': gaps, 'analyzedFiles': len(visited),
            'exportEntry': posixpath.relpath(entry, export_root),
            'explanation': 'Static references checked; runtime behavior has not been verified.',
        })
    candidates.sort(key=lambda c: (posixpath.basename(c['entry']).lower() != 'index.html', len(c['entry']), c['entry']))

    findings = []
    if manifest['status'] == 'decoded':
        for attribute, message in (('debuggable', 'Application explicitly enables debugging'),
                                   ('usesCleartextTraffic', 'Application explicitly allows cleartext traffic')):
            if manifest['application'].get(attribute) == 'true':
                findings.append({'code': attribute, 'message': message, 'path': 'AndroidManifest.xml', 'basis': 'explicit-manifest-attribute'})
        if manifest.get('split'):
            findings.append({'code': 'split-package', 'message': 'This is a split APK; companion APKs may be required', 'path': 'AndroidManifest.xml', 'basis': 'manifest-split'})
        for c in manifest['components']:
            if c['exported'] == 'true':
                findings.append({'code': 'exported-component', 'message': f"{c['type']} {c['name']} is explicitly exported",
                                 'path': 'AndroidManifest.xml', 'basis': 'explicit-manifest-attribute',
                                 'permission': c['permission']})
    abis = sorted({p.split('/')[1] for p in names if p.startswith('lib/') and len(p.split('/')) == 3 and p.endswith('.so')})
    for output in sorted(truncated_outputs):
        warnings.append({'code': 'output-truncated', 'message': output + ' exceeded the bounded report limit'})
    return {
        'manifest': manifest, 'dex': dex_reports, 'abis': abis, 'evidence': evidence,
        'frameworks': [{'name': label, 'confidence': 'corroborated' if len(bases) > 1 else 'single-source',
                        'evidenceTypes': sorted(bases)} for label, bases in sorted(framework.items())],
        'endpoints': sorted(endpoints.values(), key=lambda e: e['url']),
        'candidates': candidates, 'warnings': warnings, 'findings': findings,
        'coverage': {'htmlEntriesFound': len(html), 'htmlEntriesAnalyzed': len(candidates),
                     'textFilesAnalyzed': len(texts), 'dexFilesFound': len(dex_reports),
                     'dexFilesDecoded': sum(d['status'] == 'decoded' for d in dex_reports),
                     'manifestDecoded': manifest['status'] == 'decoded',
                     'runtimeVerified': False},
    }
