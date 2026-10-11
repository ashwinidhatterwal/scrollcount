package in.scrollcount.probe;
/** Regression for the supplied 47..78 trace, including repeated indexed events and rapid runs. */
public final class FastPositionTest {
    public static void main(String[] args) {
        TransitionProbe p=new TransitionProbe();
        CountingEngineTest.Sink sink=new CountingEngineTest.Sink(); CountingEngine engine=new CountingEngine(sink);
        long now=10000;
        p.verifiedPosition(47,now,false);
        if(p.isCommitted("pager:47")) throw new AssertionError("unconfirmed feed cannot establish baseline");
        for(int index=47;index<=78;index++) {
            now+=index>=57 && index<=66?450:3000;
            p.verifiedPosition(index,now,true);
            engine.observe("Instagram","pager:"+index,p.committedAt(),now,now+100000);
            p.verifiedPosition(index,now+120,true);
            engine.observe("Instagram","pager:"+index,p.committedAt(),now+120,now+100120);
        }
        engine.close(now+3000,now+103000);
        if(p.accepted!=31) throw new AssertionError("47..78 must have 31 transitions, duplicates excluded");
        if(sink.durations.size()!=32) throw new AssertionError("initial exposure plus 31 transitions");
        long skips=sink.categories.stream().filter(c->c.equals("SKIPPED")).count();
        if(skips!=10) throw new AssertionError("all 10 short exposures must survive in quick skips");
        p.verifiedPosition(77,now+4000,true);
        if(p.accepted!=32)throw new AssertionError("backward exposure counted once");
        p.verifiedPosition(-1,now+5000,true);
        if(p.accepted!=32)throw new AssertionError("unknown index cannot invent transitions");
        System.out.println("PASS: 47→78 indexed trace, duplicate suppression, all rapid exposures classified, backward and unknown positions");
    }
}
