package in.scrollcount.probe;
import android.app.Activity;
import android.content.Context;
import android.os.*;
import org.json.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
/** Encrypted latest snapshots: one request owner, no overlapping UI/job writes or peer rendezvous. */
final class FriendSync {
    interface Listener{void changed();}
    private static FriendSync instance;
    static FriendSync get(Context c){if(instance==null)instance=new FriendSync(c.getApplicationContext());return instance;}
    final FriendStore store;String status="Ready";
    private final Context context;private final Handler handler=new Handler(Looper.getMainLooper());
    private final FriendApi api=new FriendApi();private final ExecutorService crypto=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    // Only successfully verified envelopes enter this bounded, in-memory cache.
    private final Map<String,String> verified=new HashMap<>();
    private boolean background,running,busy,automatic,changed,autoQueued;private long generation,lastSync=-120000;private Runnable backgroundDone;
    private final Runnable loop=new Runnable(){public void run(){if(!running)return;guard(()->sync(false));if(running)handler.postDelayed(this,30000);}};
    private FriendSync(Context c){context=c;store=FriendStore.get(c);HistoryStore.get(c).observeCounts(this::countsChanged);}
    private final Runnable autoSend=()->guard(()->{autoQueued=false;if(!running||busy)return;changed=false;sync(true);});
    private void countsChanged(){
        if(!store.enabled()||!store.storedSharing()||store.id().isEmpty()||store.friends.isEmpty())return;
        changed=true;automatic=true;reconcile();
        if(!autoQueued&&!busy){autoQueued=true;handler.postDelayed(autoSend,SyncCadence.delay(SystemClock.elapsedRealtime(),lastSync));}
    }
    void observe(Activity a,Listener l){listeners.add(l);reconcile();handler.removeCallbacks(loop);if(running)handler.post(loop);notifyChanged();}
    void unobserve(Listener l){listeners.remove(l);reconcile();}
    void background(boolean on){background(on,null);}
    void background(boolean on,Runnable done){background=on;backgroundDone=on?done:null;reconcile();if(on){lastSync=-120000;sync(true);}}
    void configurationChanged(){verified.clear();stop();HourlyFriendJob.ensure(context);reconcile();notifyChanged();}
    void refresh(){lastSync=-120000;sync(true);}
    private void reconcile(){
        boolean wanted=store.enabled()&&store.storedSharing()&&!store.id().isEmpty()&&(!listeners.isEmpty()||automatic||(background&&store.prefs.getBoolean("hourly",true)));
        if(wanted&&!running){running=true;generation++;if(!listeners.isEmpty()){lastSync=-120000;handler.post(loop);}}else if(!wanted&&running)stop();
    }
    private void stop(){running=false;busy=false;automatic=false;changed=false;autoQueued=false;generation++;handler.removeCallbacks(loop);handler.removeCallbacks(autoSend);}
    private void notifyChanged(){for(Listener l:new ArrayList<>(listeners))CallbackGuard.run(l::changed,e->ServiceHealth.failure("sharing listener",e));}
    private void guard(Runnable action){CallbackGuard.run(action,e->{ServiceHealth.failure("sharing callback",e);stop();status="Sharing paused after an error. Open Friends to retry.";});}
    private void post(Runnable action){handler.post(()->guard(action));}
    private void sync(boolean force){
        if(!running||busy||(!force&&SystemClock.elapsedRealtime()-lastSync<30000))return;
        busy=true;long g=generation,day=DailyBadge.start(System.currentTimeMillis());status="Syncing…";notifyChanged();
        HistoryStore.get(context).dailyItems(day,DailyBadge.end(day),totals->{
            if(g!=generation)return;
            if(totals[3]==0){finish("Could not read daily totals. Try again.");return;}
            if(day!=DailyBadge.start(System.currentTimeMillis())){busy=false;sync(true);return;}
            try{
                String date=FriendSnapshot.day(System.currentTimeMillis()),fingerprint=date+":"+totals[0]+":"+totals[1]+":"+Math.floorDiv(System.currentTimeMillis(),HourlySyncPlan.HOUR);
                long sequence=store.prefs.getLong("outSequence",0);
                if(!fingerprint.equals(store.prefs.getString("outFingerprint",""))||sequence==0){sequence++;if(!store.prefs.edit().putLong("outSequence",sequence).putString("outFingerprint",fingerprint).commit())throw new IllegalStateException();}
                String payload=new JSONObject().put("v",1).put("day",date).put("reels",totals[0]).put("shorts",totals[1]).put("sequence",sequence).put("captured",System.currentTimeMillis()).toString();
                exchange(g,fingerprint,payload,0);
            }catch(Exception e){finish("Could not prepare sharing data. Try again.");}
        });
    }
    private KeyPair keys() throws GeneralSecurityException {
        String secret=store.prefs.getString("privateKey",""),pub=store.prefs.getString("publicKey","");
        if(secret.isEmpty()&&pub.isEmpty()){
            KeyPair pair=FriendCipher.create();if(!store.prefs.edit().putString("privateKey",FriendCipher.encode(pair.getPrivate().getEncoded())).putString("publicKey",FriendCipher.encode(pair.getPublic().getEncoded())).commit())throw new GeneralSecurityException("Could not save key");return pair;
        }
        if(secret.isEmpty()||pub.isEmpty())throw new GeneralSecurityException("Incomplete key");
        return FriendCipher.restore(secret,pub);
    }
    private void exchange(long g,String fingerprint,String payload,int phase){
        Map<String,String> recipients=new LinkedHashMap<>();
        for(FriendStore.Friend f:store.friends.values())if(!f.key.isEmpty()&&!fingerprint.equals(f.uploaded)&&!f.state.equals("Sharing key changed · Reconnect"))recipients.put(f.id,f.key);
        String own=store.id();final KeyPair pair;
        try{pair=keys();}catch(Exception e){finish("Sharing encryption unavailable. Recreate your sharing profile if needed.");return;}
        crypto.execute(()->{
            try{
                JSONArray updates=new JSONArray();
                for(Map.Entry<String,String> recipient:recipients.entrySet()){
                    FriendCipher.Envelope e=FriendCipher.encrypt(pair,recipient.getValue(),own,recipient.getKey(),payload);
                    updates.put(new JSONObject().put("to",recipient.getKey()).put("iv",e.iv).put("ciphertext",e.ciphertext));
                }
                JSONObject request=new JSONObject().put("key",FriendCipher.encode(pair.getPublic().getEncoded())).put("updates",updates);
                post(()->{if(g!=generation)return;api.request(store,"/snapshots","POST",request,(r,error)->{
                    if(g!=generation)return;
                    if(!error.isEmpty()){finish(error.equals("Not found")?"Update the sharing backend to enable saved snapshots":error);return;}
                    try{
                        Set<String> ids=new HashSet<>();boolean newKeys=false;JSONArray list=r.getJSONArray("friends");
                        for(int i=0;i<Math.min(8,list.length());i++){
                            JSONObject entry=list.getJSONObject(i);String id=entry.getString("id");if(!id.matches("[a-f0-9]{32}"))continue;ids.add(id);
                            FriendStore.Friend f=store.friends.get(id);if(f==null){f=new FriendStore.Friend(id,entry.getString("name"));store.friends.put(id,f);}else f.name=entry.getString("name");
                            String key=entry.optString("public_key","");if(key.equals("null"))key="";
                            if(!f.key.isEmpty()&&!key.isEmpty()&&!f.key.equals(key)){f.state="Sharing key changed · Reconnect";continue;}
                            if(f.key.isEmpty()&&!key.isEmpty()){f.key=key;newKeys=true;}
                            f.state=key.isEmpty()?"Awaiting update":"Synced";
                            if(recipients.containsKey(id))f.uploaded=fingerprint;
                        }
                        store.friends.keySet().retainAll(ids);store.save();
                        if(newKeys&&phase==0){exchange(g,fingerprint,payload,1);return;}
                        decrypt(g,pair,own,r.getJSONArray("snapshots"));
                    }catch(JSONException e){finish("Invalid sharing response. Try again.");}
                });});
            }catch(Exception e){post(()->{if(g==generation)finish("Sharing encryption unavailable. Try again or recreate your sharing profile.");});}
        });
    }
    private void decrypt(long g,KeyPair pair,String own,JSONArray snapshots){
        Map<String,String> pins=new HashMap<>();
        final Map<String,String> cached=new HashMap<>(verified);for(FriendStore.Friend f:store.friends.values())if(!f.state.equals("Sharing key changed · Reconnect"))pins.put(f.id,f.key);
        crypto.execute(()->{
            Map<String,JSONObject> valid=new HashMap<>();Map<String,String> checked=new HashMap<>();Set<String> invalid=new HashSet<>();
            for(int i=0;i<Math.min(8,snapshots.length());i++)try{
                JSONObject s=snapshots.getJSONObject(i);String sender=s.getString("sender"),key=pins.get(sender);if(key==null||key.isEmpty())continue;
                String envelope=key+":"+s.getString("iv")+":"+s.getString("ciphertext");
                if(envelope.equals(cached.get(sender)))continue;
                JSONObject data=new JSONObject(FriendCipher.decrypt(pair,key,sender,own,s.getString("iv"),s.getString("ciphertext")));
                if(data.length()!=6||data.getInt("v")!=1||!FriendSnapshot.valid(data.getString("day"),data.getLong("reels"),data.getLong("shorts"))||data.getLong("sequence")<=0||data.getLong("captured")<=0)continue;
                valid.put(sender,data);checked.put(sender,envelope);
            }catch(Exception ignored){try{invalid.add(snapshots.getJSONObject(i).getString("sender"));}catch(Exception absent){}}
            post(()->{
                if(g!=generation)return;
                verified.keySet().retainAll(store.friends.keySet());verified.putAll(checked);
                for(Map.Entry<String,JSONObject> entry:valid.entrySet())try{
                    FriendStore.Friend f=store.friends.get(entry.getKey());JSONObject d=entry.getValue();if(f==null||d.getLong("sequence")<=f.sequence)continue;
                    f.sequence=d.getLong("sequence");f.reels=d.getLong("reels");f.shorts=d.getLong("shorts");f.day=d.getString("day");f.received=d.getLong("captured");
                }catch(JSONException ignored){}
                for(String id:invalid){FriendStore.Friend f=store.friends.get(id);if(f!=null)f.state="Could not verify saved update";}
                store.save();store.prefs.edit().putLong("lastSnapshotSync",System.currentTimeMillis()).apply();finish(invalid.isEmpty()?"Up to date":"Some updates unavailable");
            });
        });
    }
    private void finish(String message){
        busy=false;lastSync=SystemClock.elapsedRealtime();status=message;notifyChanged();
        automatic=false;boolean pending=changed;reconcile();if(pending)countsChanged();
        Runnable done=backgroundDone;if(background&&done!=null){backgroundDone=null;post(done);}
    }
}
