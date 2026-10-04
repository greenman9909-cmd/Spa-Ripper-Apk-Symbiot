"""Small read-only Android binary XML and DEX string readers."""
import struct
import xml.etree.ElementTree as ET

def u32(data, offset): return struct.unpack_from('<I',data,offset)[0]

def strings_pool(data,start):
    _,header,size=struct.unpack_from('<HHI',data,start)
    count=u32(data,start+8);flags=u32(data,start+16);base=start+u32(data,start+20)
    if count>200000 or start+size>len(data):raise ValueError('Invalid string pool')
    def length(pos,utf8):
        if utf8:
            n=data[pos];pos+=1
            if n&128:n=((n&127)<<8)|data[pos];pos+=1
        else:
            n=struct.unpack_from('<H',data,pos)[0];pos+=2
            if n&32768:n=((n&32767)<<16)|struct.unpack_from('<H',data,pos)[0];pos+=2
        return n,pos
    result=[]
    for i in range(count):
        pos=base+u32(data,start+header+4*i)
        if flags&256:
            _,pos=length(pos,True);n,pos=length(pos,True)
            result.append(data[pos:pos+n].decode('utf-8',errors='replace'))
        else:
            n,pos=length(pos,False);result.append(data[pos:pos+n*2].decode('utf-16le',errors='replace'))
    return result

def decode_manifest(data):
    if data.lstrip().startswith(b'<'):return ET.fromstring(data)
    if len(data)<8 or struct.unpack_from('<H',data,0)[0]!=3:raise ValueError('Unsupported manifest format')
    total=u32(data,4)
    if total>len(data):raise ValueError('Truncated binary XML')
    strings=[];stack=[];root=None;pos=struct.unpack_from('<H',data,2)[0]
    def string(idx):return strings[idx] if idx!=0xffffffff and idx<len(strings) else ''
    def value(raw,typ,n):
        if raw!=0xffffffff:return string(raw)
        if typ==3:return string(n)
        if typ==18:return 'true' if n else 'false'
        if typ==16:return str(n)
        if typ==17:return hex(n)
        if typ==1:return '@0x%08x'%n
        return '0x%08x'%n
    while pos+8<=total:
        typ,header,size=struct.unpack_from('<HHI',data,pos)
        if size<header or size<8 or pos+size>total:raise ValueError('Invalid XML chunk')
        if typ==1:strings=strings_pool(data,pos)
        elif typ==0x102:
            base=pos+header
            ns,name=struct.unpack_from('<II',data,base)
            tag=string(name);namespace=string(ns)
            node=ET.Element(('{'+namespace+'}' if namespace else '')+tag)
            attr_start,attr_size,count=struct.unpack_from('<HHH',data,base+8)
            if attr_size<20 or base+attr_start+attr_size*count>pos+size:raise ValueError('Invalid attributes')
            for i in range(count):
                at=base+attr_start+i*attr_size
                ans,aname,raw=struct.unpack_from('<III',data,at)
                _,_,vtype,n=struct.unpack_from('<HBBI',data,at+12)
                namespace=string(ans);key=('{'+namespace+'}' if namespace else '')+string(aname)
                node.set(key,value(raw,vtype,n))
            if stack:stack[-1].append(node)
            else:root=node
            stack.append(node)
        elif typ==0x103:
            if stack:stack.pop()
        pos+=size
    if root is None:raise ValueError('Manifest has no root element')
    return root

def manifest_metadata(root):
    ns='{http://schemas.android.com/apk/res/android}'
    sdk=root.find('uses-sdk');app=root.find('application')
    return {'package':root.get('package'), 'versionName':root.get(ns+'versionName'), 'versionCode':root.get(ns+'versionCode'), 'minSdk':sdk.get(ns+'minSdkVersion') if sdk is not None else None, 'targetSdk':sdk.get(ns+'targetSdkVersion') if sdk is not None else None, 'label':app.get(ns+'label') if app is not None else None, 'permissions':[n.get(ns+'name') for n in root.findall('uses-permission')], 'activities':[n.get(ns+'name') for n in app.findall('activity')] if app is not None else []}

def dex_strings(data):
    if len(data)<112 or not data.startswith(b'dex\n'):return []
    count=u32(data,56);start=u32(data,60)
    if count>2000000 or start+count*4>len(data):raise ValueError('Invalid DEX string index')
    result=[]
    for i in range(count):
        pos=u32(data,start+4*i)
        for _ in range(5):
            n=data[pos];pos+=1
            if not n&128:break
        end=data.find(b'\x00',pos)
        if end==-1:raise ValueError('Unterminated DEX string')
        result.append(data[pos:end].decode('utf-8',errors='replace'))
    return result
