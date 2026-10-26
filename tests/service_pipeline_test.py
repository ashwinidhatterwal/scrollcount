"""Run the real ProbeService source/result paths with deterministic Android boundary doubles.

No copied counting implementation: production service methods are invoked reflectively.
These fixtures test orchestration, not Binder IPC or physical app layouts.
"""
from pathlib import Path
import os, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
platform = Path(os.environ['ANDROID_HOME']) / 'platforms/android-36/android.jar'
java = Path(os.environ['JAVA_HOME']) / 'bin'
doubles = {
 'android/content/BroadcastReceiver.java': '''package android.content; public abstract class BroadcastReceiver {
 public abstract void onReceive(Context context,Intent intent);
 }''',
 'android/content/Context.java': '''package android.content; public abstract class Context {
 public static SharedPreferences prefs;
 public SharedPreferences getSharedPreferences(String n,int mode){return prefs;}
 public Context getApplicationContext(){return this;}
 }''',
 'android/os/Looper.java': '''package android.os; public class Looper {
 public static Looper getMainLooper(){return new Looper();}
 }''',
 'android/os/Build.java': '''package android.os; public class Build {
 public static String MANUFACTURER="Fixture",MODEL="Fixture";
 public static class VERSION {public static int SDK_INT=36;public static String RELEASE="16";}
 }''',
 'android/view/accessibility/AccessibilityNodeInfo.java': '''package android.view.accessibility;
 public class AccessibilityNodeInfo {
 public String id="",label="",type="android.widget.TextView",pkg="com.instagram.android"; public boolean visible=true,editable,password,focused,scrollable,clickable;
 public long labelDelay;
 public AccessibilityNodeInfo[] children=new AccessibilityNodeInfo[0];
 public AccessibilityNodeInfo parent;
 public AccessibilityNodeInfo getParent(){return parent;}
 public int getWindowId(){return 11;}
 public int left=0,right=800,top=0,bottom=2400,reads;
 public void getBoundsInScreen(android.graphics.Rect r){r.set(left,top,right,bottom);}
 public boolean isVisibleToUser(){return visible;} public boolean isFocused(){return focused;} public boolean isEditable(){return editable;}
 public boolean isPassword(){return password;} public CharSequence getClassName(){return type;}
 public CharSequence getPackageName(){return pkg;}
 public boolean isScrollable(){return scrollable;} public boolean isSelected(){return false;} public boolean isClickable(){return clickable;}
 public String getViewIdResourceName(){return id;} public CharSequence getText(){reads++;android.os.SystemClock.now+=labelDelay;return label;}
 public CharSequence getContentDescription(){return null;} public int getChildCount(){return children.length;}
 public AccessibilityNodeInfo getChild(int i){return children[i];} public void recycle(){}
 }''',
 'android/view/accessibility/AccessibilityEvent.java': '''package android.view.accessibility;
 public class AccessibilityEvent {
 public String pkg="com.instagram.android",type="androidx.viewpager.widget.ViewPager";
 public int from=-1,to=-1,window=11,eventType=4096;public AccessibilityNodeInfo source;
 public int getFromIndex(){return from;} public int getToIndex(){return to;}
 public int getEventType(){return eventType;} public int getContentChangeTypes(){return 0;}
 public int getWindowId(){return window;} public CharSequence getClassName(){return type;}
 public CharSequence getPackageName(){return pkg;} public int getScrollY(){return 0;}
 public AccessibilityNodeInfo getSource(){return source;}
 public static AccessibilityEvent obtain(AccessibilityEvent e){return e;}
 public static String eventTypeToString(int type){return "event:"+type;}
 public void recycle(){}
 }''',
 'android/os/SystemClock.java': '''package android.os; public final class SystemClock {
 public static long now=10000; public static long elapsedRealtime(){return now;}
 }''',
 'android/os/Handler.java': '''package android.os; public class Handler {
 public final java.util.List<Runnable> tasks=new java.util.ArrayList<>();
 private final java.util.Map<Runnable,Long> due=new java.util.IdentityHashMap<>();
 public Handler(){} public Handler(Looper l){}
 public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long d){tasks.add(r);due.put(r,SystemClock.now+d);return true;}
 public void removeCallbacks(Runnable r){tasks.removeIf(t->t==r);due.remove(r);}
 public void runUntil(long end){int steps=0;while(true){Runnable next=null;long first=Long.MAX_VALUE;
 for(Runnable r:tasks){long when=due.get(r);if(when<first){first=when;next=r;}}
 if(next==null || first>end)break;if(++steps>1000)throw new AssertionError("Unbounded scheduling");
 tasks.remove(next);due.remove(next);SystemClock.now=Math.max(SystemClock.now,first);next.run();}
 SystemClock.now=Math.max(SystemClock.now,end);}
 }''',
 'android/graphics/Rect.java': '''package android.graphics; public class Rect {
 public int left,top,right,bottom; public Rect(){} public Rect(int l,int t,int r,int b){set(l,t,r,b);}
 public Rect(Rect r){set(r.left,r.top,r.right,r.bottom);}
 public int centerX(){return (left+right)/2;} public int centerY(){return (top+bottom)/2;}
 public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public int width(){return right-left;} public int height(){return bottom-top;}
 public String flattenToString(){return left+" "+top+" "+right+" "+bottom;}
 }''',
 'android/util/DisplayMetrics.java': '''package android.util; public class DisplayMetrics {
 public int widthPixels=1080,heightPixels=2400; public float density=1;
 }''',
 'android/content/res/Resources.java': '''package android.content.res; public class Resources {
 public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}
 }''',
 'android/os/PowerManager.java': '''package android.os; public class PowerManager {
 public boolean isInteractive(){return true;}
 }''',
 'android/app/KeyguardManager.java': '''package android.app; public class KeyguardManager {
 public boolean isKeyguardLocked(){return false;}
 }''',
 'android/accessibilityservice/AccessibilityService.java': '''package android.accessibilityservice;
 public abstract class AccessibilityService extends android.content.Context {
 public void onCreate(){} public void onDestroy(){} protected void onServiceConnected(){}
 public abstract void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent e);
 public abstract void onInterrupt();
 public Object getSystemService(String name){return name.equals("power")?new android.os.PowerManager():new android.app.KeyguardManager();}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public static AccessibilityServiceInfo info=new AccessibilityServiceInfo();
 public AccessibilityServiceInfo getServiceInfo(){return info;}
 public void setServiceInfo(AccessibilityServiceInfo i){}
 public String getPackageName(){return "in.scrollcount.probe";}
 public static android.view.accessibility.AccessibilityNodeInfo root;
 public android.view.accessibility.AccessibilityNodeInfo getRootInActiveWindow(){return root;}
 public java.util.List<android.view.accessibility.AccessibilityWindowInfo> getWindows(){return java.util.Collections.emptyList();}
 }''',
 'android/accessibilityservice/AccessibilityServiceInfo.java': '''package android.accessibilityservice;
 public class AccessibilityServiceInfo { public int flags; }
 ''',
}
with tempfile.TemporaryDirectory() as temp:
 temp = Path(temp)
 classes = temp / 'classes'
 production = [str(p) for p in (root / 'app/src/main/java').rglob('*.java')]
 subprocess.run([str(java/'javac'), '-classpath', str(platform), '-d', str(classes),
                 *production, str(root/'tests/ServicePipelineTest.java')], check=True)
 shadow = []
 for name, source in doubles.items():
  path = temp / name
  path.parent.mkdir(parents=True, exist_ok=True)
  path.write_text(source)
  shadow.append(str(path))
 subprocess.run([str(java/'javac'), '-classpath', str(platform), '-d', str(classes), *shadow], check=True)
 subprocess.run([str(java/'java'), '-Dscrollcount.fixture='+str(root/'tests/fixtures/snapchat-14.29-layout.txt'), '-cp', str(classes)+os.pathsep+str(platform),
                 'in.scrollcount.probe.ServicePipelineTest'], check=True)
