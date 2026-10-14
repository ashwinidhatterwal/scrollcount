package in.scrollcount.probe;
import java.util.*;
import java.util.concurrent.*;
public final class ReadLaneTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final class Manual implements Executor {
        final ArrayDeque<Runnable> work=new ArrayDeque<>();
        public void execute(Runnable r){work.add(r);}
        void drain(){while(!work.isEmpty())work.removeFirst().run();}
    }
    public static void main(String[] args)throws Exception {
        Manual worker=new Manual();ReadLane lane=new ReadLane(worker);int[] reads={0},releases={0};
        for(int i=0;i<100;i++)lane.submit("scan",()->reads[0]++,()->releases[0]++);
        check(worker.work.size()==1&&lane.pending()==1,"storms schedule one reader and latest snapshot");
        worker.drain();check(reads[0]==1&&releases[0]==100,"discarded and accepted copies released exactly once");
        for(int i=0;i<100;i++)lane.submit("scroll"+i,()->reads[0]++,()->releases[0]++);
        check(lane.pending()==24,"distinct scroll queue strictly bounded");
        lane.cancelPending();worker.drain();check(reads[0]==1&&releases[0]==200,"exit cancels old work and releases resources");
        int[] errors={0};ReadLane faults=new ReadLane(worker,e->errors[0]++);
        faults.submit("bad",()->{throw new IllegalStateException();},()->releases[0]++);
        faults.submit("next",()->reads[0]++,()->{throw new IllegalArgumentException();});worker.drain();
        check(errors[0]==2&&reads[0]==2,"read/release faults cannot strand the worker");
        lane.close();lane.submit("closed",()->reads[0]++,()->releases[0]++);
        check(reads[0]==2&&releases[0]==202,"closed reader refuses work but releases copied event");
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1),blocked=new CountDownLatch(1),completed=new CountDownLatch(1);
        ReadLane real=new ReadLane(executor);
        real.submit("slow",()->{entered.countDown();try{blocked.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}},()->completed.countDown());
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try {
            check(entered.await(2,TimeUnit.SECONDS),"slow reader started");
            Future<?> accepted=caller.submit(()->{real.cancelPending();real.submit("queued",()->{},()->{});});
            accepted.get(2,TimeUnit.SECONDS);
            check(completed.getCount()==1,"main-side admission completes while reader remains blocked");
            blocked.countDown();check(completed.await(2,TimeUnit.SECONDS),"slow reader releases on completion");
        } finally {blocked.countDown();real.close();executor.shutdownNow();caller.shutdownNow();}
        System.out.println("PASS: single worker, burst coalescing, bounded queue, exact resource release, cancellation, fault recovery and nonblocking main admission during stalled read");
    }
}
