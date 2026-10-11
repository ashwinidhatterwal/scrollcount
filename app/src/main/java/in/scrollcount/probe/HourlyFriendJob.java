package in.scrollcount.probe;
import android.app.job.*;
import android.content.*;
import android.os.*;
/** Network-constrained, persisted, bounded hourly upload/download. No exact alarm or wake lock. */
public final class HourlyFriendJob extends JobService {
    private static final int ID=16002;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JobParameters active;private FriendSync sync;
    private final Runnable finish=()->complete();
    static void ensure(Context context){
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        FriendStore store=FriendStore.get(context);
        if(!store.enabled()||!store.storedSharing()||store.id().isEmpty()||!store.prefs.getBoolean("hourly",true)){scheduler.cancel(ID);return;}
        if(scheduler.getPendingJob(ID)!=null)return;
        schedule(context);
    }
    private static void schedule(Context context){
        FriendStore store=FriendStore.get(context);
        if(!store.enabled()||!store.storedSharing()||store.id().isEmpty()||!store.prefs.getBoolean("hourly",true))return;
        long now=System.currentTimeMillis(),next=HourlySyncPlan.next(now);
        JobInfo job=new JobInfo.Builder(ID,new ComponentName(context,HourlyFriendJob.class))
            .setMinimumLatency(next-now).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true).setBackoffCriteria(15*60000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
        int result=((JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(job);
        store.prefs.edit().putLong("nextHourlyAttempt",result==JobScheduler.RESULT_SUCCESS?next:0).apply();
    }
    @Override public boolean onStartJob(JobParameters params){
        FriendStore store=FriendStore.get(this);
        if(!store.enabled()||!store.storedSharing()||store.id().isEmpty()||!store.prefs.getBoolean("hourly",true))return false;
        active=params;sync=FriendSync.get(this);
        store.prefs.edit().putLong("lastHourlyAttempt",System.currentTimeMillis()).apply();
        handler.postDelayed(finish,HourlySyncPlan.WINDOW);
        sync.background(true,finish);
        return true;
    }
    private void complete(){
        if(active==null)return;JobParameters params=active;active=null;handler.removeCallbacks(finish);
        if(sync!=null){sync.background(false);sync=null;}
        jobFinished(params,false);schedule(this);
    }
    @Override public boolean onStopJob(JobParameters params){
        active=null;handler.removeCallbacks(finish);if(sync!=null){sync.background(false);sync=null;}
        FriendStore store=FriendStore.get(this);return store.enabled()&&store.storedSharing()&&store.prefs.getBoolean("hourly",true);
    }
    @Override public void onDestroy(){handler.removeCallbacks(finish);if(sync!=null){sync.background(false);sync=null;}super.onDestroy();}
}
