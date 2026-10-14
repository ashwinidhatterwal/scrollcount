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
 'android/view/accessibility/AccessibilityNodeInfo.java': '''package android.view.accessibility;
 public class AccessibilityNodeInfo {
 public String id="",label=""; public boolean visible=true,editable,password;
 public long labelDelay;
 public AccessibilityNodeInfo[] children=new AccessibilityNodeInfo[0];
 public int right=800;
 public void getBoundsInScreen(android.graphics.Rect r){r.set(0,0,right,2400);}
 public boolean isVisibleToUser(){return visible;} public boolean isEditable(){return editable;}
 public boolean isPassword(){return password;} public CharSequence getClassName(){return "android.widget.TextView";}
 public CharSequence getPackageName(){return "com.instagram.android";}
 public boolean isSelected(){return false;} public boolean isClickable(){return false;}
 public String getViewIdResourceName(){return id;} public CharSequence getText(){android.os.SystemClock.now+=labelDelay;return label;}
 public CharSequence getContentDescription(){return null;} public int getChildCount(){return children.length;}
 public AccessibilityNodeInfo getChild(int i){return children[i];} public void recycle(){}
 }''',
 'android/os/SystemClock.java': '''package android.os; public final class SystemClock {
 public static long now=10000; public static long elapsedRealtime(){return now;}
 }''',
 'android/os/Handler.java': '''package android.os; public class Handler {
 public final java.util.List<Runnable> tasks=new java.util.ArrayList<>();
 public Handler(){} public Handler(Looper l){}
 public boolean post(Runnable r){tasks.add(r);return true;}
 public boolean postDelayed(Runnable r,long d){tasks.add(r);return true;}
 public void removeCallbacks(Runnable r){tasks.removeIf(t->t==r);}
 }''',
 'android/graphics/Rect.java': '''package android.graphics; public class Rect {
 public int left,top,right,bottom; public Rect(){} public Rect(int l,int t,int r,int b){set(l,t,r,b);}
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
 public AccessibilityServiceInfo getServiceInfo(){return null;}
 public void setServiceInfo(AccessibilityServiceInfo i){}
 public String getPackageName(){return "in.scrollcount.probe";}
 public static android.view.accessibility.AccessibilityNodeInfo root;
 public android.view.accessibility.AccessibilityNodeInfo getRootInActiveWindow(){return root;}
 public java.util.List<android.view.accessibility.AccessibilityWindowInfo> getWindows(){return java.util.Collections.emptyList();}
 }''',
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
 subprocess.run([str(java/'java'), '-cp', str(classes)+os.pathsep+str(platform),
                 'in.scrollcount.probe.ServicePipelineTest'], check=True)
