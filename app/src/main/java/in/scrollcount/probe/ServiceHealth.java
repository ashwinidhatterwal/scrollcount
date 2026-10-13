package in.scrollcount.probe;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
/** Bounded, content-free local lifecycle/fault evidence; no global crash-handler override. */
final class ServiceHealth {
    private static Context app;
    private static long lastFailure=-1,lastExitRead=-1;
    private static String exitReport="";
    static void attach(Context context) { app=context.getApplicationContext(); }
    static void event(String phase) { save(phase); }
    static void failure(String phase,RuntimeException error) {
        long now=android.os.SystemClock.elapsedRealtime();
        if(lastFailure>=0 && now-lastFailure<30000)return;
        lastFailure=now;
        // Exception messages can embed UI data. Save only code locations and exception types.
        String location="";
        for(StackTraceElement frame:error.getStackTrace()) {
            if(frame.getClassName().startsWith("in.scrollcount.probe.")) {
                location=" at "+frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber();break;
            }
        }
        save(phase+" · "+error.getClass().getSimpleName()+location);
    }
    private static void save(String value) {
        if(app==null)return;
        try {
            SharedPreferences prefs=app.getSharedPreferences("service_health",0);
            String[] previous=prefs.getString("events","").split("\n");
            StringBuilder out=new StringBuilder();
            for(int i=Math.max(0,previous.length-11);i<previous.length;i++)if(!previous[i].isEmpty())out.append(previous[i]).append('\n');
            out.append(System.currentTimeMillis()).append(" · ").append(value);
            prefs.edit().putString("events",out.toString()).apply();
        } catch(RuntimeException ignored) { /* Storage failures cannot stop detection. */ }
    }
    static boolean enabled(Context context) {
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName own=new ComponentName(context,ProbeService.class);
        if(enabled!=null)for(String part:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(part)))return true;
        return false;
    }
    static String report(Context context) {
        try { return reportSafely(context); }
        catch(RuntimeException ignored) { return "Service health temporarily unavailable"; }
    }
    private static String reportSafely(Context context) {
        StringBuilder out=new StringBuilder("Service health\n");
        try { out.append("Android accessibility setting: ").append(enabled(context)?"enabled":"off").append('\n'); }
        catch(RuntimeException ignored) { out.append("Android accessibility setting: unavailable\n"); }
        out.append("Service connected in this process: ").append(ProbeStore.connected).append('\n');
        android.os.PowerManager power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if(power!=null)out.append("Battery optimization exemption: ").append(power.isIgnoringBatteryOptimizations(context.getPackageName())).append('\n');
        out.append("Recent local health events (timestamps are epoch milliseconds):\n");
        try { out.append(context.getSharedPreferences("service_health",0).getString("events","No events recorded yet")).append('\n'); }
        catch(RuntimeException ignored) { out.append("Health history unavailable\n"); }
        if(Build.VERSION.SDK_INT>=30) {
            long now=android.os.SystemClock.elapsedRealtime();
            if(lastExitRead<0 || now-lastExitRead>=30000) { exitReport=exitReasons(context); lastExitRead=now; }
            out.append(exitReport);
        }
        return out.toString();
    }
    private static String exitReasons(Context context) {
        if(Build.VERSION.SDK_INT<30)return "Process exit information requires Android 11 or newer\n";
        StringBuilder out=new StringBuilder("Recent process exits reported by Android (not necessarily service failures):\n");
        try {
            android.app.ActivityManager manager=context.getSystemService(android.app.ActivityManager.class);
            if(manager==null)return "Process exit information unavailable\n";
            java.util.List<android.app.ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,4);
            for(android.app.ApplicationExitInfo exit:exits)out.append(exit.getTimestamp()).append(" · ").append(reason(exit.getReason())).append(" · status ").append(exit.getStatus()).append('\n');
            if(exits.isEmpty())out.append("No recorded exits\n");
        } catch(RuntimeException ignored) { out.append("Process exit information unavailable\n"); }
        return out.toString();
    }
    private static String reason(int reason) {
        switch(reason) {
            case 2:return "Signal / terminated";
            case 3:return "Low memory";
            case 4:return "Java crash";
            case 5:return "Native crash";
            case 6:return "Not responding (ANR)";
            case 9:return "Excessive resource usage";
            case 10:return "User requested / force stop";
            case 11:return "User stopped";
            case 12:return "Dependency died";
            case 13:return "Other system reason";
            default:return "Reason "+reason;
        }
    }
    static void clear(Context context) {
        context.getSharedPreferences("service_health",0).edit().clear().apply();
        lastFailure=-1;
    }
}
