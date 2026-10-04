#!/usr/bin/env python3
"""APKForge: local APK inventory and bundled-web recovery. Python 3.10+."""
import argparse
import hashlib
import io
import json
import mimetypes
import re
import secrets
import threading
import zipfile
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit, parse_qs

MAX_UPLOAD = 256 * 1024 * 1024
MAX_EXPANDED = 512 * 1024 * 1024
MAX_ENTRY = 32 * 1024 * 1024
MAX_ENTRIES = 20000
SESSIONS = {}
LOCK = threading.Lock()
TOKEN = secrets.token_urlsafe(24)
WEB_EXT = {'.html', '.htm', '.js', '.css', '.json'}

def inspect_apk(data, name='app.apk'):
    if len(data) > MAX_UPLOAD:
        raise ValueError('APK exceeds the 256 MB upload limit.')
    try:
        z = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as exc:
        raise ValueError('This file is not a valid APK / ZIP archive.') from exc
    infos = z.infolist()
    if len(infos) > MAX_ENTRIES or sum(i.file_size for i in infos) > MAX_EXPANDED:
        raise ValueError('Archive exceeds the extraction limits.')
    names = set()
    for i in infos:
        p = PurePosixPath(i.filename)
        if p.is_absolute() or '..' in p.parts or '\\' in i.filename or re.match(r'^[A-Za-z]:', i.filename) or '\x00' in i.filename:
            raise ValueError('Archive contains an unsafe path.')
        if i.filename in names:
            raise ValueError('Archive contains duplicate paths.')
        names.add(i.filename)
        if i.flag_bits & 1:
            raise ValueError('Encrypted archive entries are unsupported.')
        if i.file_size > MAX_ENTRY:
            raise ValueError('An archive entry exceeds the 32 MB limit.')
    if 'AndroidManifest.xml' not in names:
        raise ValueError('No AndroidManifest.xml found. Supply an APK, not a generic ZIP.')
    files = [{'path':i.filename, 'bytes':i.file_size, 'kind':kind(i.filename)} for i in infos if not i.is_dir()]
    html = sorted(f['path'] for f in files if PurePosixPath(f['path']).suffix.lower() in {'.html','.htm'} and f['path'].startswith('assets/'))
    signals = []
    if any('flutter_assets/' in n or n.endswith('/libflutter.so') for n in names): signals.append('Flutter')
    if any(n.endswith('index.android.bundle') or n.endswith('/libreactnative.so') for n in names): signals.append('React Native')
    if any(n.endswith('/cordova.js') for n in names): signals.append('Cordova')
    if any(n.endswith('/capacitor.config.json') for n in names): signals.append('Capacitor')
    if html: signals.append('Bundled HTML')
    counts = dict(Counter(f['kind'] for f in files))
    candidates = [{'entry':h, 'root':str(PurePosixPath(h).parent), 'mode':'web-assets', 'status':'Previewable; native bridges and remote services may be required'} for h in html]
    candidates.sort(key=lambda c:(PurePosixPath(c['entry']).name.lower() != 'index.html',len(c['entry'])))
    report = {'name':name[:180], 'sha256':hashlib.sha256(data).hexdigest(), 'bytes':len(data), 'expandedBytes':sum(i.file_size for i in infos), 'files':files, 'counts':counts, 'signals':signals or ['No known framework marker'], 'candidates':candidates, 'nativeRuntime':'Not implemented: use Android Emulator for native execution.', 'manifest':'Raw manifest retained; binary XML decoding is not implemented.', 'limitations':['Static inventory is not a source-code reconstruction.', 'Bundled HTML can rely on native plugins, remote APIs, or missing split APK assets.', 'Browser preview blocks network requests and isolates recovered scripts. Fetch/storage/native bridges may not work.', 'Framework signals are filename heuristics, not proof of architecture.']}
    return {'zip':z, 'report':report}

def kind(name):
    ext = PurePosixPath(name).suffix.lower()
    if ext in {'.png','.jpg','.jpeg','.webp','.gif','.svg','.avif'}: return 'Images'
    if ext in WEB_EXT: return 'Web'
    if ext in {'.ttf','.otf','.woff','.woff2'}: return 'Fonts'
    if ext == '.dex': return 'DEX'
    if ext == '.so': return 'Native'
    return 'Other'

def export_web(session, entry):
    candidate = next((c for c in session['report']['candidates'] if c['entry']==entry), None)
    if not candidate: raise ValueError('Choose a detected HTML entry point.')
    root = candidate['root'] + '/'
    out = io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        for f in session['report']['files']:
            if f['path'].startswith(root):
                z.writestr('web/'+f['path'][len(root):],session['zip'].read(f['path']))
        z.writestr('analysis.json',json.dumps(session['report'],indent=2))
        z.writestr('RUN.txt','Bundled web files only. In the web folder run: python -m http.server 8000 --bind 127.0.0.1\nOpen http://127.0.0.1:8000/'+PurePosixPath(entry).name+'\nNative plugins, backend services, and root-relative paths may require adaptation.\n')
    return out.getvalue()

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def send(self, status, body, mime='application/json', extra=None):
        if isinstance(body,dict): body=json.dumps(body).encode()
        if isinstance(body,str): body=body.encode()
        self.send_response(status)
        self.send_header('Content-Type',mime)
        self.send_header('Content-Length',str(len(body)))
        self.send_header('Cache-Control','no-store')
        self.send_header('X-Content-Type-Options','nosniff')
        for k,v in (extra or {}).items(): self.send_header(k,v)
        self.end_headers()
        self.wfile.write(body)
    def allowed(self):
        return self.headers.get('Host') == self.server.local_host
    def do_POST(self):
        if not self.allowed() or self.headers.get('X-APKForge-Token') != TOKEN:
            return self.send(403,{'error':'Invalid local request.'})
        if self.path != '/api/upload': return self.send(404,{'error':'Not found'})
        try:
            size=int(self.headers.get('Content-Length','0'))
            if not 0 < size <= MAX_UPLOAD: raise ValueError('Upload must be between 1 byte and 256 MB.')
            data=self.rfile.read(size)
            if len(data) != size: raise ValueError('Upload was interrupted.')
            session=inspect_apk(data,unquote(self.headers.get('X-Filename','app.apk')))
            sid=secrets.token_hex(12)
            with LOCK:
                if len(SESSIONS)>=3:
                    old=SESSIONS.pop(next(iter(SESSIONS))); old['zip'].close()
                SESSIONS[sid]=session
            self.send(200,dict(session['report'],session=sid))
        except (ValueError,RuntimeError,zipfile.BadZipFile,NotImplementedError) as exc:
            self.send(400,{'error':str(exc)})
    def do_GET(self):
        if not self.allowed(): return self.send(403,{'error':'Invalid host.'})
        u=urlsplit(self.path)
        if u.path=='/':
            page=(Path(__file__).parent/'index.html').read_text().replace('__TOKEN__',TOKEN)
            return self.send(200,page,'text/html; charset=utf-8',{'Content-Security-Policy':"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-src 'self'; object-src 'none'; frame-ancestors 'none'"})
        parts=u.path.split('/')
        if len(parts)<4 or parts[1] not in {'content','export','report'}:
            return self.send(404,{'error':'Not found'})
        with LOCK: session=SESSIONS.get(parts[2])
        if not session: return self.send(404,{'error':'Session expired. Upload again.'})
        try:
            if parts[1]=='report':
                return self.send(200,session['report'],extra={'Content-Disposition':'attachment; filename="analysis.json"'})
            if parts[1]=='export':
                entry=parse_qs(u.query).get('entry',[''])[0]
                return self.send(200,export_web(session,entry),'application/zip',{'Content-Disposition':'attachment; filename="apkforge-web.zip"'})
            path=unquote('/'.join(parts[3:]))
            if path not in session['zip'].namelist(): return self.send(404,{'error':'Asset not found'})
            mime=mimetypes.guess_type(path)[0] or 'application/octet-stream'
            policy="default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; sandbox allow-scripts"
            return self.send(200,session['zip'].read(path),mime,{'Content-Security-Policy':policy})
        except (ValueError,KeyError,RuntimeError,zipfile.BadZipFile) as exc:
            return self.send(400,{'error':str(exc)})

def main():
    p=argparse.ArgumentParser(); p.add_argument('--port',type=int,default=8787); args=p.parse_args()
    server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler)
    server.local_host=f'127.0.0.1:{server.server_port}'
    print(f'APKForge ready: http://{server.local_host}\nCtrl+C to stop. APKs remain in memory (last 3 sessions).',flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()
if __name__=='__main__': main()
