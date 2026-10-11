"""Run the deployment helper with mocked transport; never uses real credentials."""
import contextlib, io, json, runpy, tempfile, shutil
from pathlib import Path
from unittest.mock import patch
root=Path(__file__).resolve().parents[1]
class Reply(io.BytesIO):
 def __enter__(self):return self
 def __exit__(self,*args):self.close()
calls=[]
def fake(req,timeout=0):
 if isinstance(req,str):return Reply(json.dumps({'version':4,'snapshots':True,'inviteLinks':True,'privacyReady':True}).encode())
 calls.append(req)
 path=req.full_url
 result=([{'success':True}] if path.endswith('/query') else {'subdomain':'actionbridge'} if path.endswith('/workers/subdomain') else {})
 return Reply(json.dumps({'success':True,'result':result}).encode())
with tempfile.TemporaryDirectory() as temp:
 d=Path(temp)
 for file in ['deploy_termux.py','schema.sql','worker.mjs','public-pages.mjs','release-info.mjs']:shutil.copy(root/'backend'/file,d/file)
 (d/'release-info.mjs').write_text((d/'release-info.mjs').read_text().replace('"supportEmail": ""','"supportEmail": "privacy@example.org"'))
 (d/'deployment.json').write_text(json.dumps({'account_id':'a'*32,'database_id':'12345678-1234-1234-1234-123456789abc'}))
 with patch('urllib.request.urlopen',fake),patch('getpass.getpass',return_value='SECRET_TOKEN'),patch('builtins.input',side_effect=AssertionError('must reuse IDs')),contextlib.redirect_stdout(io.StringIO()):runpy.run_path(str(d/'deploy_termux.py'),run_name='__main__')
 assert 'SECRET_TOKEN' not in (d/'deployment.json').read_text()
 assert len(calls)==6
 upload=calls[2];assert upload.method=='PUT' and upload.full_url.endswith('/workers/scripts/scrollcount-friends')
 assert b'"observability": {"enabled": false}' in upload.data and b'filename="public-pages.mjs"' in upload.data and b'filename="release-info.mjs"' in upload.data
 assert b'"main_module": "worker.mjs"' in upload.data and b'"type": "d1"' in upload.data and b'"type": "ratelimit"' in upload.data
 assert all('/accounts/'+'a'*32+'/' in req.full_url for req in calls)
 assert calls[3].method=='POST' and json.loads(calls[3].data)['enabled']
 assert calls[4].method=='PUT' and json.loads(calls[4].data)==[{'cron':'*/10 * * * *'}]
 assert all('SECRET_TOKEN' not in (d/f).read_text() for f in ['worker.mjs','schema.sql','deployment.json'])
print('PASS: deployment helper ID reuse, additive schema, multipart Worker/bindings, cron, URL verification and no saved token')
