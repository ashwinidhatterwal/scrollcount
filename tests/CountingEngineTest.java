package in.scrollcount.probe;
import java.util.ArrayList;
public final class CountingEngineTest {
    static class Sink implements CountingEngine.Sink {
        final ArrayList<Long> durations=new ArrayList<>(); final ArrayList<String> categories=new ArrayList<>(); int starts,ends;
        public void startSession(String id,String platform,long at){starts++;}
        public void exposure(String s,String p,long start,long end,long d,String c){ if(end<start)throw new AssertionError("negative wall span");durations.add(d);categories.add(c); }
        public void endSession(String id,long at){ends++;}
    }
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        Sink s=new Sink(); CountingEngine e=new CountingEngine(s);
        e.observe("Instagram","one",1000,1700,101700);
        check(e.live(3000).duration==2000,"baseline includes stability period and threshold");
        e.observe("Instagram","one",1000,4000,104000);
        check(s.durations.isEmpty(),"same identity cannot finalize twice");
        e.observe("Instagram","two",5000,5700,105700);
        check(s.durations.get(0)==4000 && s.categories.get(0).equals("SEEN"),"switch closes at committed boundary");
        e.pause(6000); e.pause(9000);
        check(e.live(15000).duration==1000,"overlay excludes paused time, repeated pause harmless");
        e.observe("Instagram","two",5000,16000,116000); e.close(17000,117000);
        check(s.durations.get(1)==2000 && s.categories.get(1).equals("SEEN"),"resume same item combines active segments once");
        check(s.starts==1 && s.ends==1 && e.live(18000)==null,"one session finalized");
        e.close(19000,119000); check(s.ends==1,"duplicate exit harmless");
        e.observe("YouTube","a",20000,20700,120700); e.observe("Instagram","b",21000,21700,121700);
        check(s.starts==3 && s.ends==2 && s.categories.get(2).equals("SKIPPED"),"platform switch closes short exposure and starts session");
        e.close(33000,133000); check(s.categories.get(3).equals("LONGER"),"long exposure category");
        check(CountingEngine.classification(1999).equals("SKIPPED") && CountingEngine.classification(2000).equals("SEEN") && CountingEngine.classification(9999).equals("SEEN") && CountingEngine.classification(10000).equals("LONGER"),"classification boundaries");
        System.out.println("PASS: exposure threshold, stable identity, commit boundary, overlay pause, resume, exits, platform switch, long views");
    }
}
