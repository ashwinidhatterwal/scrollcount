package in.scrollcount.probe;
import java.util.UUID;
/** On-device exposure timing. Identities remain in memory and never enter the sink. */
public final class CountingEngine {
    public interface Sink {
        void startSession(String id,String platform,long startedAt);
        void exposure(String session,String platform,long start,long end,long duration,String classification);
        void endSession(String id,long endedAt);
        default void detected(String session,String platform,long startedAt) {
        }
        default void checkpoint(String session,String platform,long start,long end,long duration,String classification) {
        }
    }
    public static final class Live {
        public final String platform,sessionId;
        public final long duration,startedAt,sessionDuration,sessionStartedAt;
        public final boolean paused;
        Live(String p,long d,long startedAt,boolean paused,long sessionDuration,long sessionStartedAt,String sessionId) {
            platform=p;
            duration=d;
            this.startedAt=startedAt;
            this.paused=paused;
            this.sessionDuration=sessionDuration;
            this.sessionStartedAt=sessionStartedAt;
            this.sessionId=sessionId;
        }
    }
    private final Sink sink;
    private String session,platform,identity;
    private long startWall,activeSince=-1,accumulated,sessionAccumulated,sessionStartWall;
    public CountingEngine(Sink sink) {
        this.sink=sink;
    }
    public static String classification(long duration) {
        return duration<2000?"SKIPPED":duration>=10000?"LONGER":"SEEN";
    }
    public synchronized void observe(String platform,String identity,long committedAt,long now,long wall) {
        long boundary=Math.min(now,Math.max(0,committedAt));
        if(session!=null && !this.platform.equals(platform)) close(now,wall);
        if(session==null) {
            session=UUID.randomUUID().toString();
            this.platform=platform;
            sessionAccumulated=0;
            sessionStartWall=wall-(now-boundary);
            sink.startSession(session,platform,sessionStartWall);
        }
        if(this.identity==null) begin(identity,boundary,wall-(now-boundary));
        else if(!this.identity.equals(identity)) {
            finish(boundary,wall-(now-boundary));
            begin(identity,boundary,wall-(now-boundary));
        }
        else if(activeSince<0) activeSince=now;
    }
    private void begin(String identity,long elapsed,long wall) {
        this.identity=identity;
        startWall=wall;
        accumulated=0;
        activeSince=elapsed;
        sink.detected(session,platform,startWall);
    }
    public synchronized void pause(long now) {
        if(activeSince>=0) {
            accumulated+=Math.max(0,now-activeSince);
            activeSince=-1;
        }
    }
    private void finish(long now,long wall) {
        if(identity==null) return;
        pause(now);
        sessionAccumulated+=accumulated;
        if(accumulated>0) sink.exposure(session,platform,startWall,Math.max(startWall,wall),accumulated,classification(accumulated));
        identity=null;
        accumulated=0;
    }
    public synchronized void close(long now,long wall) {
        if(session==null) return;
        finish(now,wall);
        sink.endSession(session,wall);
        session=platform=identity=null;
        activeSince=-1;
    }
    public synchronized void checkpoint(long now,long wall) {
        if(identity==null)return;
        long duration=accumulated+(activeSince>=0?Math.max(0,now-activeSince):0);
        if(duration>0)sink.checkpoint(session,platform,startWall,Math.max(startWall,wall),duration,classification(duration));
    }
    public synchronized Live live(long now) {
        if(identity==null) return null;
        return new Live(platform,accumulated+(activeSince>=0?Math.max(0,now-activeSince):0),startWall,activeSince<0,sessionAccumulated+accumulated+(activeSince>=0?Math.max(0,now-activeSince):0),sessionStartWall,session);
    }
}
