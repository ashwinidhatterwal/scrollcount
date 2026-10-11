package in.scrollcount.probe;
public final class FastFingerprintTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        FastFingerprintTracker f=new FastFingerprintTracker(); TransitionProbe p=new TransitionProbe();
        CountingEngineTest.Sink sink=new CountingEngineTest.Sink();CountingEngine engine=new CountingEngine(sink);
        p.verifiedFingerprint("initial",10000);f.seed("initial");engine.observe("YouTube","initial",10000,10000,110000);
        // Synthetic content identities at the supplied rapid source-event timestamps.
        long[] events={66608173,66609335,66610470,66611549,66613262};
        for(int i=0;i<events.length;i++){
            long t=11000+events[i]-events[0];String id="short"+i;f.movement(t);
            check(!f.observe(id,t+20,true),"first read must confirm");
            check(!f.observe(id,t+50,true),"transient shorter than 60 ms");
            check(f.observe(id,t+90,true),"rapid content change accepted");
            p.verifiedFingerprint(id,f.acceptedAt());engine.observe("YouTube",id,p.committedAt(),t+90,t+100090);
            f.movement(t+120);check(!f.observe(id,t+150,true),"duplicate scroll cannot count same content");
        }
        engine.close(19000,119000);
        check(p.accepted==5 && sink.durations.size()==6,"five transitions plus initial exposure");
        check(sink.categories.stream().filter(c->c.equals("SKIPPED")).count()==5,"rapid exposures retained as skips");
        check(!f.observe("no movement",25000,true),"expired movement cannot count");
        f.movement(26000);check(!f.observe("transient",26020,true),"transient candidate");
        check(!f.observe(null,26090,true),"missing content clears candidate");
        check(!f.observe("transient",26100,true),"requires fresh confirmation after missing content");
        check(!f.observe("transient",26200,false),"overlay cannot confirm");
        f.movement(28000);
        check(!f.observe("delayed",30700,true),"delayed content first read");
        check(f.observe("delayed",30770,true),"delayed content confirmed within finite lease");
        check(!f.active(30771),"accepted content consumes movement");
        check(!f.observe("label edit",30800,true),"label edits without another movement excluded");
        f.movement(32000);
        check(!f.observe("expired",36001,true),"four-second movement lease remains bounded");
        f.reset();check(!f.observe("after reset",27000,true),"reset clears movement lease");
        System.out.println("PASS: rapid YouTube fingerprints, exposure durations, duplicate suppression, missing content, overlay and expired movement");
    }
}
