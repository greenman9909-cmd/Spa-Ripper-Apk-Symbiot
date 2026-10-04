import io
import unittest
import zipfile
import server


def fixture(extra=None):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as z:
        z.writestr('AndroidManifest.xml','<manifest package="dev.apkforge.demo"/>')
        z.writestr('assets/www/index.html','<h1>Recovered demo</h1><script src="app.js"></script>')
        z.writestr('assets/www/app.js','document.body.dataset.loaded="yes";')
        z.writestr('assets/www/style.css','body { color: green; }')
        z.writestr('classes.dex',b'dex\n035\x00')
        for path,body in (extra or {}).items(): z.writestr(path,body)
    return stream.getvalue()

class EngineTests(unittest.TestCase):
    def test_recovery_and_export(self):
        session=server.inspect_apk(fixture())
        self.assertEqual(session['report']['candidates'][0]['entry'],'assets/www/index.html')
        self.assertEqual(session['report']['counts']['DEX'],1)
        out=zipfile.ZipFile(io.BytesIO(server.export_web(session,'assets/www/index.html')))
        self.assertEqual(out.read('web/app.js'),b'document.body.dataset.loaded="yes";')
        self.assertNotIn('classes.dex',out.namelist())
        self.assertIn('analysis.json',out.namelist())
    def test_traversal_rejected(self):
        for path in ('../escape','/absolute','assets/../../escape','assets\\escape','C:/escape'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                server.inspect_apk(fixture({path:'bad'}))
    def test_kotlin_metadata_colon_accepted(self):
        session=server.inspect_apk(fixture({'META-INF/Nuvio:composeApp.kotlin_module':b'metadata'}))
        self.assertTrue(any(f['path']=='META-INF/Nuvio:composeApp.kotlin_module' for f in session['report']['files']))

    def test_invalid_archive(self):
        with self.assertRaises(ValueError): server.inspect_apk(b'invalid')
    def test_generic_zip_rejected(self):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w') as z:z.writestr('index.html','Hi')
        with self.assertRaises(ValueError): server.inspect_apk(stream.getvalue())
    def test_missing_export_entry_rejected(self):
        with self.assertRaises(ValueError):server.export_web(server.inspect_apk(fixture()),'missing.html')
    def test_archive_limit(self):
        original=server.MAX_EXPANDED
        try:
            server.MAX_EXPANDED=10
            with self.assertRaises(ValueError):server.inspect_apk(fixture())
        finally:server.MAX_EXPANDED=original
    def test_native_only(self):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w') as z:
            z.writestr('AndroidManifest.xml',b'\x03\x00\x08\x00')
            z.writestr('lib/arm64-v8a/libflutter.so',b'ELF')
        report=server.inspect_apk(stream.getvalue())['report']
        self.assertEqual(report['candidates'],[])
        self.assertIn('Flutter',report['signals'])


class HTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import threading
        cls.http=server.ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        cls.http.local_host='127.0.0.1:'+str(cls.http.server_port)
        cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True)
        cls.thread.start()
    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown();cls.http.server_close();cls.thread.join()
    def request(self,method,path,body=None,headers=None):
        import http.client
        conn=http.client.HTTPConnection('127.0.0.1',self.http.server_port)
        conn.request(method,path,body,headers or {})
        res=conn.getresponse();result=(res.status,dict(res.getheaders()),res.read());conn.close();return result
    def test_upload_preview_export(self):
        import json
        status,_,body=self.request('GET','/')
        self.assertEqual(status,200);self.assertIn(server.TOKEN.encode(),body)
        status,_,body=self.request('POST','/api/upload',fixture(),{'X-APKForge-Token':server.TOKEN})
        self.assertEqual(status,200);sid=json.loads(body)['session']
        status,headers,body=self.request('GET','/content/'+sid+'/assets/www/index.html')
        self.assertEqual(status,200);self.assertIn(b'Recovered demo',body)
        self.assertIn("connect-src 'none'",headers['Content-Security-Policy'])
        status,_,body=self.request('GET','/export/'+sid+'/web.zip?entry=assets%2Fwww%2Findex.html')
        self.assertEqual(status,200)
        self.assertIn('web/index.html',zipfile.ZipFile(io.BytesIO(body)).namelist())
    def test_upload_without_token(self):
        status,_,_=self.request('POST','/api/upload',fixture())
        self.assertEqual(status,403)
    def test_host_rejected(self):
        status,_,_=self.request('GET','/',headers={'Host':'attacker.invalid'})
        self.assertEqual(status,403)

if __name__=='__main__':unittest.main()
