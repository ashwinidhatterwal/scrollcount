"""Execute the real preference migration without an Android device."""
from pathlib import Path
import tempfile,subprocess,os
root=Path(__file__).resolve().parents[1]
s={
'android/content/Context.java':'package android.content;public class Context {public SharedPreferences prefs=new SharedPreferences();public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String n,int m){return prefs;}}',
'android/content/SharedPreferences.java':'''package android.content;import java.util.*;public class SharedPreferences {Map<String,Object> m=new HashMap<>();public String getString(String k,String d){return (String)m.getOrDefault(k,d);}public boolean getBoolean(String k,boolean d){return (Boolean)m.getOrDefault(k,d);}public int getInt(String k,int d){return (Integer)m.getOrDefault(k,d);}public SharedPreferences edit(){return this;}public SharedPreferences putString(String k,String v){m.put(k,v);return this;}public SharedPreferences putBoolean(String k,boolean v){m.put(k,v);return this;}public SharedPreferences putInt(String k,int v){m.put(k,v);return this;}public SharedPreferences clear(){m.clear();return this;}public void apply(){}}''',
'org/json/JSONException.java':'package org.json;public class JSONException extends Exception {}',
'org/json/JSONArray.java':'package org.json;public class JSONArray {public JSONArray(){}public JSONArray(String s)throws JSONException{}public int length(){return 0;}public JSONObject getJSONObject(int i)throws JSONException{throw new JSONException();}public JSONArray put(JSONObject o){return this;}public String toString(){return "[]";}}',
'org/json/JSONObject.java':'package org.json;public class JSONObject {public String getString(String k)throws JSONException{return "";}public String optString(String k,String d){return d;}public long optLong(String k){return 0;}public JSONObject put(String k,Object v)throws JSONException{return this;}}',
'in/scrollcount/probe/FriendDefaultsTest.java':'''package in.scrollcount.probe;import android.content.*;public class FriendDefaultsTest {static FriendStore load(Context c)throws Exception{var ctor=FriendStore.class.getDeclaredConstructor(Context.class);ctor.setAccessible(true);return ctor.newInstance(c);}static void check(boolean b,String m){if(!b)throw new AssertionError(m);}public static void main(String[] a)throws Exception{Context c=new Context();check(!load(c).enabled()&&!load(c).storedSharing(),"no profile means no automatic sharing");c.prefs.edit().putString("id","a".repeat(32)).putString("privateKey","keep").putBoolean("sharing",false).putBoolean("hourly",false).apply();FriendStore f=load(c);check(f.storedSharing()&&c.prefs.getBoolean("hourly",false),"upgrade enables reliable/hourly defaults");check(!f.enabled(),"paused sharing stays paused");check(c.prefs.getString("privateKey","").equals("keep"),"key remains unchanged");c.prefs.edit().putBoolean("hourly",false).apply();load(c);check(!c.prefs.getBoolean("hourly",true),"subsequent user choice remains off");System.out.println("PASS: actual defaults migration, profile requirement, paused sharing, preserved key and later hourly preference");}}'''
}
with tempfile.TemporaryDirectory() as temp:
 d=Path(temp)
 for n,text in s.items():p=d/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
 files=[str(p) for p in d.rglob('*.java')]+[str(root/'app/src/main/java/in/scrollcount/probe/FriendStore.java')]
 java=Path(os.environ['JAVA_HOME'])/'bin'
 subprocess.run([str(java/'javac'),'-d',str(d/'classes'),*files],check=True)
 subprocess.run([str(java/'java'),'-cp',str(d/'classes'),'in.scrollcount.probe.FriendDefaultsTest'],check=True)
