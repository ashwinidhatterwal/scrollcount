import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import {policy} from './release-info.mjs';
import worker,{validateSignal} from './worker.mjs';
const sql=new DatabaseSync(':memory:');sql.exec(readFileSync(new URL('./schema.sql',import.meta.url),'utf8'));
class Statement{
 constructor(query,args=[]){this.query=query;this.args=args;}
 bind(...args){return new Statement(this.query,args);}
 async first(){return sql.prepare(this.query).get(...this.args)||null;}
 async all(){return {results:sql.prepare(this.query).all(...this.args)};}
 async run(){const r=sql.prepare(this.query).run(...this.args);return {meta:{changes:Number(r.changes)}};}
}
const env={DB:{prepare:q=>new Statement(q),async batch(stmts){sql.exec('BEGIN');try{const result=[];for(const s of stmts)result.push(await s.run());sql.exec('COMMIT');return result;}catch(e){sql.exec('ROLLBACK');throw e;}}}};
async function call(route,method='POST',data={},token=''){
 const r=await worker.fetch(new Request('https://test.workers.dev'+route,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(method==='GET'?{}:{body:JSON.stringify(data)})}),env);return {status:r.status,data:await r.json()};
}
const register=async n=>(await call('/register','POST',{name:n})).data;
const a=await register('Asha'),b=await register('Bina'),c=await register('Chitra');
assert.equal((await call('/sync')).status,401);
assert.equal((await call('/register','POST',{name:'x'.repeat(41)})).status,400);
assert.notEqual(sql.prepare('SELECT token_hash FROM profiles WHERE id=?').get(a.id).token_hash,a.token);
const invite=(await call('/invite','POST',{},a.token)).data;
assert.equal((await call('/preview','POST',{code:invite.code},a.token)).status,400,'self pairing rejected');
assert.equal((await call('/preview','POST',{code:invite.code},b.token)).data.name,'Asha');
assert.equal((await call('/accept','POST',{code:invite.code},b.token)).status,200);
assert.equal((await call('/accept','POST',{code:invite.code},c.token)).status,404,'single-use invite');
assert.equal((await call('/sync','POST',{},a.token)).data.friends[0].name,'Bina');
const signal={type:'offer',connection:'11111111-1111-4111-8111-111111111111',sdp:'v=0\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\na=fingerprint:sha-256 AA:BB\r\n'};
assert.equal((await call('/signal','POST',{to:c.id,signal},a.token)).status,403,'unlinked peer rejected');
assert.equal((await call('/signal','POST',{to:b.id,signal:{...signal,reels:100}},a.token)).status,400,'counter payload rejected');
assert.equal((await call('/signal','POST',{to:b.id,signal,reels:100},a.token)).status,400,'extra counter fields rejected');
assert.throws(()=>validateSignal({...signal,sdp:signal.sdp+'m=video 9 RTP/AVP 0\r\n'}));
assert.equal((await call('/signal','POST',{to:b.id,signal},a.token)).status,200);
let inbox=(await call('/sync','POST',{ack:0},b.token)).data;
assert.equal(inbox.signals.length,1);
assert.equal(inbox.signals[0].from,a.id);
assert.equal((await call('/sync','POST',{ack:0},c.token)).data.signals.length,0,'inbox private');
assert.equal((await call('/sync','POST',{ack:inbox.cursor},b.token)).data.signals.length,0,'ack removes description');
await call('/signal','POST',{to:b.id,signal},a.token);
sql.prepare('UPDATE signals SET expires_at=?').run(Date.now()-1);
assert.equal((await call('/sync','POST',{},b.token)).data.signals.length,0,'expired descriptions never delivered');
// Actual snapshot SQL: recipient isolation, immutable keys, bounds, TTL and replacement.
const key=async()=>{const pair=await crypto.subtle.generateKey({name:'ECDH',namedCurve:'P-256'},true,['deriveBits']);return Buffer.from(await crypto.subtle.exportKey('spki',pair.publicKey)).toString('base64');};
const ak=await key(),bk=await key(),ck=await key();
const snap=(k,updates=[])=>({key:k,updates});
assert.equal((await call('/snapshots','POST',snap(bk),b.token)).status,200);
assert.equal((await call('/snapshots','POST',snap(ak),a.token)).data.friends[0].public_key,bk);
const encrypted={to:b.id,iv:Buffer.alloc(12,7).toString('base64'),ciphertext:Buffer.alloc(32,9).toString('base64')};
assert.equal((await call('/snapshots','POST',snap(ak,[{...encrypted,reels:20}]),a.token)).status,400);
assert.equal((await call('/snapshots','POST',snap(ak,[{...encrypted,to:c.id}]),a.token)).status,403);
assert.equal((await call('/snapshots','POST',snap(ak,[encrypted,encrypted]),a.token)).status,400);
assert.equal((await call('/snapshots','POST',snap(ak,[{...encrypted,ciphertext:'A'.repeat(2050)}]),a.token)).status,400);
assert.equal((await call('/snapshots','POST',snap(ak,[encrypted]),a.token)).status,200);
assert.equal((await call('/snapshots','POST',snap(bk),b.token)).data.snapshots[0].sender,a.id,'offline sender data remains available');
assert.equal((await call('/snapshots','POST',snap(ck),c.token)).data.snapshots.length,0,'unlinked recipient sees nothing');
assert.equal((await call('/snapshots','POST',snap(await key(),[encrypted]),a.token)).status,409,'key cannot rotate silently');
assert.equal((await call('/snapshots','POST',snap(ak,[encrypted]),a.token)).status,200);
assert.equal(sql.prepare('SELECT COUNT(*) n FROM snapshots').get().n,1,'only latest direction is retained');
sql.prepare('UPDATE snapshots SET updated_at=0').run();
assert.equal((await call('/snapshots','POST',snap(bk),b.token)).data.snapshots.length,0,'expired snapshots never delivered');
await worker.scheduled({},env);assert.equal(sql.prepare('SELECT COUNT(*) n FROM snapshots').get().n,0);
await call('/snapshots','POST',snap(ak,[encrypted]),a.token);
await call('/friend','DELETE',{id:b.id},a.token);
assert.equal((await call('/signal','POST',{to:b.id,signal},a.token)).status,403,'revocation');
assert.equal(sql.prepare('SELECT COUNT(*) n FROM snapshots').get().n,0,'removal deletes encrypted data');
assert.equal((await call('/sync','POST',{},b.token)).data.friends.length,0);
const expired=(await call('/invite','POST',{},a.token)).data;
sql.prepare('UPDATE invites SET expires_at=0').run();
assert.equal((await call('/accept','POST',{code:expired.code},c.token)).status,404);
for(let i=0;i<8;i++){const f=await register('Friend '+i);const code=(await call('/invite','POST',{},a.token)).data.code;assert.equal((await call('/accept','POST',{code},f.token)).status,200);}
assert.equal((await call('/invite','POST',{},a.token)).status,400,'friend cap');
const newFriend=(await call('/snapshots','POST',snap(ak),a.token)).data.friends[0];const friendToken=await register('DeleteTest');const code=(await call('/invite','POST',{},friendToken.token)).data.code;await call('/accept','POST',{code},b.token);await call('/snapshots','POST',snap(await key()),friendToken.token);await call('/snapshots','POST',snap(bk,[{...encrypted,to:friendToken.id}]),b.token);await call('/me','DELETE',{},friendToken.token);assert.equal(sql.prepare('SELECT COUNT(*) n FROM snapshots').get().n,0,'profile deletion removes incoming snapshots');assert.equal(sql.prepare('SELECT COUNT(*) n FROM profile_keys WHERE profile=?').get(friendToken.id).n,0);
await call('/me','DELETE',{},a.token);
assert.equal((await call('/sync','POST',{},a.token)).status,401,'deleted credential revoked');
assert.equal(sql.prepare('SELECT COUNT(*) n FROM friends WHERE a=? OR b=?').get(a.id,a.id).n,0);
assert.equal(sql.prepare('SELECT COUNT(*) n FROM signals WHERE sender=? OR recipient=?').get(a.id,a.id).n,0);
assert.equal((await call('/counts','POST',{reels:1},b.token)).status,404,'no server counter endpoint');
const oldEmail=policy.supportEmail;policy.supportEmail='privacy@scrollcount.test';
const deletionPage=await worker.fetch(new Request('https://test.workers.dev/delete'),env);assert.equal(deletionPage.status,200);assert((await deletionPage.text()).includes('Delete your ScrollCount sharing profile'));
const columns=sql.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(x=>x.name);assert(!columns.some(x=>/count|total|history/.test(x)));
console.log('PASS: registration/auth, hashed credentials, pair consent, invite reuse/expiry/self guards, friend cap, private signals, TTL/ack, revocation, deletion, opaque snapshots, immutable keys, recipient isolation, TTL, deletion and legacy compatibility');

const asset=await worker.fetch(new Request('https://test.workers.dev/.well-known/assetlinks.json'),env);
assert.equal(asset.status,200);const association=await asset.json();assert.equal(association[0].target.package_name,'in.scrollcount.probe');assert.match(association[0].target.sha256_cert_fingerprints[0],/^([A-F0-9]{2}:){31}[A-F0-9]{2}$/);
const rowsBefore=sql.prepare('SELECT COUNT(*) n FROM invites').get().n;
const landing=await worker.fetch(new Request('https://test.workers.dev/invite/'+'a'.repeat(32)),env);
assert.equal(landing.status,200);const html=await landing.text();assert(html.includes('Open ScrollCount'));assert(html.includes('intent://invite/'));assert(html.includes('server=https%3A%2F%2Ftest.workers.dev'));assert.equal(sql.prepare('SELECT COUNT(*) n FROM invites').get().n,rowsBefore,'link preview does not consume invitations');
assert.equal((await worker.fetch(new Request('https://test.workers.dev/invite/invalid'),env)).status,400);
console.log('PASS: verified app association, browser fallback, malformed invite rejection and non-consuming link previews');

// Actual streamed request validation and dedicated deletion-key SQL.
for(const value of [null, [], true, 42, 'bad'])assert.equal((await call('/register','POST',value)).status,400,'JSON object required');
const oversized=await worker.fetch(new Request('https://test.workers.dev/register',{method:'POST',body:'x'.repeat(32769)}),env);
assert.equal(oversized.status,413,'chunked/unknown-length input is bounded');
const unicode=await worker.fetch(new Request('https://test.workers.dev/register',{method:'POST',body:JSON.stringify({name:'界'.repeat(12000)})}),env);
assert.equal(unicode.status,413,'limit measures bytes, not characters');
const recover=await register('Recovery');
assert.match(recover.deletionKey,/^[a-f0-9]{64}$/);assert.notEqual(recover.deletionKey,recover.token);
assert.notEqual(sql.prepare('SELECT key_hash FROM deletion_keys WHERE profile=?').get(recover.id).key_hash,recover.deletionKey);
assert.equal((await call('/sync','POST',{},recover.deletionKey)).status,401,'deletion key cannot read account data');
const rotated=(await call('/deletion-key','POST',{},recover.token)).data.deletionKey;
assert.equal((await call('/delete-profile','POST',{key:recover.deletionKey})).status,404,'old key revoked by rotation');
const crossOrigin=await worker.fetch(new Request('https://test.workers.dev/delete-profile',{method:'POST',headers:{Origin:'https://attacker.invalid'},body:JSON.stringify({key:rotated})}),env);
assert.equal(crossOrigin.status,403);assert(sql.prepare('SELECT id FROM profiles WHERE id=?').get(recover.id));
const recoveryInvite=(await call('/invite','POST',{},recover.token)).data.code;
await call('/accept','POST',{code:recoveryInvite},c.token);
await call('/snapshots','POST',snap(await key()),recover.token);
assert.equal((await call('/delete-profile','POST',{key:rotated})).status,200,'saved key works without app/session');
assert.equal((await call('/sync','POST',{},recover.token)).status,401);
for(const table of ['deletion_keys','profile_keys'])assert.equal(sql.prepare(`SELECT COUNT(*) n FROM ${table} WHERE profile=?`).get(recover.id).n,0);
assert.equal(sql.prepare('SELECT COUNT(*) n FROM friends WHERE a=? OR b=?').get(recover.id,recover.id).n,0);
assert.equal(sql.prepare('SELECT COUNT(*) n FROM invites WHERE owner=?').get(recover.id).n,0);
const legacy=await register('Legacy');assert.equal((await call('/delete-profile','POST',{key:legacy.token})).status,200,'old saved key remains usable');
const privacy=await worker.fetch(new Request('https://test.workers.dev/privacy'),env);
assert.equal(privacy.status,200);const policyHtml=await privacy.text();
for(const text of ['ScrollCount privacy policy','privacy@scrollcount.test','Operator:','background','30 days','Cloudflare','Scrolls'])assert(policyHtml.includes(text),text);
assert.equal(privacy.headers.get('Referrer-Policy'),'no-referrer');assert(privacy.headers.get('Content-Security-Policy').includes("frame-ancestors 'none'"));
const oldOperator=policy.operator;policy.operator='<script>alert(1)</script>';
const escaped=await (await worker.fetch(new Request('https://test.workers.dev/privacy'),env)).text();assert(!escaped.includes('<script>alert(1)</script>'));policy.operator=oldOperator;
policy.supportEmail='';assert.equal((await worker.fetch(new Request('https://test.workers.dev/privacy'),env)).status,503,'incomplete policy cannot masquerade as publish-ready');
policy.supportEmail=oldEmail;
console.log('PASS: bounded byte-stream JSON, dedicated hashed deletion keys, no read access, rotation, origin protection, external/legacy deletion, escaped public policy and incomplete-publication guard');
