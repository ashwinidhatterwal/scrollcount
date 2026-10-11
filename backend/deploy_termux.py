#!/usr/bin/env python3
"""Update the ScrollCount Worker/D1 using Python stdlib. Never saves API tokens."""
import getpass, json, re, urllib.request, urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parent
BASE='https://api.cloudflare.com/client/v4'
SCRIPT='scrollcount-friends'
def request(path,method='GET',data=None,content_type='application/json'):
    payload=data if isinstance(data,bytes) else (json.dumps(data).encode() if data is not None else None)
    req=urllib.request.Request(BASE+path,data=payload,method=method,headers={'Authorization':'Bearer '+TOKEN,'Content-Type':content_type})
    try:
        with urllib.request.urlopen(req,timeout=90) as res: result=json.load(res)
    except urllib.error.HTTPError as e:
        try: details=json.load(e).get('errors',[])
        except Exception: details=[]
        raise RuntimeError('Cloudflare HTTP '+str(e.code)+': '+str(details)) from None
    if not result.get('success'):raise RuntimeError(str(result.get('errors','Cloudflare request failed')))
    return result.get('result')
def identifier(value,label,pattern):
    if not re.fullmatch(pattern,value):raise ValueError('Invalid '+label)
    return value
if __name__=='__main__':
    try:
        metadata_path=ROOT/'release-info.mjs'
        public=json.loads(metadata_path.read_text().split('export const policy = ',1)[1].strip().rstrip(';'))
        if not re.fullmatch(r'[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+',public.get('supportEmail','')):
            raise ValueError('Configure your public privacy email first: python3 tools/configure_release.py --email YOUR_EMAIL')
        config_path=ROOT/'deployment.json' 
        config=json.loads(config_path.read_text()) if config_path.exists() else {}
        account=identifier(config.get('account_id') or input('Cloudflare account ID: ').strip(),'account ID',r'[a-fA-F0-9]{32}')
        database=identifier(config.get('database_id') or input('Existing ScrollCount database ID: ').strip(),'database ID',r'[a-fA-F0-9-]{36}')
        TOKEN=getpass.getpass('Cloudflare API token (hidden, not saved): ').strip()
        if not TOKEN:raise ValueError('API token is required')
        root='/accounts/'+account
        results=request(root+'/d1/database/'+database+'/query','POST',{'sql':(ROOT/'schema.sql').read_text()})
        if any(not item.get('success',False) for item in results):raise RuntimeError('Database migration failed')
        request(root+'/d1/database/'+database+'/query','POST',{'sql':'SELECT COUNT(*) FROM profile_keys; SELECT COUNT(*) FROM snapshots;'})
        metadata={'main_module':'worker.mjs','compatibility_date':'2026-10-08','observability':{'enabled':False},'bindings':[{'type':'d1','name':'DB','id':database},{'type':'ratelimit','name':'RATE_LIMITER','namespace_id':'16001','simple':{'limit':60,'period':60}}]}
        boundary='ScrollCountDeployBoundary2026'
        body=bytearray()
        for part,filename,mime,value in [('metadata',None,'application/json',json.dumps(metadata).encode()),('worker.mjs','worker.mjs','application/javascript+module',(ROOT/'worker.mjs').read_bytes()),('public-pages.mjs','public-pages.mjs','application/javascript+module',(ROOT/'public-pages.mjs').read_bytes()),('release-info.mjs','release-info.mjs','application/javascript+module',(ROOT/'release-info.mjs').read_bytes())]:
            disposition='form-data; name="'+part+'"'+('; filename="'+filename+'"' if filename else '')
            body.extend(('--'+boundary+'\r\nContent-Disposition: '+disposition+'\r\nContent-Type: '+mime+'\r\n\r\n').encode());body.extend(value);body.extend(b'\r\n')
        body.extend(('--'+boundary+'--\r\n').encode())
        script=root+'/workers/scripts/'+SCRIPT
        request(script,'PUT',bytes(body),'multipart/form-data; boundary='+boundary)
        request(script+'/subdomain','POST',{'enabled':True})
        request(script+'/schedules','PUT',[{'cron':'*/10 * * * *'}])
        sub=request(root+'/workers/subdomain')['subdomain']
        url='https://'+SCRIPT+'.'+sub+'.workers.dev'
        config_path.write_text(json.dumps({'account_id':account,'database_id':database,'url':url},indent=2)+'\n')
        print('Backend updated. Profiles and friendships retained.\n'+url+'\n'+url+'/health')
        try:
            with urllib.request.urlopen(url+'/health',timeout=20) as res:health=json.load(res)
            if health.get('version')!=4 or not health.get('privacyReady') or not health.get('snapshots'):raise RuntimeError('Old health response')
            print('Verified: version 4, encrypted snapshots enabled.')
        except Exception:print('Deployment completed. Open /health and check version 4 with snapshots: true; propagation may take a moment.')
    except Exception as e:
        print('Stopped: '+str(e));raise SystemExit(1)
