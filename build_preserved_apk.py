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
import zipfile

from server import inspect_apk

ROOT = Path(__file__).resolve().parent
SOURCE_SHA256 = '9f44b888baf558269eb79a868854ee9f0004a11704df0eafa00d95453f0e87e1'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)


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
    ]
    for relative, reason in paths:
        path = copied[relative]
        before = path.read_bytes(); text = before.decode('utf-8').replace('\r\n', '\n')
        if relative.startswith('s70/'):
            text = replace_method(text, 'public final b()V', '    .locals 0\n    return-void')
        elif relative.endswith('SharedPreferencesTokenStorage.smali'):
            text = replace_method(text, 'public getRefreshToken()Ljava/lang/String;', '    .locals 1\n    const-string v0, "apkforge-local-guest"\n    return-object v0')
            text = replace_method(text, 'public isPresent()Z', '    .locals 1\n    const/4 v0, 0x1\n    return v0')
        else:
            anchor = '.method private final varargs addInterceptors(Lme0/y$a;[Lme0/u;)Lme0/y$a;\n    .locals 4'
            if text.count(anchor) != 1: raise ValueError('Original network builder does not match recipe')
            text = text.replace(anchor, anchor + '\n\n    invoke-static {p1}, Ldev/apkforge/bridge/BackendBridge;->install(Ljava/lang/Object;)V')
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
            if name not in ('classes2.dex', 'stamp-cert-sha256') and not member['preserved']:
                raise ValueError(f'Unexpected change to original APK member: {name}')
            if name == 'stamp-cert-sha256' and final_data is None:
                member['reason'] = 'Original SourceStamp removed by Android signer for the new signing key'
    report = {'inputSha256': sha(original), 'outputSha256': sha(result.read_bytes()), 'patchedOriginalClasses': records,
              'newDex': 'classes5.dex', 'originalMemberPreservation': mapping,
              'originalUiCodeChanged': False, 'runtimeVerified': False,
              'migrationComplete': False, 'limitations': ['Native API adapter is incomplete', 'Playback and account/profile APIs still require integration', 'Local signing key differs from official app']}
    result.with_suffix('.provenance.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f'Original-code test build: {result}')
    print('Retained every original UI resource and all original screen implementations. Backend migration is incomplete.')


if __name__ == '__main__':
    main()
