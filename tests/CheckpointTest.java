package in.scrollcount.probe;
public final class CheckpointTest {
    static final class Sink implements CountingEngine.Sink {
        long duration,start,end;String session,category;int checkpoints,exposures;
        public void startSession(String id,String p,long at){session=id;}
        public void exposure(String s,String p,long start,long end,long d,String c){exposures++;}
        public void endSession(String s,long at){}
        public void checkpoint(String s,String p,long start,long end,long d,String c){checkpoints++;this.start=start;this.end=end;duration=d;category=c;if(!session.equals(s))throw new AssertionError("same session");}
    }
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){Sink s=new Sink();CountingEngine e=new CountingEngine(s);e.observe("YouTube","a",1000,1000,101000);e.checkpoint(4000,104000);check(s.duration==3000 && s.category.equals("SEEN") && s.start==101000,"saved metadata without committing active exposure");check(s.exposures==0,"checkpoint not double counted");e.pause(5000);e.checkpoint(20000,120000);check(s.duration==4000,"paused time not checkpointed as active");e.observe("YouTube","a",1000,25000,125000);e.observe("YouTube","b",30000,30000,130000);check(e.live(32000).sessionDuration==11000,"session sum excludes paused gap");check(e.live(32000).sessionStartedAt==101000,"stable session origin");e.checkpoint(32000,132000);check(s.duration==2000 && s.start==130000,"checkpoint now refers to current exposure only");e.close(33000,133000);int before=s.checkpoints;e.checkpoint(34000,134000);check(before==s.checkpoints && s.exposures==2,"closed session cannot checkpoint");System.out.println("PASS: checkpoint metadata, no double count, overlay exclusion, active session time and closed lifecycle");}
}
