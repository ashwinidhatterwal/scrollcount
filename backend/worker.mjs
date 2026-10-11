import {publicPage,policyReady} from './public-pages.mjs';
import {policy} from './release-info.mjs';
// Pairing, legacy WebRTC signaling, and recipient-only encrypted latest snapshots.
const MAX_FRIENDS=8;
const hex=n=>Array.from(crypto.getRandomValues(new Uint8Array(n)),b=>b.toString(16).padStart(2,'0')).join('');
const hash=async s=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s))),b=>b.toString(16).padStart(2,'0')).join('');
const response=(data,status=200)=>Response.json(data,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
function fail(message,status=400){throw Object.assign(new Error(message),{status});}
function name(value){if(typeof value!=='string')fail('Choose a name');const n=value.trim();if(n.length<1||n.length>40||/[\u0000-\u001f\u007f]/.test(n))fail('Use a name of 1–40 characters');return n;}
async function body(request){
 if(Number(request.headers.get('Content-Length')||0)>32768)fail('Request too large',413);
 if(!request.body)fail('Invalid JSON');
 const reader=request.body.getReader(),chunks=[];let size=0;
 try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;
  if(size>32768){await reader.cancel();fail('Request too large',413);}chunks.push(value);}}
 finally{reader.releaseLock();}
 const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
 let result;try{result=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));}catch{fail('Invalid JSON');}
 if(!result||typeof result!=='object'||Array.isArray(result))fail('A JSON object is required');
 return result;
}
async function deleteProfile(env,id){
 await env.DB.batch([
  env.DB.prepare('DELETE FROM snapshots WHERE sender=? OR recipient=?').bind(id,id),
  env.DB.prepare('DELETE FROM profile_keys WHERE profile=?').bind(id),
  env.DB.prepare('DELETE FROM deletion_keys WHERE profile=?').bind(id),
  env.DB.prepare('DELETE FROM signals WHERE sender=? OR recipient=?').bind(id,id),
  env.DB.prepare('DELETE FROM friends WHERE a=? OR b=?').bind(id,id),
  env.DB.prepare('DELETE FROM invites WHERE owner=?').bind(id),
  env.DB.prepare('DELETE FROM profiles WHERE id=?').bind(id)]);
}
async function user(request,env){const t=(request.headers.get('Authorization')||'').replace(/^Bearer /,'');if(!/^[a-f0-9]{64}$/.test(t))fail('Sign in again',401);const u=await env.DB.prepare('SELECT id,name FROM profiles WHERE token_hash=?').bind(await hash(t)).first();if(!u)fail('Profile no longer exists',401);return u;}
async function linked(env,a,b){const [x,y]=[a,b].sort();return env.DB.prepare('SELECT 1 FROM friends WHERE a=? AND b=?').bind(x,y).first();}
async function count(env,id){return (await env.DB.prepare('SELECT COUNT(*) n FROM friends WHERE a=? OR b=?').bind(id,id).first()).n;}
function validateSignal(p){
 if(!p||!['offer','answer'].includes(p.type)||!/^[-a-f0-9]{36}$/.test(p.connection||'')||typeof p.sdp!=='string'||p.sdp.length>24000)fail('Invalid connection details');
 if(Object.keys(p).sort().join(',')!=='connection,sdp,type')fail('Only connection details are accepted');
 if(!p.sdp.startsWith('v=0\r\n')||!/m=application [^\r\n]* (UDP\/DTLS\/SCTP|DTLS\/SCTP)/.test(p.sdp)||/m=(audio|video) /.test(p.sdp)||!/^a=fingerprint:sha-256 /m.test(p.sdp))fail('Only encrypted data channels are accepted');
 return {type:p.type,connection:p.connection,sdp:p.sdp};
}
async function publicKey(value){
 if(typeof value!=='string'||value.length>160||!/^[A-Za-z0-9+/]+={0,2}$/.test(value))fail('Invalid sharing key');
 try{const bytes=Uint8Array.from(atob(value),c=>c.charCodeAt(0));await crypto.subtle.importKey('spki',bytes,{name:'ECDH',namedCurve:'P-256'},false,[]);}catch{fail('Invalid sharing key');}
 return value;
}
const APP_CERT='72:AE:FF:E9:66:D7:E6:16:EF:A2:BE:9D:09:1A:E8:3C:65:E1:0E:AB:A3:F8:E1:58:BC:D1:BA:A1:E8:16:A7:67';
function invitePage(url,code){
 const server=encodeURIComponent(url.origin);
 const open=`intent://invite/${code}?server=${server}#Intent;scheme=scrollcount;package=in.scrollcount.probe;end`;
 return new Response(`<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>ScrollCount invitation</title><style>body{font:16px system-ui;background:#f4f7f3;color:#193d2d;display:grid;place-items:center;min-height:90vh;margin:0;padding:24px;box-sizing:border-box}main{max-width:420px;background:white;border:1px solid #dfebe2;border-radius:28px;padding:32px;text-align:center}a{display:block;background:#216e4f;color:white;padding:17px;border-radius:16px;text-decoration:none;font-weight:600;margin:24px 0}p{line-height:1.6;color:#52665a}</style></head><body><main><h1>Let's compare our day</h1><p>Open ScrollCount to see who invited you and confirm adding them.</p><a href="${open}">Open ScrollCount</a><p>Install the latest ScrollCount app first if it isn't on your phone. Then tap this link again.</p></main></body></html>`,{headers:{'Content-Type':'text/html; charset=UTF-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'"}});
}
export default {
 async fetch(request,env){
  try {
   const url=new URL(request.url),route=url.pathname,now=Date.now();
   if((route==='/privacy'||route==='/delete')&&request.method==='GET')return publicPage(route,url.origin);
   if(env.RATE_LIMITER && !(await env.RATE_LIMITER.limit({key:request.headers.get('CF-Connecting-IP')||'unknown'})).success)return response({error:'Too many requests. Try again shortly.'},429);
   if(route==='/health'&&request.method==='GET')return response({service:'scrollcount-signaling',version:4,snapshots:true,inviteLinks:true,privacyReady:policyReady()});
   if(route==='/.well-known/assetlinks.json'&&request.method==='GET')return Response.json([{relation:['delegate_permission/common.handle_all_urls'],target:{namespace:'android_app',package_name:'in.scrollcount.probe',sha256_cert_fingerprints:[...new Set([APP_CERT,...policy.appSigningCertificates])]}}],{headers:{'Cache-Control':'public, max-age=3600','X-Content-Type-Options':'nosniff'}});
   if(route.startsWith('/invite/')&&request.method==='GET'){
    const code=route.slice(8);if(!/^[a-f0-9]{32}$/.test(code))return response({error:'Invalid invitation'},400);
    // Link previews never consume or look up the invitation. Acceptance stays authenticated.
    return invitePage(url,code);
   }
   if(route==='/register'&&request.method==='POST'){
    const p=await body(request),id=hex(16),token=hex(32),deletionKey=hex(32),n=name(p.name);
    await env.DB.batch([env.DB.prepare('INSERT INTO profiles VALUES(?,?,?,?)').bind(id,await hash(token),n,now),env.DB.prepare('INSERT INTO deletion_keys VALUES(?,?)').bind(id,await hash(deletionKey))]);
    return response({id,token,deletionKey,name:n},201);
   }
   if(route==='/delete-profile'&&request.method==='POST'){
    if(request.headers.get('Origin')&&request.headers.get('Origin')!==url.origin)fail('Invalid origin',403);
    const p=await body(request);if(Object.keys(p).join(',')!=='key'||!(/^[a-f0-9]{64}$/).test(p.key||''))fail('Enter a valid private deletion key');
    const keyHash=await hash(p.key);
    let account=await env.DB.prepare('SELECT profile AS id FROM deletion_keys WHERE key_hash=?').bind(keyHash).first();
    // Older users may have saved their original private profile key.
    if(!account)account=await env.DB.prepare('SELECT id FROM profiles WHERE token_hash=?').bind(keyHash).first();
    if(!account)fail('Key not recognised or profile already deleted',404);
    await deleteProfile(env,account.id);return response({deleted:true});
   }
   const u=await user(request,env);
   if(route==='/me'&&request.method==='DELETE'){
    await deleteProfile(env,u.id);return response({deleted:true});
   }
   if(route==='/deletion-key'&&request.method==='POST'){
    const deletionKey=hex(32);
    await env.DB.prepare('INSERT INTO deletion_keys(profile,key_hash) SELECT ?,? WHERE EXISTS(SELECT 1 FROM profiles WHERE id=?) ON CONFLICT(profile) DO UPDATE SET key_hash=excluded.key_hash').bind(u.id,await hash(deletionKey),u.id).run();
    return response({deletionKey});
   }
   if(route==='/invite'&&request.method==='POST'){
    if(await count(env,u.id)>=MAX_FRIENDS)fail('You can connect up to 8 friends');
    const code=hex(16);
    await env.DB.batch([env.DB.prepare('DELETE FROM invites WHERE owner=?').bind(u.id),env.DB.prepare('INSERT INTO invites VALUES(?,?,?)').bind(code,u.id,now+86400000)]);
    return response({code,expiresAt:now+86400000});
   }
   if((route==='/preview'||route==='/accept')&&request.method==='POST'){
    const p=await body(request);if(!/^[a-f0-9]{32}$/.test(p.code||''))fail('Invalid invitation');
    const invite=await env.DB.prepare('SELECT i.owner,p.name FROM invites i JOIN profiles p ON p.id=i.owner WHERE i.code=? AND i.expires_at>?').bind(p.code,now).first();
    if(!invite)fail('Invitation expired or already used',404);
    if(invite.owner===u.id)fail('This is your own invitation');
    if(route==='/preview')return response({id:invite.owner,name:invite.name});
    const [a,b]=[u.id,invite.owner].sort();
    // Conditional insertion and invitation consumption happen in one atomic batch.
    // The invitation must still exist, so racing acceptors cannot reuse it.
    const results=await env.DB.batch([
     env.DB.prepare('INSERT OR IGNORE INTO friends(a,b,created_at) SELECT ?,?,? WHERE EXISTS(SELECT 1 FROM invites WHERE code=? AND expires_at>?) AND (SELECT COUNT(*) FROM friends WHERE a=? OR b=?)<8 AND (SELECT COUNT(*) FROM friends WHERE a=? OR b=?)<8').bind(a,b,now,p.code,now,u.id,u.id,invite.owner,invite.owner),
     env.DB.prepare('DELETE FROM invites WHERE code=? AND EXISTS(SELECT 1 FROM friends WHERE a=? AND b=?)').bind(p.code,a,b)]);
    if(!results[0].meta.changes)fail('Already friends, invitation used, or friend limit reached',409);
    return response({id:invite.owner,name:invite.name});
   }
   if(route==='/friend'&&request.method==='DELETE'){
    const p=await body(request),[a,b]=[u.id,String(p.id)].sort();
    await env.DB.batch([env.DB.prepare('DELETE FROM friends WHERE a=? AND b=?').bind(a,b),env.DB.prepare('DELETE FROM signals WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)').bind(a,b,b,a),env.DB.prepare('DELETE FROM snapshots WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)').bind(a,b,b,a)]);
    return response({removed:true});
   }
   if(route==='/snapshots'&&request.method==='POST'){
    const p=await body(request);
    if(Object.keys(p).sort().join(',')!=='key,updates'||!Array.isArray(p.updates)||p.updates.length>8)fail('Invalid encrypted snapshot request');
    const key=await publicKey(p.key),seen=new Set();
    for(const item of p.updates){
     if(!item||Object.keys(item).sort().join(',')!=='ciphertext,iv,to'||!(/^[a-f0-9]{32}$/).test(item.to||'')||seen.has(item.to)||!(/^[A-Za-z0-9+/]{16}$/).test(item.iv||'')||typeof item.ciphertext!=='string'||item.ciphertext.length<24||item.ciphertext.length>2048||!(/^[A-Za-z0-9+/]+={0,2}$/).test(item.ciphertext))fail('Only bounded encrypted snapshots are accepted');
     seen.add(item.to);
    }
    const existing=await env.DB.prepare('SELECT public_key FROM profile_keys WHERE profile=?').bind(u.id).first();
    if(existing&&existing.public_key!==key)fail('Sharing key differs. Delete this sharing profile and set it up again.',409);
    const friends=await env.DB.prepare('SELECT p.id,p.name,k.public_key FROM friends f JOIN profiles p ON p.id=CASE WHEN f.a=? THEN f.b ELSE f.a END LEFT JOIN profile_keys k ON k.profile=p.id WHERE f.a=? OR f.b=? ORDER BY p.name LIMIT 8').bind(u.id,u.id,u.id).all();
    const allowed=new Set(friends.results.filter(f=>f.public_key).map(f=>f.id));
    if(p.updates.some(item=>!allowed.has(item.to)))fail('Friend connection not allowed',403);
    const writes=[];if(!existing)writes.push(env.DB.prepare('INSERT OR IGNORE INTO profile_keys(profile,public_key) SELECT ?,? WHERE EXISTS(SELECT 1 FROM profiles WHERE id=?)').bind(u.id,key,u.id));
    for(const item of p.updates){
     const [a,b]=[u.id,item.to].sort();
     writes.push(env.DB.prepare('INSERT INTO snapshots(sender,recipient,iv,ciphertext,updated_at) SELECT ?,?,?,?,? WHERE EXISTS(SELECT 1 FROM friends WHERE a=? AND b=?) AND EXISTS(SELECT 1 FROM profile_keys WHERE profile=? AND public_key=?) ON CONFLICT(sender,recipient) DO UPDATE SET iv=excluded.iv,ciphertext=excluded.ciphertext,updated_at=excluded.updated_at').bind(u.id,item.to,item.iv,item.ciphertext,now,a,b,u.id,key));
    }
    if(writes.length)await env.DB.batch(writes);
    // Read again after registration; immutable keys make concurrent registration safe.
    const registered=await env.DB.prepare('SELECT public_key FROM profile_keys WHERE profile=?').bind(u.id).first();
    if(!registered||registered.public_key!==key)fail('Sharing key differs. Delete this sharing profile and set it up again.',409);
    const rows=await env.DB.prepare('SELECT s.sender,s.iv,s.ciphertext,s.updated_at FROM snapshots s JOIN friends f ON (f.a=s.sender AND f.b=s.recipient) OR (f.b=s.sender AND f.a=s.recipient) WHERE s.recipient=? AND s.updated_at>? LIMIT 8').bind(u.id,now-7*86400000).all();
    const ids=new Set(friends.results.map(f=>f.id));
    return response({friends:friends.results,snapshots:rows.results.filter(s=>ids.has(s.sender))});
   }
   if(route==='/signal'&&request.method==='POST'){
    const p=await body(request);if(Object.keys(p).sort().join(',')!=='signal,to')fail('Only connection details are accepted');if(!/^[a-f0-9]{32}$/.test(p.to||'')||!await linked(env,u.id,p.to))fail('Friend connection not allowed',403);
    const payload=JSON.stringify(validateSignal(p.signal));
    // Only keep the newest description for each direction. No unbounded inbox.
    const [a,b]=[u.id,p.to].sort();
    await env.DB.batch([env.DB.prepare('DELETE FROM signals WHERE sender=? AND recipient=?').bind(u.id,p.to),
     env.DB.prepare('INSERT INTO signals(sender,recipient,payload,expires_at) SELECT ?,?,?,? WHERE EXISTS(SELECT 1 FROM friends WHERE a=? AND b=?)').bind(u.id,p.to,payload,now+120000,a,b)]);
    return response({sent:true});
   }
   if(route==='/sync'&&request.method==='POST'){
    const p=await body(request);const ack=Number.isSafeInteger(p.ack)&&p.ack>=0?p.ack:0;
    await env.DB.prepare('DELETE FROM signals WHERE recipient=? AND (id<=? OR expires_at<=?)').bind(u.id,ack,now).run();
    const friends=await env.DB.prepare('SELECT p.id,p.name FROM friends f JOIN profiles p ON p.id=CASE WHEN f.a=? THEN f.b ELSE f.a END WHERE f.a=? OR f.b=? ORDER BY p.name LIMIT 8').bind(u.id,u.id,u.id).all();
    const ids=new Set(friends.results.map(f=>f.id));
    const rows=await env.DB.prepare('SELECT id,sender,payload FROM signals WHERE recipient=? AND id>? AND expires_at>? ORDER BY id LIMIT 16').bind(u.id,ack,now).all();
    return response({friends:friends.results,signals:rows.results.filter(r=>ids.has(r.sender)).map(r=>({id:r.id,from:r.sender,signal:JSON.parse(r.payload)})),cursor:rows.results.reduce((n,r)=>Math.max(n,r.id),ack)});
   }
   return response({error:'Not found'},404);
  }catch(e){return response({error:e.status?e.message:'Service unavailable. Try again later.'},e.status||503);}
 },
 async scheduled(_event,env){await env.DB.batch([env.DB.prepare('DELETE FROM signals WHERE expires_at<=?').bind(Date.now()),env.DB.prepare('DELETE FROM invites WHERE expires_at<=?').bind(Date.now()),env.DB.prepare('DELETE FROM snapshots WHERE updated_at<=?').bind(Date.now()-7*86400000)]);}
};
export {validateSignal,name};
