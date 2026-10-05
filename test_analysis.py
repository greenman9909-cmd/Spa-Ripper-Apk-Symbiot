"""Regression fixtures for Android formats, dependency evidence, archives, CLI and HTTP."""
import contextlib
import hashlib
import io
import json
import random
import stat
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import analysis
import android_formats as formats
import server
import test_engine
from test_engine import fixture


def apk(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as archive:
        for path, value in entries.items():
            archive.writestr(path, value)
    return stream.getvalue()


def dex_fixture(values):
    """Standard little-endian 035 header + string IDs + MUTF-8 data."""
    header = bytearray(112)
    header[:8] = b'dex\n035\x00'
    data_start = 112 + 4 * len(values)
    index, body = bytearray(), bytearray()
    for value in values:
        index.extend(struct.pack('<I', data_start + len(body)))
        utf16 = value.encode('utf-16le')
        units = len(utf16) // 2
        while units >= 128:
            body.append((units & 127) | 128)
            units >>= 7
        body.append(units)
        encoded = utf16.decode('utf-16le', errors='surrogatepass')
        # Encode surrogate pairs individually as required by MUTF-8.
        encoded = ''.join(chr(struct.unpack_from('<H', utf16, i)[0]) for i in range(0, len(utf16), 2))
        body.extend(encoded.encode('utf-8', errors='surrogatepass').replace(b'\x00', b'\xc0\x80'))
        body.append(0)
    struct.pack_into('<III', header, 32, data_start + len(body), 112, 0x12345678)
    struct.pack_into('<II', header, 56, len(values), 112 if values else 0)
    struct.pack_into('<II', header, 104, len(body), data_start)
    result = header + index + body
    result[12:32] = hashlib.sha1(result[32:]).digest()
    import zlib
    struct.pack_into('<I', result, 8, zlib.adler32(result[12:]) & 0xffffffff)
    return bytes(result)


def binary_manifest(utf8=True, release_version=False):
    strings = ['manifest', 'package', 'dev.fixture', 'http://schemas.android.com/apk/res/android',
               'versionCode', 'application', 'debuggable']
    if release_version: strings += ['versionName','3.61.0']
    offsets, content = [], bytearray()
    for value in strings:
        offsets.append(len(content))
        encoded = value.encode('utf-8' if utf8 else 'utf-16le')
        if utf8:
            content.extend(bytes([len(value), len(encoded)]) + encoded + b'\x00')
        else:
            content.extend(struct.pack('<H', len(value)) + encoded + b'\x00\x00')
    content.extend(b'\x00' * ((-len(content)) % 4))
    start = 28 + 4 * len(strings)
    pool = struct.pack('<HHIIIIII', 1, 28, start + len(content), len(strings), 0, 256 if utf8 else 0, start, 0)
    pool += b''.join(struct.pack('<I', n) for n in offsets) + content
    none = 0xffffffff

    def begin(name, attrs):
        extension = struct.pack('<IIHHHHHH', none, name, 20, 20, len(attrs), 0, 0, 0)
        body = b''.join(struct.pack('<IIIHBBI', ns, key, raw, 8, 0, typ, value) for ns, key, raw, typ, value in attrs)
        return struct.pack('<HHIII', 0x102, 16, 16 + len(extension) + len(body), 1, none) + extension + body

    def end(name):
        return struct.pack('<HHIIIII', 0x103, 16, 24, 1, none, none, name)

    attrs = [(none, 1, 2, 3, 2), (3, 4, none, 16, 770 if release_version else 42)]
    if release_version: attrs.append((3,7,8,3,8))
    chunks = pool + begin(0, attrs)
    chunks += begin(5, [(3, 6, none, 18, 1)]) + end(5) + end(0)
    return struct.pack('<HHI', 3, 8, len(chunks) + 8) + chunks


class AndroidFormatTests(unittest.TestCase):
    def test_release_version_preserves_manifest_semantics(self):
        from build_preserved_apk import version_manifest
        for utf8 in (True,False):
            original=binary_manifest(utf8,release_version=True)
            patched=version_manifest(original)
            root=formats.decode_manifest(patched)
            self.assertEqual(root.get(formats.ANDROID+'versionName'),'0.4.8')
            self.assertEqual(root.get(formats.ANDROID+'versionCode'),'1000048')
            self.assertEqual(root.get('package'),'dev.fixture')
            self.assertEqual(root.find('application').get(formats.ANDROID+'debuggable'),'true')
            self.assertEqual(len(original),len(patched))
        with self.assertRaises(ValueError): version_manifest(binary_manifest())
    def test_real_binary_xml_utf8_and_utf16(self):
        for utf8 in (True, False):
            with self.subTest(utf8=utf8):
                root = formats.decode_manifest(binary_manifest(utf8))
                meta = formats.manifest_metadata(root)
                self.assertEqual(meta['package'], 'dev.fixture')
                self.assertEqual(meta['versionCode'], '42')
                self.assertEqual(meta['application']['debuggable'], 'true')

    def test_manifest_permissions_aliases_and_intents(self):
        root = formats.decode_manifest(b'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.app" android:versionName="2">
        <uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/>
        <uses-permission android:name="android.permission.INTERNET"/>
        <uses-permission-sdk-23 android:name="android.permission.CAMERA"/>
        <application android:label="@0x7f010001">
        <activity android:name=".Main"/>
        <activity-alias android:name="Launch" android:targetActivity=".Main" android:exported="true">
        <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/>
        <data android:scheme="demo" android:host="open"/></intent-filter></activity-alias>
        <service android:name="dev.app.Worker"/></application></manifest>''')
        meta = formats.manifest_metadata(root)
        self.assertEqual(meta['launchers'], ['dev.app.Launch'])
        self.assertEqual(meta['components'][1]['targetActivity'], 'dev.app.Main')
        self.assertEqual(meta['targetSdk'], '35')
        self.assertEqual(len(meta['permissions']), 2)
        self.assertEqual(meta['components'][1]['intentFilters'][0]['data'][0]['scheme'], 'demo')

    def test_xml_truncations_and_invalid_string_refs(self):
        data = binary_manifest()
        for i in range(len(data)):
            with self.subTest(offset=i), self.assertRaises(ValueError):
                formats.decode_manifest(data[:i])
        mutated = bytearray(data)
        # First string ID must point into string data, not beyond the pool.
        struct.pack_into('<I', mutated, 8 + 28, 0xffffffff)
        with self.assertRaises(ValueError):
            formats.decode_manifest(mutated)

    def test_unbalanced_xml(self):
        data = bytearray(binary_manifest())
        struct.pack_into('<I', data, len(data) - 4, 5)
        with self.assertRaisesRegex(ValueError, 'Unbalanced'):
            formats.decode_manifest(data)

    def test_entity_expansion_rejected(self):
        payload = '<!DOCTYPE manifest [<!ENTITY x "expanded">]><manifest package="&x;"/>'
        for data in (payload.encode(), payload.encode('utf-16')):
            with self.assertRaisesRegex(ValueError, 'entity'):
                formats.decode_manifest(data)

    def test_mutf8_including_null_and_supplementary_unicode(self):
        values = ['Lcom/facebook/react/ReactActivity;', 'nul\x00inside', 'emoji \U0001f600', 'été', 'a' * 200]
        self.assertEqual(formats.dex_strings(dex_fixture(values)), values)

    def test_dex_malformed_offsets_lengths_and_header(self):
        valid = dex_fixture(['demo'])
        for offset, value in ((60, 0xffffffff), (112, 0xffffffff), (32, 1), (40, 0), (104, 0xffffffff)):
            data = bytearray(valid)
            struct.pack_into('<I', data, offset, value)
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                formats.dex_strings(data)
        data = bytearray(valid)
        data[116] = 10
        with self.assertRaisesRegex(ValueError, 'length mismatch'):
            formats.dex_strings(data)
        for i in range(len(valid)):
            with self.subTest(truncation=i), self.assertRaises(ValueError):
                formats.dex_strings(valid[:i])

    def test_random_inputs_never_raise_raw_struct_or_index_errors(self):
        rng = random.Random(42)
        for _ in range(300):
            payload = bytes(rng.randrange(256) for _ in range(rng.randrange(1, 300)))
            for parser in (formats.decode_manifest, formats.dex_strings):
                try:
                    parser(payload)
                except ValueError:
                    pass


class EvidenceTests(unittest.TestCase):
    def inspect(self, entries):
        entries = {'AndroidManifest.xml': '<manifest package="dev.app"/>', **entries}
        session = server.inspect_apk(apk(entries))
        self.addCleanup(session['zip'].close)
        return session

    def test_framework_corroboration_and_endpoint_redaction(self):
        report = self.inspect({
            'classes.dex': dex_fixture(['Lcom/facebook/react/ReactActivity;', 'https://user:pass@api.example.test/v1?token=private#frag']),
            'assets/index.android.bundle': 'const endpoint="https://api.example.test/v1?token=private";',
            'lib/arm64-v8a/libreactnative.so': b'ELF',
        })['report']
        framework = report['frameworks'][0]
        self.assertEqual(framework['name'], 'React Native')
        self.assertEqual(framework['confidence'], 'corroborated')
        self.assertEqual(report['abis'], ['arm64-v8a'])
        self.assertEqual(report['endpoints'][0]['url'], 'https://api.example.test/v1')
        self.assertEqual(report['coverage']['dexFilesDecoded'], 1)
        self.assertFalse(report['coverage']['runtimeVerified'])

    def test_corrupt_manifest_and_dex_are_visible_gaps(self):
        report = self.inspect({'AndroidManifest.xml': b'\x03\x00\x08\x00', 'classes.dex': b'dex\n035\x00'})['report']
        self.assertEqual(report['manifest']['status'], 'unavailable')
        self.assertEqual(report['dex'][0]['status'], 'unavailable')
        self.assertEqual({w['code'] for w in report['warnings']}, {'manifest-unavailable', 'dex-unavailable'})
        self.assertTrue(report['integrity']['archiveCrcChecked'])

    def test_dependency_graph_cycles_and_sibling_export(self):
        session = self.inspect({
            'assets/www/index.html': '<link rel="stylesheet" href="style.css"><script type="module" src="app.js"></script>',
            'assets/www/style.css': 'body{background:url(../shared/cover.png)}',
            'assets/www/app.js': 'import "../shared/util.js";',
            'assets/shared/util.js': 'import "../www/app.js";',
            'assets/shared/cover.png': b'\x89PNG',
        })
        candidate = session['report']['candidates'][0]
        self.assertEqual(candidate['root'], 'assets')
        self.assertEqual(candidate['exportEntry'], 'www/index.html')
        self.assertEqual(candidate['dependencyCounts'], {'present': 5})
        self.assertEqual(candidate['analyzedFiles'], 4)
        output = server.export_web(session, candidate['entry'])
        self.assertEqual(output, server.export_web(session, candidate['entry']))
        with zipfile.ZipFile(io.BytesIO(output)) as recovered:
            self.assertEqual(recovered.read('web/shared/cover.png'), b'\x89PNG')
            self.assertIn(b'www/index.html', recovered.read('RUN.txt'))
            manifest = json.loads(recovered.read('recovery.json'))
            for file in manifest['files']:
                self.assertEqual(hashlib.sha256(recovered.read(file['output'])).hexdigest(), file['sha256'])

    def test_missing_external_root_relative_bridges_and_runtime(self):
        candidate = self.inspect({
            'assets/www/index.html': '<script src="/app.js"></script><img src="absent.png"><script src="https://cdn.example.test/sdk.js"></script>',
            'assets/www/app.js': 'cordova.exec();fetch("/api/data");import "react";',
        })['report']['candidates'][0]
        self.assertEqual(candidate['status'], 'needs-review')
        self.assertEqual(candidate['dependencyCounts'], {'present': 1, 'missing': 1, 'external': 1, 'unresolved-module': 1, 'runtime-dependent': 1})
        self.assertEqual(candidate['bridges'][0]['bridge'], 'Cordova')
        self.assertTrue(any(d.get('assumption') for d in candidate['dependencies']))

    def test_base_href_and_escaped_paths(self):
        candidate = self.inspect({
            'assets/www/index.html': '<base href="../shared/"><img src="cover%20art.png?x=1#image">',
            'assets/shared/cover art.png': b'image',
        })['report']['candidates'][0]
        self.assertEqual(candidate['root'], 'assets')
        self.assertEqual(candidate['dependencies'][0]['target'], 'assets/shared/cover art.png')
        self.assertEqual(candidate['dependencyCounts'], {'present': 1})

    def test_text_and_global_reference_limits_are_explicit(self):
        with patch.object(analysis, 'TEXT_LIMIT', 8):
            candidate = self.inspect({'assets/www/index.html': '<h1>too long</h1>'})['report']['candidates'][0]
            self.assertTrue(candidate['analysisGaps'])
            self.assertEqual(candidate['status'], 'needs-review')
        with patch.object(analysis, 'TOTAL_REFERENCES', 1):
            candidate = self.inspect({'assets/www/index.html': '<img src="a.png"><img src="b.png">'})['report']['candidates'][0]
            self.assertTrue(candidate['analysisGaps'])
            self.assertEqual(len(candidate['dependencies']), 1)

    def test_explicit_config_findings_do_not_infer_defaults(self):
        report = self.inspect({'AndroidManifest.xml': '''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.app">
        <application android:debuggable="true"><activity android:name=".Main"/><receiver android:name=".Receiver" android:exported="true"/></application></manifest>'''})['report']
        self.assertEqual([f['code'] for f in report['findings']], ['debuggable', 'exported-component'])

    def test_apk_hash_and_every_member_hash(self):
        report = self.inspect({'assets/index.html': '<h1>demo</h1>'})['report']
        for file in report['files']:
            self.assertEqual(len(file['sha256']), 64)
        self.assertEqual(report['schemaVersion'], 2)
        self.assertFalse(report['integrity']['signatureVerified'])


class ArchiveAndCLITests(unittest.TestCase):
    def test_misleading_filename_cannot_pass_expected_package_check(self):
        data = apk({'AndroidManifest.xml': '<manifest package="com.uptodown"/>'})
        with self.assertRaisesRegex(ValueError, 'expected com.crunchyroll.crunchyroid, found com.uptodown'):
            server.inspect_apk(data, 'crunchyroll.apk', expected_package='com.crunchyroll.crunchyroid')
        session = server.inspect_apk(data, expected_package='com.uptodown')
        self.addCleanup(session['zip'].close)
        self.assertEqual(session['report']['manifest']['package'], 'com.uptodown')

    def test_non_apk_compression_rejected(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w', zipfile.ZIP_BZIP2) as archive:
            archive.writestr('AndroidManifest.xml', '<manifest/>')
        with self.assertRaisesRegex(ValueError, 'DEFLATE'):
            server.inspect_apk(stream.getvalue())

    def test_corrupt_crc_rejected_at_ingest(self):
        data = fixture().replace(b'document.body.dataset.loaded="yes";', b'document.body.dataset.loaded="noo";')
        with self.assertRaises(zipfile.BadZipFile):
            server.inspect_apk(data)

    def test_noncanonical_paths_file_conflicts_and_duplicate_paths(self):
        for path in ('assets//a', 'assets/./a', 'assets/control\x01', 'assets/a/../b'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                server.inspect_apk(fixture({path: 'bad'}))
        with self.assertRaisesRegex(ValueError, 'conflicting'):
            server.inspect_apk(fixture({'assets/www': 'file'}))
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            archive.writestr('AndroidManifest.xml', '<manifest/>')
            with self.assertWarns(UserWarning):
                archive.writestr('AndroidManifest.xml', '<manifest/>')
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            server.inspect_apk(stream.getvalue())

    def test_symbolic_link_rejected(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            archive.writestr('AndroidManifest.xml', '<manifest/>')
            link = zipfile.ZipInfo('assets/link')
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(link, '../outside')
        with self.assertRaisesRegex(ValueError, 'symbolic'):
            server.inspect_apk(stream.getvalue())

    def test_nonportable_exports_rejected_without_losing_inventory(self):
        for entries in ({'assets/www/A.js': 'a', 'assets/www/a.js': 'b'},
                        {'assets/www/CON.txt': 'reserved'}, {'assets/www/unsafe:name': 'colon'},
                        {'assets/www/Folder': 'file', 'assets/www/folder/file.js': 'collision'}):
            session = server.inspect_apk(fixture(entries))
            self.addCleanup(session['zip'].close)
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                server.export_web(session, 'assets/www/index.html')

    def test_cli_inspect_report_and_export(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source, report, export = directory/'app.apk', directory/'report.json', directory/'web.zip'
            source.write_bytes(fixture())
            result = server.main(['--inspect', str(source), '--report', str(report), '--export', str(export)])
            self.assertEqual(result, 0)
            self.assertEqual(json.loads(report.read_text())['manifest']['package'], 'dev.apkforge.demo')
            with zipfile.ZipFile(export) as archive:
                self.assertIn('recovery.json', archive.namelist())
            before = source.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as exc:
                server.main(['--inspect', str(source), '--report', str(source)])
            self.assertEqual(exc.exception.code, 2)
            self.assertEqual(source.read_bytes(), before)

    def test_cli_invalid_input_is_a_clean_error(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)/'bad.apk'
            source.write_bytes(b'bad')
            errors = io.StringIO()
            with contextlib.redirect_stderr(errors), self.assertRaises(SystemExit) as exc:
                server.main(['--inspect', str(source)])
            self.assertEqual(exc.exception.code, 2)
            self.assertIn('Inspection failed', errors.getvalue())


class ExtendedHTTPTests(test_engine.HTTPTests):
    def upload(self):
        status, _, body = self.request('POST', '/api/upload', fixture(), {'X-APKForge-Token': server.TOKEN})
        self.assertEqual(status, 200)
        return json.loads(body)['session']

    def test_corrupt_upload_returns_json_error(self):
        data = fixture().replace(b'document.body.dataset.loaded="yes";', b'document.body.dataset.loaded="noo";')
        status, _, body = self.request('POST', '/api/upload', data, {'X-APKForge-Token': server.TOKEN})
        self.assertEqual(status, 400)
        self.assertIn('error', json.loads(body))

    def test_manifest_download_and_expired_session(self):
        sid = self.upload()
        status, headers, body = self.request('GET', '/manifest/'+sid+'/AndroidManifest.xml')
        self.assertEqual(status, 200)
        self.assertEqual(headers['Content-Type'], 'application/xml')
        self.assertIn(b'dev.apkforge.demo', body)
        status, _, body = self.request('GET', '/report/missing/analysis.json')
        self.assertEqual(status, 404)
        self.assertIn('expired', json.loads(body)['error'])

    def test_export_preflight_accepts_valid_and_rejects_missing_entry(self):
        sid = self.upload()
        status, _, body = self.request('GET', '/export/'+sid+'/check?entry=assets%2Fwww%2Findex.html')
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body), {'entry': 'index.html', 'files': 3})
        status, _, body = self.request('GET', '/export/'+sid+'/check?entry=missing.html')
        self.assertEqual(status, 400)
        self.assertIn('error', json.loads(body))

    def test_origin_rejected_and_upload_concurrency_bounded(self):
        status, _, _ = self.request('POST', '/api/upload', fixture(), {'X-APKForge-Token': server.TOKEN, 'Origin': 'null'})
        self.assertEqual(status, 403)
        with patch.object(server, 'UPLOAD_SLOTS', threading_semaphore()):
            status, _, body = self.request('POST', '/api/upload', fixture(), {'X-APKForge-Token': server.TOKEN})
        self.assertEqual(status, 429)
        self.assertIn('error', json.loads(body))

    def test_session_eviction_closes_old_archive(self):
        with server.LOCK:
            for session in server.SESSIONS.values():
                session['zip'].close()
            server.SESSIONS.clear()
        first = self.upload()
        old = server.SESSIONS[first]
        for _ in range(3):
            self.upload()
        self.assertNotIn(first, server.SESSIONS)
        self.assertIsNone(old['zip'].fp)
        self.assertEqual(len(server.SESSIONS), 3)


def threading_semaphore():
    import threading
    return threading.Semaphore(0)


if __name__ == '__main__':
    unittest.main()
