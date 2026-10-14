package in.scrollcount.probe;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** One reader, bounded pending work. Replaced events are always released. No UI locks during IPC. */
final class ReadLane {
    private static final int LIMIT=24;
    private final Executor executor;
    private final java.util.function.Consumer<RuntimeException> failure;
    private final ArrayDeque<Job> pending=new ArrayDeque<>();
    private boolean running,closed;
    private long dropped;
    private static final class Job {
        final String key; final Runnable read,release;
        Job(String key,Runnable read,Runnable release){this.key=key;this.read=read;this.release=release;}
    }
    ReadLane(java.util.function.Consumer<RuntimeException> failure){this(Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ScrollCount-reader");t.setDaemon(true);return t;}),failure);}
    ReadLane(Executor executor){this(executor,error->{});}
    ReadLane(Executor executor,java.util.function.Consumer<RuntimeException> failure){this.executor=executor;this.failure=failure;}
    private void release(Job job){CallbackGuard.run(job.release,failure::accept);}
    synchronized void submit(String key,Runnable read,Runnable release) {
        if(closed){CallbackGuard.run(release,failure::accept);return;}
        java.util.Iterator<Job> iterator=pending.iterator();
        while(iterator.hasNext()) {
            Job previous=iterator.next();
            if(previous.key.equals(key)){iterator.remove();release(previous);dropped++;break;}
        }
        if(pending.size()>=LIMIT){release(pending.removeFirst());dropped++;}
        pending.addLast(new Job(key,read,release));
        if(!running){running=true;executor.execute(this::drain);}
    }
    private void drain() {
        while(true) {
            Job job;
            synchronized(this){job=pending.pollFirst();if(job==null){running=false;return;}}
            try {CallbackGuard.run(job.read,failure::accept);} finally {release(job);}
        }
    }
    synchronized void cancelPending(){while(!pending.isEmpty())release(pending.removeFirst());}
    synchronized void close(){closed=true;cancelPending();if(executor instanceof ExecutorService)((ExecutorService)executor).shutdown();}
    synchronized int pending(){return pending.size();}
    synchronized long dropped(){return dropped;}
}
