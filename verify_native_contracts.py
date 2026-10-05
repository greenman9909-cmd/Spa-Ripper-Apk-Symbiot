"""Run adapter payloads through the original Android model classes in an isolated emulator."""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def run(*args, capture=False):
    result = subprocess.run([str(arg) for arg in args], cwd=ROOT, check=False,
                            capture_output=capture, text=capture, timeout=90)
    if result.returncode:
        raise RuntimeError(f'Native probe command failed ({result.returncode}): {result.stdout or ""}\n{result.stderr or ""}')
    return result.stdout if capture else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='emulator-5560')
    parser.add_argument('--expected-avd', default='APKForge_Original_UI')
    parser.add_argument('--cloud-negative', action='store_true', help='Also verify live invalid-login and private-data rejection; creates no account')
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', Path.home() / 'AppData/Local/Android/Sdk')))
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('This model probe is intended for an isolated emulator')
    name = run('adb', '-s', args.serial, 'emu', 'avd', 'name', capture=True).splitlines()[0].strip()
    if name != args.expected_avd:
        parser.error(f'Expected isolated AVD {args.expected_avd}, got {name}')
    work = ROOT / 'android-build/model-contracts'
    work.mkdir(parents=True, exist_ok=True)
    classes = work / 'classes'
    classes.mkdir(exist_ok=True)
    android_jar = args.sdk / 'platforms/android-37.0/android.jar'
    run('javac', '--release', '8', '-classpath', str(android_jar) + os.pathsep + str(ROOT / 'android-build/original-ui/bridge-classes'),
        '-d', classes, ROOT / 'native_adapter/tests/dev/apkforge/bridge/ModelContractProbe.java')
    with zipfile.ZipFile(work / 'probe.jar', 'w') as jar:
        for file in classes.rglob('*.class'):
            jar.write(file, file.relative_to(classes).as_posix())
    run(args.sdk / 'build-tools/36.0.0/d8.bat', '--min-api', '26', '--lib', android_jar, '--output', work, work / 'probe.jar')
    remote = '/data/local/tmp/apkforge-model-contracts'
    run('adb', '-s', args.serial, 'shell', 'mkdir', '-p', remote)
    run('adb', '-s', args.serial, 'push', ROOT / 'android-build/Original-UI-AniPM.apk', remote + '/app.apk')
    run('adb', '-s', args.serial, 'push', work / 'classes.dex', remote + '/probe.dex')
    result = run('adb', '-s', args.serial, 'shell', 'env', 'CLASSPATH=' + remote + '/app.apk:' + remote + '/probe.dex',
                 'app_process', '/', 'dev.apkforge.bridge.ModelContractProbe', *(['--cloud-negative'] if args.cloud_negative else []), capture=True)
    if 'Native model contracts passed: 29' not in result:
        raise RuntimeError('Native model contracts did not report success: ' + result)
    print(result.strip())


if __name__ == '__main__':
    main()
