"""Bounded, read-only Android binary XML and DEX inspection (no execution)."""
import re
import struct
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
MAX_STRINGS = 200000
MAX_XML_NODES = 50000


def unpack(data, fmt, offset, end=None):
    limit = len(data) if end is None else end
    if offset < 0 or offset + struct.calcsize(fmt) > limit:
        raise ValueError('Truncated Android structure')
    return struct.unpack_from(fmt, data, offset)


def u32(data, offset):
    return unpack(data, '<I', offset)[0]


def strings_pool(data, start):
    typ, header, size = unpack(data, '<HHI', start)
    end = start + size
    if typ != 1 or header < 28 or size < header or end > len(data):
        raise ValueError('Invalid string pool header')
    count, styles, flags, strings_start, styles_start = unpack(data, '<IIIII', start + 8, end)
    index_end = start + header + 4 * (count + styles)
    base = start + strings_start
    text_end = start + styles_start if styles_start else end
    if count > MAX_STRINGS or not index_end <= base <= text_end <= end:
        raise ValueError('Invalid string pool bounds')

    def length(pos, utf8):
        fmt, high, mask, shift, step = ('<B', 128, 127, 8, 1) if utf8 else ('<H', 32768, 32767, 16, 2)
        n = unpack(data, fmt, pos, text_end)[0]
        pos += step
        if n & high:
            n = ((n & mask) << shift) | unpack(data, fmt, pos, text_end)[0]
            pos += step
        return n, pos

    result = []
    for i in range(count):
        pos = base + unpack(data, '<I', start + header + 4 * i, end)[0]
        if not base <= pos < text_end:
            raise ValueError('Invalid string offset')
        utf8 = bool(flags & 256)
        units, pos = length(pos, utf8)
        n, pos = length(pos, True) if utf8 else (units * 2, pos)
        terminator = b'\x00' if utf8 else b'\x00\x00'
        if pos + n + len(terminator) > text_end or data[pos + n:pos + n + len(terminator)] != terminator:
            raise ValueError('Unterminated string pool entry')
        try:
            value = data[pos:pos + n].decode('utf-8' if utf8 else 'utf-16le')
        except UnicodeError as exc:
            raise ValueError('Invalid string pool encoding') from exc
        if len(value.encode('utf-16le')) // 2 != units:
            raise ValueError('String pool length mismatch')
        result.append(value)
    return result


def decode_manifest(data):
    if len(data) > 4 * 1024 * 1024:
        raise ValueError('Manifest exceeds the 4 MB analysis limit')
    # Android manifests do not need DTDs. Reject before ElementTree expands entities.
    declarations = data.upper().replace(b'\x00', b'')
    if b'<!DOCTYPE' in declarations or b'<!ENTITY' in declarations:
        raise ValueError('Manifest entity declarations are unsupported')
    if data.lstrip().startswith(b'<') or data.startswith((b'\xff\xfe', b'\xfe\xff', b'\xef\xbb\xbf')):
        try:
            root = ET.fromstring(data)
        except ET.ParseError as exc:
            raise ValueError('Invalid text manifest XML') from exc
        if root.tag != 'manifest':
            raise ValueError('Expected a manifest root element')
        if sum(1 for _ in root.iter()) > MAX_XML_NODES:
            raise ValueError('Manifest node limit exceeded')
        return root
    typ, header, total = unpack(data, '<HHI', 0)
    if typ != 3 or header != 8 or total != len(data):
        raise ValueError('Unsupported or truncated binary XML')
    strings, stack, root, nodes, pos = [], [], None, 0, header

    def string(idx, optional=False):
        if optional and idx == 0xffffffff:
            return ''
        if idx >= len(strings):
            raise ValueError('Invalid XML string reference')
        return strings[idx]

    def value(raw, vtype, n):
        if raw != 0xffffffff:
            return string(raw)
        if vtype == 3:
            return string(n)
        if vtype == 18:
            return 'true' if n else 'false'
        if vtype == 16:
            return str(n if n < 0x80000000 else n - 0x100000000)
        if vtype == 17:
            return hex(n)
        return ('@0x%08x' if vtype == 1 else '0x%08x') % n

    def qname(ns, name):
        namespace = string(ns, True)
        name = string(name)
        if not name:
            raise ValueError('Empty XML element or attribute name')
        return ('{' + namespace + '}' if namespace else '') + name

    while pos < total:
        typ, header, size = unpack(data, '<HHI', pos, total)
        end = pos + size
        if size < header or header < 8 or end > total:
            raise ValueError('Invalid XML chunk')
        if typ == 1:
            if strings or root is not None:
                raise ValueError('Unexpected XML string pool')
            strings = strings_pool(data, pos)
        elif typ == 0x102:
            if header != 16:
                raise ValueError('Invalid XML node header')
            base = pos + header
            ns, name, attr_start, attr_size, count = unpack(data, '<IIHHH', base, end)
            if attr_start < 20 or attr_size < 20 or base + attr_start + attr_size * count > end:
                raise ValueError('Invalid XML attributes')
            node = ET.Element(qname(ns, name))
            for i in range(count):
                at = base + attr_start + i * attr_size
                ans, aname, raw, vsize, reserved, vtype, n = unpack(data, '<IIIHBBI', at, end)
                if vsize != 8 or reserved:
                    raise ValueError('Invalid typed XML value')
                key = qname(ans, aname)
                if key in node.attrib:
                    raise ValueError('Duplicate XML attribute')
                node.set(key, value(raw, vtype, n))
            nodes += 1
            if nodes > MAX_XML_NODES or len(stack) >= 256:
                raise ValueError('Manifest complexity limit exceeded')
            if stack:
                stack[-1].append(node)
            elif root is None:
                root = node
            else:
                raise ValueError('Multiple XML root elements')
            stack.append(node)
        elif typ == 0x103:
            if header != 16:
                raise ValueError('Invalid XML end node')
            ns, name = unpack(data, '<II', pos + header, end)
            if not stack or stack[-1].tag != qname(ns, name):
                raise ValueError('Unbalanced XML elements')
            stack.pop()
        pos = end
    if root is None or stack or root.tag != 'manifest':
        raise ValueError('Incomplete manifest tree')
    return root


def manifest_metadata(root):
    sdk, app = root.find('uses-sdk'), root.find('application')
    package = root.get('package')

    def qualify(name):
        if not name or not package:
            return name
        return package + name if name.startswith('.') else package + '.' + name if '.' not in name else name

    components, launchers = [], []
    if app is not None:
        for node in app:
            if node.tag not in {'activity', 'activity-alias', 'service', 'receiver', 'provider'}:
                continue
            filters = []
            for intent in node.findall('intent-filter'):
                filters.append({
                    'actions': [n.get(ANDROID + 'name') for n in intent.findall('action')],
                    'categories': [n.get(ANDROID + 'name') for n in intent.findall('category')],
                    'data': [{k.removeprefix(ANDROID): v for k, v in n.attrib.items()} for n in intent.findall('data')],
                })
            component = {'type': node.tag, 'name': qualify(node.get(ANDROID + 'name')),
                         'exported': node.get(ANDROID + 'exported'), 'enabled': node.get(ANDROID + 'enabled'),
                         'permission': node.get(ANDROID + 'permission'), 'intentFilters': filters}
            if node.tag == 'activity-alias':
                component['targetActivity'] = qualify(node.get(ANDROID + 'targetActivity'))
            components.append(component)
            if node.tag in {'activity', 'activity-alias'} and any(
                'android.intent.action.MAIN' in f['actions'] and 'android.intent.category.LAUNCHER' in f['categories'] for f in filters
            ):
                launchers.append(component['name'])
    return {
        'package': package, 'versionName': root.get(ANDROID + 'versionName'), 'versionCode': root.get(ANDROID + 'versionCode'),
        'split': root.get('split'), 'minSdk': sdk.get(ANDROID + 'minSdkVersion') if sdk is not None else None,
        'targetSdk': sdk.get(ANDROID + 'targetSdkVersion') if sdk is not None else None,
        'label': app.get(ANDROID + 'label') if app is not None else None,
        'permissions': sorted({n.get(ANDROID + 'name') for n in root if n.tag in {'uses-permission', 'uses-permission-sdk-23'} and n.get(ANDROID + 'name')}),
        'activities': [c['name'] for c in components if c['type'] == 'activity'],
        'components': components, 'launchers': launchers,
        'application': {k.removeprefix(ANDROID): v for k, v in app.attrib.items()} if app is not None else {},
    }


def dex_strings(data):
    """Read standard DEX 035-040 strings. DEX 041 containers are explicit gaps."""
    if len(data) < 112 or not re.fullmatch(rb'dex\n0(?:3[5-9]|40)\x00', data[:8]):
        raise ValueError('Unsupported or truncated DEX header (supported: 035–040)')
    if u32(data, 32) != len(data) or u32(data, 36) != 112 or u32(data, 40) != 0x12345678:
        raise ValueError('Invalid DEX size, header, or byte order')
    count, start = unpack(data, '<II', 56)
    data_size, data_start = unpack(data, '<II', 104)
    if count > MAX_STRINGS or (count and start < 112) or start + count * 4 > len(data):
        raise ValueError('Invalid DEX string index or analysis limit exceeded')
    if data_start < 112 or data_start + data_size != len(data):
        raise ValueError('Invalid DEX data section')
    if count and start + count * 4 > data_start:
        raise ValueError('DEX string index overlaps the data section')
    result, budget = [], 0
    for i in range(count):
        pos = u32(data, start + 4 * i)
        if not data_start <= pos < len(data):
            raise ValueError('Invalid DEX string offset')
        units = 0
        for shift in range(0, 35, 7):
            n = unpack(data, '<B', pos)[0]
            pos += 1
            if shift == 28 and n > 15:
                raise ValueError('Invalid DEX ULEB128 length')
            units |= (n & 127) << shift
            if not n & 128:
                break
        end = data.find(b'\x00', pos, min(len(data), pos + 1024 * 1024))
        if end == -1:
            raise ValueError('Unterminated or oversized DEX string')
        budget += end - pos
        if budget > 16 * 1024 * 1024:
            raise ValueError('DEX decoded string budget exceeded')
        try:
            # MUTF-8 encodes NUL as C0 80 and supplementary code points as surrogate pairs.
            if any(n >= 0xf0 for n in data[pos:end]):
                raise ValueError('Four-byte UTF-8 is invalid in DEX modified UTF-8')
            value = data[pos:end].replace(b'\xc0\x80', b'\x00').decode('utf-8', errors='surrogatepass')
            utf16 = value.encode('utf-16le', errors='surrogatepass')
            value = utf16.decode('utf-16le', errors='surrogatepass')
        except UnicodeError as exc:
            raise ValueError('Invalid DEX modified UTF-8') from exc
        if len(utf16) // 2 != units:
            raise ValueError('DEX string length mismatch')
        result.append(value)
    return result
