package in.scrollcount.probe;
import android.app.*;
import android.content.*;
import android.os.Build;
/** Optional, event-driven reminder. No alarms, polling worker or notification permission at setup. */
final class Wellbeing {
    private String remindedSession;
    void observe(Context c,CountingEngine.Live live) {
        if(live==null)return;
        int minutes=c.getSharedPreferences("privacy",0).getInt("reminderMinutes",0);
        if(!ReminderRule.due(minutes,live.sessionDuration,live.paused,live.sessionId.equals(remindedSession)))return;
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(!manager.areNotificationsEnabled())return;
        manager.createNotificationChannel(new NotificationChannel("breaks","Optional session reminders",NotificationManager.IMPORTANCE_LOW));
        if(manager.getNotificationChannel("breaks").getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        Intent intent=new Intent(c,ProbeActivity.class);
        PendingIntent open=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(1,new Notification.Builder(c,"breaks").setSmallIcon(c.getResources().getIdentifier("ic_notification","drawable",c.getPackageName())).setContentTitle("A moment to check in").setContentText("This short-video session has lasted "+minutes+" minutes.").setStyle(new Notification.BigTextStyle().bigText("This short-video session has lasted "+minutes+" minutes. Take a break whenever it feels right.")).setContentIntent(open).setAutoCancel(true).build());
        remindedSession=live.sessionId;
    }
}
