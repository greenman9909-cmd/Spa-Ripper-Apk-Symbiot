"""Experimental original-code backend migration. Retains all original UI/resources.

This recipe targets the supplied 3.61.0 APK only. It is not a complete migration;
unmapped native API routes return explicit errors and are recorded by Android.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import struct
import zipfile
import xml.etree.ElementTree as ET

from server import inspect_apk
from android_formats import ANDROID, decode_manifest, strings_pool

ROOT = Path(__file__).resolve().parent
SOURCE_SHA256 = '9f44b888baf558269eb79a868854ee9f0004a11704df0eafa00d95453f0e87e1'
BUILD_VERSION = '0.4.5'
BUILD_CODE = 1000045


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)


def version_manifest(data, version=BUILD_VERSION, code=BUILD_CODE):
    """Change only root version attributes; retain every component and permission."""
    before = decode_manifest(data)
    if before.get(ANDROID+'versionName') != '3.61.0' or before.get(ANDROID+'versionCode') != '770':
        raise ValueError('Unexpected original version metadata')
    if not re.fullmatch(r'[0-9.]{1,6}', version) or not 770 < code < 2147483647:
        raise ValueError('Invalid release version')
    output = bytearray(data); pos = 8; pool = None; values = []
    changed = set()
    while pos < len(data):
        typ, header, size = struct.unpack_from('<HHI', data, pos)
        if typ == 1:
            pool = pos; values = strings_pool(data, pos)
        elif typ == 0x102:
            base = pos+header
            _, name, start, width, count = struct.unpack_from('<IIHHH', data, base)
            if values[name] == 'manifest':
                for i in range(count):
                    at = base+start+i*width
                    ns, key, raw, _, _, kind, value = struct.unpack_from('<IIIHBBI', data, at)
                    if ns == 0xffffffff or values[ns] != ANDROID[1:-1]: continue
                    if values[key] == 'versionCode':
                        if kind != 16 or raw != 0xffffffff: raise ValueError('Unexpected version code encoding')
                        struct.pack_into('<I', output, at+16, code); changed.add('code')
                    elif values[key] == 'versionName':
                        if kind != 3 or values[value] != '3.61.0': raise ValueError('Unexpected version name encoding')
                        flags, string_start = struct.unpack_from('<II', data, pool+16)
                        offset = struct.unpack_from('<I', data, pool+28+value*4)[0]
                        entry = pool+string_start+offset
                        if flags & 256:
                            old = b'\x06\x06' + b'3.61.0\x00'
                            new = bytes([len(version),len(version)])+version.encode('ascii')+b'\x00'
                        else:
                            old = b'\x06\x00'+'3.61.0'.encode('utf-16le')+b'\x00\x00'
                            new = struct.pack('<H',len(version))+version.encode('utf-16le')+b'\x00\x00'
                        if data[entry:entry+len(old)] != old: raise ValueError('Unexpected version string entry')
                        output[entry:entry+len(old)] = new.ljust(len(old),b'\x00'); changed.add('name')
        pos += size
    if changed != {'code','name'}: raise ValueError('Missing version attributes')
    after = decode_manifest(bytes(output))
    if after.get(ANDROID+'versionName') != version or after.get(ANDROID+'versionCode') != str(code):
        raise ValueError('Version rewrite did not decode correctly')
    for key in ('versionName','versionCode'): after.set(ANDROID+key,before.get(ANDROID+key))
    if ET.tostring(before) != ET.tostring(after): raise ValueError('Unexpected manifest change beyond release version')
    return bytes(output)


def replace_method(text, signature, body):
    pattern = r'(?m)^\.method ' + re.escape(signature) + r'\r?\n.*?^\.end method'
    updated, count = re.subn(pattern, '.method ' + signature + '\n' + body + '\n.end method', text, flags=re.S)
    if count != 1:
        raise ValueError(f'Expected exactly one original method: {signature}')
    return updated


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reference-apk', type=Path, required=True)
    p.add_argument('--decoded', type=Path, required=True, help='Full Apktool 3.0.3 decode with smali')
    p.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', Path.home() / 'AppData/Local/Android/Sdk')))
    args = p.parse_args()
    original = args.reference_apk.resolve().read_bytes()
    if sha(original) != SOURCE_SHA256:
        p.error('This native recipe targets one verified input APK; other versions require a new recipe')
    inspect_apk(original, args.reference_apk.name, expected_package='com.crunchyroll.crunchyroid')
    work = ROOT / 'android-build/original-ui'; work.mkdir(parents=True, exist_ok=True)
    source = args.decoded.resolve() / 'smali_classes2'
    # Class descriptors live inside smali; flatten filenames to support Windows
    # without changing the machine's long-path settings or the original decode.
    patched = work / 'smali-flat'; patched.mkdir(exist_ok=True)
    copied = {}
    for file in sorted(source.rglob('*.smali')):
        relative = file.relative_to(source).as_posix()
        target = patched / (sha(relative.encode()) + '.smali')
        readable = Path('\\\\?\\' + str(file)) if os.name == 'nt' else file
        content = readable.read_bytes()
        if not target.exists() or target.read_bytes() != content:
            target.write_bytes(content)
        copied[relative] = target
    records = []
    paths = [
        ('s70/e.smali', 'inactive-client monitor'),
        ('com/ellation/crunchyroll/api/etp/OkHttpClientFactory.smali', 'backend adapter install'),
        ('com/ellation/crunchyroll/api/etp/auth/SharedPreferencesTokenStorage.smali', 'optional local guest session for replacement backend'),
        ('cr/l.smali', 'replacement HLS stream resolver in original player backend'),
        ('cr/g.smali', 'HLS mapping into original native player models'),
        ('ll/a.smali', 'provider headers on original media data source'),
    ]
    for relative, reason in paths:
        path = copied[relative]
        before = path.read_bytes(); text = before.decode('utf-8').replace('\r\n', '\n')
        if relative.startswith('s70/'):
            text = replace_method(text, 'public final b()V', '    .locals 0\n    return-void')
        elif relative.endswith('SharedPreferencesTokenStorage.smali'):
            text = replace_method(text, 'public getRefreshToken()Ljava/lang/String;', '    .locals 1\n    const-string v0, "apkforge-local-guest"\n    return-object v0')
            text = replace_method(text, 'public isPresent()Z', '    .locals 1\n    const/4 v0, 0x1\n    return v0')
        elif relative == 'cr/l.smali':
            text = replace_method(text, 'public final k(Lcom/ellation/crunchyroll/model/PlayableAsset;ZLzc0/d;)Ljava/io/Serializable;', '''    .locals 1
    invoke-static {p1, p3}, Ldev/apkforge/bridge/NativePlayback;->streamsAsync(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Ljava/io/Serializable;
    return-object v0''')
        elif relative == 'cr/g.smali':
            anchor = '.method public final a(Ljava/lang/String;Lcom/ellation/crunchyroll/api/cms/model/streams/Streams;Ljg/d;Ljava/lang/String;)Lbl/c;\n    .locals 14'
            if text.count(anchor) != 1: raise ValueError('Original streams mapper does not match recipe')
            text = text.replace(anchor, anchor + '''
    invoke-static/range {p1 .. p4}, Ldev/apkforge/bridge/NativePlayback;->mapHls(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    if-eqz v0, :apkforge_original_mapper
    check-cast v0, Lbl/c;
    return-object v0
    :apkforge_original_mapper''')
        elif relative == 'll/a.smali':
            anchor = '    iput-object v2, v0, Lll/a;->h:Lk5/f$a;'
            if text.count(anchor) != 1: raise ValueError('Original media factory does not match recipe')
            text = text.replace(anchor, '    invoke-static {v2}, Ldev/apkforge/bridge/NativePlayback;->configureMediaFactory(Ljava/lang/Object;)V\n\n' + anchor)
        else:
            anchor = '.method private final varargs addInterceptors(Lme0/y$a;[Lme0/u;)Lme0/y$a;\n    .locals 4'
            if text.count(anchor) != 1: raise ValueError('Original network builder does not match recipe')
            start=text.index(anchor);end=text.index('.end method',start)
            method=text[start:end]
            if method.count('    return-object p1')!=1: raise ValueError('Original network builder return does not match recipe')
            # Keep original authentication, account-state and error interceptors
            # around replacement responses. An early terminal adapter skips them.
            method=method.replace('    return-object p1','    invoke-static {p1}, Ldev/apkforge/bridge/BackendBridge;->install(Ljava/lang/Object;)V\n\n    return-object p1')
            text=text[:start]+method+text[end:]
        path.write_text(text, encoding='utf-8', newline='\n')
        records.append({'class': relative, 'reason': reason, 'originalSmaliSha256': sha(before), 'patchedSmaliSha256': sha(path.read_bytes())})
    jar = ROOT / '.tools/apktool.jar'
    assembly_fingerprint = sha(json.dumps(records, sort_keys=True).encode() + (ROOT / 'native_adapter/Assemble.java').read_bytes())
    cache_file = work / 'assembly.json'
    cache = json.loads(cache_file.read_text()) if cache_file.exists() else {}
    if not (cache.get('fingerprint') == assembly_fingerprint and (work / 'classes2.dex').exists() and sha((work / 'classes2.dex').read_bytes()) == cache.get('dexSha256')):
        run('javac', '-classpath', jar, '-d', work, ROOT / 'native_adapter/Assemble.java')
        run('java', '-Xmx2g', '-classpath', str(work) + os.pathsep + str(jar), 'Assemble', patched, work / 'classes2.dex')
        cache_file.write_text(json.dumps({'fingerprint': assembly_fingerprint, 'dexSha256': sha((work / 'classes2.dex').read_bytes())}))
    classes = work / 'bridge-classes'; classes.mkdir(exist_ok=True)
    android_jar = args.sdk / 'platforms/android-37.0/android.jar'
    bt = args.sdk / 'build-tools/36.0.0'
    run('javac', '--release', '8', '-classpath', android_jar, '-d', classes, *sorted((ROOT / 'native_adapter/src').rglob('*.java')))
    with zipfile.ZipFile(work / 'bridge.jar', 'w') as archive:
        for file in sorted(classes.rglob('*.class')): archive.write(file, file.relative_to(classes).as_posix())
    dex = work / 'bridge-dex'; dex.mkdir(exist_ok=True)
    run(bt / 'd8.bat', '--min-api', '26', '--lib', android_jar, '--output', dex, work / 'bridge.jar')
    mapping = []
    import io
    with zipfile.ZipFile(io.BytesIO(original)) as source_apk, zipfile.ZipFile(work / 'unsigned.apk', 'w') as output:
        for info in source_apk.infolist():
            name = info.filename
            if re.match(r'^META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))$', name, re.I): continue
            data = source_apk.read(info)
            updated = (work / 'classes2.dex').read_bytes() if name == 'classes2.dex' else data
            if name == 'AndroidManifest.xml': updated = version_manifest(data)
            output.writestr(info, updated)
            mapping.append({'path': name, 'originalSha256': sha(data), 'outputSha256': sha(updated), 'preserved': data == updated})
        output.write(dex / 'classes.dex', 'classes5.dex')
    run(bt / 'zipalign.exe', '-f', '-p', '4', work / 'unsigned.apk', work / 'aligned.apk')
    key = ROOT / 'android-build/prototype.jks'
    if not key.exists():
        run('keytool', '-genkeypair', '-keystore', key, '-alias', 'prototype', '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650', '-dname', 'CN=APKForge Local Prototype, O=Local Development, C=US')
    result = ROOT / 'android-build/Original-UI-AniPM.apk'
    run(bt / 'apksigner.bat', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--out', result, work / 'aligned.apk')
    run(bt / 'apksigner.bat', 'verify', '--verbose', result)
    # Signing can remove an old SourceStamp certificate. Report the final signed
    # archive rather than assuming unsigned ZIP members survived signing.
    with zipfile.ZipFile(result) as signed:
        names = set(signed.namelist())
        for member in mapping:
            name = member['path']
            final_data = signed.read(name) if name in names else None
            member['outputSha256'] = sha(final_data) if final_data is not None else None
            member['preserved'] = member['outputSha256'] == member['originalSha256']
            if name not in ('classes2.dex', 'AndroidManifest.xml', 'stamp-cert-sha256') and not member['preserved']:
                raise ValueError(f'Unexpected change to original APK member: {name}')
            if name == 'stamp-cert-sha256' and final_data is None:
                member['reason'] = 'Original SourceStamp removed by Android signer for the new signing key'
    report = {'inputSha256': sha(original), 'outputSha256': sha(result.read_bytes()), 'patchedOriginalClasses': records,
              'newDex': 'classes5.dex', 'originalMemberPreservation': mapping,
              'versionName': BUILD_VERSION, 'versionCode': BUILD_CODE,
              'manifestChanges': ['Root versionName/versionCode only; decoded manifest otherwise identical'],
              'originalUiCodeChanged': False, 'runtimeVerified': False,
              'migrationComplete': False, 'limitations': ['Native API adapter is incomplete', 'Playback and account/profile APIs still require integration', 'Local signing key differs from official app']}
    result.with_suffix('.provenance.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f'Original-code test build: {result}')
    print('Retained every original UI resource and all original screen implementations. Backend migration is incomplete.')


if __name__ == '__main__':
    main()
