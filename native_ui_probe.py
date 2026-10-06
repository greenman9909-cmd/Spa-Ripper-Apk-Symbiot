"""Inspect or exercise the original UI only in the explicitly named isolated AVD."""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PACKAGE = 'com.crunchyroll.crunchyroid'


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', default='emulator-5560')
    p.add_argument('--expected-avd', default='APKForge_Original_UI')
    p.add_argument('--text')
    p.add_argument('--description')
    p.add_argument('--resource', help='One visible native resource-id')
    p.add_argument('--class-name', help='Disambiguate a visible target by its observed native class')
    p.add_argument('--type-text', help='ASCII fixture text for one visible non-password input')
    p.add_argument('--output', type=Path)
    args = p.parse_args()
    if sum(value is not None for value in (args.text,args.description,args.resource)) > 1:
        p.error('Choose one text, description or resource-id')
    if not args.serial.startswith('emulator-'):
        p.error('An isolated emulator is required')

    def adb(*command):
        return subprocess.check_output(['adb', '-s', args.serial, *command], text=True, encoding='utf-8', timeout=35)

    name = adb('emu', 'avd', 'name').splitlines()[0].strip()
    if name != args.expected_avd:
        p.error(f'Expected isolated AVD {args.expected_avd}, got {name}')

    def snapshot():
        path = '/sdcard/apkforge-native-ui.xml'
        output = adb('shell', 'uiautomator', 'dump', path)
        if 'UI hierchary dumped to:' not in output:
            raise RuntimeError('UI snapshot failed; refusing to use a stale tree')
        xml = adb('exec-out', 'cat', path)
        tree = ET.fromstring(xml)
        for node in tree.iter('node'):
            if node.get('password') == 'true':
                node.set('text', '[password hidden]')
                node.set('content-desc', '')
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(ET.tostring(tree, encoding='unicode'), encoding='utf-8')
        return tree

    tree = snapshot()
    attr, value = ('text', args.text) if args.text is not None else ('resource-id', args.resource) if args.resource is not None else ('content-desc', args.description)
    if value is not None:
        parents = {child: parent for parent in tree.iter() for child in parent}
        targets = {}
        for node in tree.iter('node'):
            if node.get(attr) != value or node.get('package') != PACKAGE:
                continue
            if args.class_name and node.get('class') != args.class_name:
                continue
            visible = tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))
            if len(visible) != 4 or visible[2] <= visible[0] or visible[3] <= visible[1]:
                continue
            candidate = node
            while candidate is not None and candidate.get('clickable') != 'true':
                candidate = parents.get(candidate)
            if candidate is None or candidate.get('enabled') != 'true' or candidate.get('package') != PACKAGE:
                continue
            bounds = tuple(map(int, re.findall(r'\d+', candidate.get('bounds', ''))))
            if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                targets[bounds] = candidate
        if len(targets) != 1:
            p.error(f'Expected one current clickable target for {value!r}, found {len(targets)}')
        x1, y1, x2, y2 = next(iter(targets))
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        tree = snapshot()
    if args.type_text is not None:
        if not re.fullmatch(r'[A-Za-z0-9 ]{1,100}',args.type_text):
            p.error('Fixture input allows only ASCII letters, numbers and spaces')
        inputs=[node for node in tree.iter('node') if node.get('package')==PACKAGE
                and node.get('class')=='android.widget.EditText' and node.get('enabled')=='true'
                and node.get('password')!='true']
        if len(inputs)!=1:
            p.error(f'Expected one non-password native text input, found {len(inputs)}')
        bounds=tuple(map(int,re.findall(r'\d+',inputs[0].get('bounds',''))))
        if len(bounds)!=4 or bounds[2]<=bounds[0] or bounds[3]<=bounds[1]:
            p.error('Input is not visible')
        adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
        adb('shell','input','text',args.type_text.replace(' ','%s'))
        tree=snapshot()
    for node in tree.iter('node'):
        text, desc = node.get('text', ''), node.get('content-desc', '')
        if node.get('password') == 'true':
            text = '[password hidden]'
        if text or desc:
            print(text[:100], desc[:100], node.get('bounds'))


if __name__ == '__main__':
    main()
