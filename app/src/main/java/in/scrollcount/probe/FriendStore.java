package in.scrollcount.probe;
import android.content.*;
import org.json.*;
import java.util.*;
/** Private preferences contain credentials and the most recent peer totals, never UI content. */
final class FriendStore {
    private static FriendStore instance;
    static synchronized FriendStore get(Context c){if(instance==null)instance=new FriendStore(c.getApplicationContext());return instance;}
    final android.content.SharedPreferences prefs;
    final LinkedHashMap<String,Friend> friends=new LinkedHashMap<>();
    static final class Friend {
        String id,name,day="",state="Last shared",key="",uploaded="";
        long reels,shorts,received,sequence;
        Friend(String id,String name){this.id=id;this.name=name;}
    }
    private FriendStore(Context c){prefs=c.getSharedPreferences("friends",0);if(!id().isEmpty()&&prefs.getInt("sharingDefaults",0)<1)prefs.edit().putBoolean("storedSharing",true).putBoolean("hourly",true).putInt("sharingDefaults",1).apply();load();}
    void load(){
        try{JSONArray a=new JSONArray(prefs.getString("cards","[]"));for(int i=0;i<Math.min(8,a.length());i++){
            JSONObject o=a.getJSONObject(i);String id=o.getString("id");if(!id.matches("[a-f0-9]{32}"))continue;
            Friend f=new Friend(id,o.optString("name","Friend"));f.day=o.optString("day","");f.reels=o.optLong("reels");f.shorts=o.optLong("shorts");f.received=o.optLong("received");f.key=o.optString("key","");f.uploaded=o.optString("uploaded","");f.sequence=o.optLong("sequence");friends.put(id,f);
        }}catch(JSONException ignored){}
    }
    void save(){JSONArray a=new JSONArray();for(Friend f:friends.values())try{
        a.put(new JSONObject().put("id",f.id).put("name",f.name).put("day",f.day).put("reels",f.reels).put("shorts",f.shorts).put("received",f.received).put("key",f.key).put("uploaded",f.uploaded).put("sequence",f.sequence));
    }catch(JSONException ignored){}String encoded=a.toString();if(!encoded.equals(prefs.getString("cards","[]")))prefs.edit().putString("cards",encoded).apply();}
    String id(){return prefs.getString("id","");}
    String server(){return prefs.getString("server","");}
    boolean enabled(){return prefs.getBoolean("sharing",false);}
    boolean storedSharing(){return prefs.getBoolean("storedSharing",false);}
    void clear(){prefs.edit().clear().apply();friends.clear();}
}
