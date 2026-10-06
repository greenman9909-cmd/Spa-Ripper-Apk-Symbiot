#!/usr/bin/env python3
"""APKForge: validated APK inspection and evidence-backed web recovery. Python 3.10+."""
import argparse
import hashlib
import io
import json
import mimetypes
import re
import secrets
import stat
import threading
import zipfile
import zlib
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit, parse_qs, quote

from analysis import evidence_analysis

MAX_UPLOAD = 256 * 1024 * 1024
MAX_EXPANDED = 512 * 1024 * 1024
MAX_ENTRY = 32 * 1024 * 1024
MAX_ENTRIES = 20000
MAX_SESSION_BYTES = 512 * 1024 * 1024
SESSIONS = {}
LOCK = threading.RLock()
UPLOAD_SLOTS = threading.BoundedSemaphore(2)
TOKEN = secrets.token_urlsafe(24)
WEB_EXT = {'.html', '.htm', '.js', '.mjs', '.css', '.json', '.map'}
ARCHIVE_ERRORS = (ValueError, RuntimeError, zipfile.BadZipFile, NotImplementedError, EOFError, OSError, zlib.error)


def inspect_apk(data, name='app.apk', expected_package=None):
    if len(data) > MAX_UPLOAD:
        raise ValueError('APK exceeds the 256 MB upload limit.')
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as exc:
        raise ValueError('This file is not a valid APK / ZIP archive.') from exc
    try:
        infos = archive.infolist()
        if len(infos) > MAX_ENTRIES or sum(i.file_size for i in infos) > MAX_EXPANDED:
            raise ValueError('Archive exceeds the extraction limits.')
        names, file_names = set(), set()
        for info in infos:
            raw = info.orig_filename
            path = PurePosixPath(raw)
            canonical = str(path) + ('/' if info.is_dir() else '')
            if (not path.parts or path.is_absolute() or '..' in path.parts or '\\' in raw
                    or re.match(r'^[A-Za-z]:', raw) or any(ord(c) < 32 for c in raw)
                    or raw != canonical):
                raise ValueError('Archive contains an unsafe or noncanonical path.')
            if str(path) in names:
                raise ValueError('Archive contains duplicate paths.')
            names.add(str(path))
            if not info.is_dir():
                file_names.add(raw)
            if info.flag_bits & 1:
                raise ValueError('Encrypted archive entries are unsupported.')
            if info.compress_type not in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}:
                raise ValueError('Only stored and DEFLATE APK entries are supported.')
            if stat.S_ISLNK(info.external_attr >> 16):
                raise ValueError('Archive symbolic links are unsupported.')
            if info.file_size > MAX_ENTRY:
                raise ValueError('An archive entry exceeds the 32 MB limit.')
            if info.is_dir() and info.file_size:
                raise ValueError('Archive directory entry contains data.')
        for name_in_archive in names:
            if any(str(parent) in file_names for parent in PurePosixPath(name_in_archive).parents):
                raise ValueError('Archive contains conflicting file and directory paths.')
        if 'AndroidManifest.xml' not in file_names:
            raise ValueError('No AndroidManifest.xml found. Supply an APK, not a generic ZIP.')
        files, expanded = [], 0
        # Read each member to EOF to validate CRC and actual sizes, with bounded buffers.
        for info in infos:
            digest, actual = hashlib.sha256(), 0
            with archive.open(info) as member:
                while True:
                    chunk = member.read(1024 * 1024)
                    if not chunk:
                        break
                    actual += len(chunk)
                    expanded += len(chunk)
                    if actual > MAX_ENTRY or expanded > MAX_EXPANDED:
                        raise ValueError('Archive exceeds the actual expansion limits.')
                    digest.update(chunk)
            if actual != info.file_size:
                raise ValueError('Archive entry size does not match its metadata.')
            if not info.is_dir():
                files.append({'path': info.filename, 'bytes': actual, 'compressedBytes': info.compress_size,
                              'kind': kind(info.filename), 'sha256': digest.hexdigest()})
        files.sort(key=lambda f: f['path'])
        html = sorted((f['path'] for f in files if PurePosixPath(f['path']).suffix.lower() in {'.html', '.htm'}
                       and f['path'].startswith('assets/')),
                      key=lambda p: (PurePosixPath(p).name.lower() != 'index.html', len(p), p))
        analysis = evidence_analysis(archive, files, html)
        actual_package = analysis['manifest'].get('package')
        if expected_package and actual_package != expected_package:
            raise ValueError(f'APK identity mismatch: expected {expected_package}, found {actual_package or "an undecodable package"}. The filename is not proof of app identity.')
        signals = [f['name'] for f in analysis['frameworks']]
        if html:
            signals.append('Bundled HTML')
        report = {
            'schemaVersion': 2, 'name': str(name)[:180],
            'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data), 'expandedBytes': expanded,
            'files': files, 'counts': dict(Counter(f['kind'] for f in files)),
            'signals': signals or ['No known framework marker'],
            'integrity': {'status': 'verified', 'filesHashed': len(files), 'archiveCrcChecked': True,
                          'signatureVerified': False},
            'nativeRuntime': 'Not implemented: use Android Emulator for native execution.',
            **analysis,
            'limitations': [
                'Static inventory and dependency presence do not prove runtime or visual accuracy.',
                'Framework evidence identifies packaged SDK references, not necessarily the app rendering engine.',
                'Compiled resources, APK signatures, DEX instructions, and native code are not decoded or verified.',
                'Dynamic imports, computed URLs, resource IDs, split dependencies, and remote services may remain unresolved.',
                'Preview blocks network requests and isolates scripts. Storage and native bridges may not work.',
                'Endpoint strings are hints, not verified APIs; query values and credentials are omitted.',
            ],
        }
        return {'zip': archive, 'report': report}
    except Exception:
        archive.close()
        raise


def kind(name):
    ext = PurePosixPath(name).suffix.lower()
    if ext in {'.png', '.jpg', '.jpeg', '.webp', '.gif', '.svg', '.avif'}:
        return 'Images'
    if ext in WEB_EXT:
        return 'Web'
    if ext in {'.ttf', '.otf', '.woff', '.woff2'}:
        return 'Fonts'
    if ext == '.dex':
        return 'DEX'
    if ext == '.so':
        return 'Native'
    if ext in {'.mp3', '.ogg', '.wav', '.mp4', '.webm'}:
        return 'Media'
    if name == 'resources.arsc' or name.startswith('res/'):
        return 'Resources'
    return 'Other'


def portable_export_paths(files, root):
    seen = set()
    reserved = {'CON', 'PRN', 'AUX', 'NUL', *(f'COM{i}' for i in range(1, 10)), *(f'LPT{i}' for i in range(1, 10))}
    for file in files:
        relative = file['path'][len(root):]
        parts = PurePosixPath(relative).parts
        if any(re.search(r'[<>:"|?*]', p) or p.endswith((' ', '.')) or p.split('.')[0].upper() in reserved for p in parts):
            raise ValueError('Web assets contain paths that cannot be safely exported on Windows: ' + file['path'])
        key = relative.casefold()
        if key in seen:
            raise ValueError('Web assets contain case-colliding export paths.')
        seen.add(key)
    for key in seen:
        if any(str(parent) in seen for parent in PurePosixPath(key).parents):
            raise ValueError('Web assets contain case-colliding file and directory paths.')


def export_plan(session, entry):
    candidate = next((c for c in session['report']['candidates'] if c['entry'] == entry), None)
    if not candidate:
        raise ValueError('Choose a detected HTML entry point.')
    root = candidate['root'] + '/'
    files = [f for f in session['report']['files'] if f['path'].startswith(root)]
    portable_export_paths(files, root)
    return candidate, root, files


def export_web(session, entry):
    candidate, root, files = export_plan(session, entry)
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
        def write(path, data):
            info = zipfile.ZipInfo(path, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
        recovery = []
        for file in files:
            output = 'web/' + file['path'][len(root):]
            write(output, session['zip'].read(file['path']))
            recovery.append({'source': file['path'], 'output': output, 'bytes': file['bytes'], 'sha256': file['sha256']})
        write('analysis.json', json.dumps(session['report'], indent=2))
        write('recovery.json', json.dumps({'schemaVersion': 1, 'apkSha256': session['report']['sha256'],
                                         'entry': 'web/' + candidate['exportEntry'], 'files': recovery,
                                         'candidate': candidate}, indent=2))
        write('RUN.txt', 'Recovered files retain their original bytes. Runtime behavior is unverified.\n'
              'In the web folder run: python -m http.server 8000 --bind 127.0.0.1\n'
              'Open http://127.0.0.1:8000/' + quote(candidate['exportEntry'], safe='/') + '\n'
              'See recovery.json for file hashes, dependencies, native bridges, and analysis gaps.\n'
              'Exported apps have normal browser behavior and may call original remote services.\n'
              'Root-relative URLs and native plugins may require adaptation.\n')
    return out.getvalue()


def remember_session(session):
    sid = secrets.token_hex(12)
    with LOCK:
        while SESSIONS and (len(SESSIONS) >= 3 or sum(s['report']['bytes'] for s in SESSIONS.values()) + session['report']['bytes'] > MAX_SESSION_BYTES):
            SESSIONS.pop(next(iter(SESSIONS)))['zip'].close()
        SESSIONS[sid] = session
    return sid


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(30)

    def log_message(self, *args):
        pass

    def send(self, status, body, mime='application/json', extra=None):
        if isinstance(body, dict):
            body = json.dumps(body).encode()
        if isinstance(body, str):
            body = body.encode()
        self.send_response(status)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def allowed(self):
        return self.headers.get('Host') == self.server.local_host

    def do_POST(self):
        self.close_connection = True
        if (not self.allowed() or self.headers.get('X-APKForge-Token') != TOKEN
                or self.headers.get('Origin') not in {None, 'http://' + self.server.local_host}):
            return self.send(403, {'error': 'Invalid local request.'})
        if self.path != '/api/upload':
            return self.send(404, {'error': 'Not found'})
        if not UPLOAD_SLOTS.acquire(blocking=False):
            return self.send(429, {'error': 'Two uploads are already being analyzed. Try again shortly.'})
        try:
            if self.headers.get('Transfer-Encoding') or len(self.headers.get_all('Content-Length', [])) != 1:
                raise ValueError('Supply one Content-Length; chunked uploads are unsupported.')
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= MAX_UPLOAD:
                raise ValueError('Upload must be between 1 byte and 256 MB.')
            data = self.rfile.read(size)
            if len(data) != size:
                raise ValueError('Upload was interrupted.')
            session = inspect_apk(data, unquote(self.headers.get('X-Filename', 'app.apk')))
            sid = remember_session(session)
            self.send(200, dict(session['report'], session=sid))
        except ARCHIVE_ERRORS as exc:
            self.send(400, {'error': str(exc)})
        finally:
            UPLOAD_SLOTS.release()

    def do_GET(self):
        if not self.allowed():
            return self.send(403, {'error': 'Invalid host.'})
        try:
            url = urlsplit(self.path)
        except ValueError:
            return self.send(400, {'error': 'Invalid URL.'})
        if url.path == '/':
            page = (Path(__file__).parent / 'index.html').read_text(encoding='utf-8').replace('__TOKEN__', TOKEN)
            return self.send(200, page, 'text/html; charset=utf-8', {
                'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-src 'self'; object-src 'none'; frame-ancestors 'none'",
            })
        parts = url.path.split('/')
        if len(parts) < 4 or parts[1] not in {'content', 'export', 'report', 'manifest'}:
            return self.send(404, {'error': 'Not found'})
        try:
            # Keep a lease on the session for reads/export; eviction cannot close a ZIP mid-read.
            with LOCK:
                session = SESSIONS.get(parts[2])
                if not session:
                    return self.send(404, {'error': 'Session expired. Upload again.'})
                if parts[1] == 'report':
                    body, mime, extra = session['report'], 'application/json', {'Content-Disposition': 'attachment; filename="analysis.json"'}
                elif parts[1] == 'export':
                    entry = parse_qs(url.query).get('entry', [''])[0]
                    if parts[3] == 'check':
                        candidate, _, files = export_plan(session, entry)
                        body, mime, extra = {'entry': candidate['exportEntry'], 'files': len(files)}, 'application/json', {}
                    else:
                        body, mime, extra = export_web(session, entry), 'application/zip', {'Content-Disposition': 'attachment; filename="apkforge-web.zip"'}
                elif parts[1] == 'manifest':
                    if session['report']['manifest']['status'] != 'decoded':
                        return self.send(422, {'error': 'Manifest decoding is unavailable; inspect the analysis warning.'})
                    from android_formats import decode_manifest
                    import xml.etree.ElementTree as ET
                    body = ET.tostring(decode_manifest(session['zip'].read('AndroidManifest.xml')), encoding='utf-8', xml_declaration=True)
                    mime, extra = 'application/xml', {'Content-Disposition': 'attachment; filename="AndroidManifest.xml"'}
                else:
                    path = unquote('/'.join(parts[3:]))
                    if path not in session['zip'].NameToInfo or session['zip'].getinfo(path).is_dir():
                        return self.send(404, {'error': 'Asset not found'})
                    mime = mimetypes.guess_type(path)[0] or 'application/octet-stream'
                    policy = "default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; sandbox allow-scripts"
                    body, extra = session['zip'].read(path), {'Content-Security-Policy': policy}
            return self.send(200, body, mime, extra)
        except ARCHIVE_ERRORS as exc:
            return self.send(400, {'error': str(exc)})


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8787)
    parser.add_argument('--inspect', type=Path, metavar='APK', help='Inspect an APK without starting the server')
    parser.add_argument('--report', type=Path, metavar='JSON', help='Save the analysis (otherwise printed to stdout)')
    parser.add_argument('--export', type=Path, metavar='ZIP', help='Export a bundled web candidate')
    parser.add_argument('--entry', help='Candidate APK path, such as assets/www/index.html')
    parser.add_argument('--expected-package', help='Require the decoded Android package to match this identity')
    args = parser.parse_args(argv)
    if not args.inspect and (args.report or args.export or args.entry or args.expected_package):
        parser.error('--report, --export, --entry, and --expected-package require --inspect')
    if args.inspect:
        session = None
        try:
            with args.inspect.open('rb') as source:
                data = source.read(MAX_UPLOAD + 1)
            session = inspect_apk(data, args.inspect.name, args.expected_package)
            bundle = None
            if args.export:
                candidates = session['report']['candidates']
                entry = args.entry
                if not entry:
                    if len(candidates) != 1:
                        raise ValueError('Use --entry to choose from the report candidates.')
                    entry = candidates[0]['entry']
                bundle = export_web(session, entry)
            for output in (args.report, args.export):
                if output and (output.resolve() == args.inspect.resolve() or output.exists()):
                    raise ValueError('Output already exists or would overwrite the input: ' + str(output))
            if args.report and args.export and args.report.resolve() == args.export.resolve():
                raise ValueError('Report and export must use different output paths.')
            report = json.dumps(session['report'], indent=2)
            if args.export:
                with args.export.open('xb') as output:
                    output.write(bundle)
            if args.report:
                with args.report.open('x', encoding='utf-8') as output:
                    output.write(report + '\n')
            else:
                print(report)
            return 0
        except ARCHIVE_ERRORS as exc:
            parser.exit(2, 'Inspection failed: ' + str(exc) + '\n')
        finally:
            if session:
                session['zip'].close()
    if not 0 <= args.port <= 65535:
        parser.error('--port must be between 0 and 65535')
    http = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
    http.local_host = f'127.0.0.1:{http.server_port}'
    print(f'APKForge ready: http://{http.local_host}\nCtrl+C to stop. Last 3 sessions, at most 512 MB of APK data, remain in memory.', flush=True)
    try:
        http.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        http.server_close()
        with LOCK:
            for session in SESSIONS.values():
                session['zip'].close()
            SESSIONS.clear()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
