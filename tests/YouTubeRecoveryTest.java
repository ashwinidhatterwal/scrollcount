package in.scrollcount.probe;

/** Synthetic exposures: labels, movement verification, transition count and stored categories together. */
public final class YouTubeRecoveryTest {
    static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TransitionProbe transitions=new TransitionProbe();
        FastFingerprintTracker tracker=new FastFingerprintTracker();
        CountingEngineTest.Sink sink=new CountingEngineTest.Sink();
        CountingEngine engine=new CountingEngine(sink);
        for(int session=0;session<2;session++) {
            long start=10000+session*200000, interval=session==0?5000:1000;
            transitions.forgetFingerprints();tracker.reset();
            String baseline="session"+session+" short0";
            transitions.verifiedFingerprint(baseline,start);tracker.seed(baseline);
            engine.observe("YouTube",baseline,start,start,start+100000);
            for(int i=1;i<20;i++) {
                long movement=start+i*interval;
                tracker.movement(movement);
                long confirmed=FeedEvidence.update(0,movement,true,true,false,true,true);
                check(FeedEvidence.valid(confirmed,movement+90),"confirmed structure can retry loading labels");
                check(!tracker.observe(null,movement+20,true),"loading labels never count");
                String label="session"+session+" short"+i;
                long labelAt=movement+(session==0?2100:90);
                check(!tracker.observe(label,labelAt,true),"candidate needs second read");
                check(tracker.observe(label,labelAt+70,true),"slow/quick label transition confirmed");
                transitions.verifiedFingerprint(label,tracker.acceptedAt());
                engine.observe("YouTube",label,transitions.committedAt(),labelAt+70,labelAt+100070);
                check(!tracker.observe(label,labelAt+90,true),"same label not counted twice");
                check(!tracker.observe("changed action",labelAt+100,true),"no second count from used movement");
            }
            long end=start+20*interval+(session==0?2100:90);
            engine.close(end,end+100000);
        }
        check(transitions.accepted==38,"two baselines plus 38 transitions for 40 exposures");
        check(sink.durations.size()==40,"40 finalized exposures");
        check(sink.categories.stream().filter("SKIPPED"::equals).count()==20,"20 quick skips stored");
        check(sink.categories.stream().filter("SEEN"::equals).count()==20,"20 slow views stored");
        tracker.movement(500000);
        check(!tracker.observe("overlay",500100,false),"overlay cannot start candidate");
        check(!tracker.observe("overlay",500200,false),"overlay cannot confirm candidate");
        check(!tracker.observe("normal feed",504001,true),"expired movement excluded");
        check(FeedEvidence.update(500000,500100,false,false,false,true,true)==0,"normal feed clears lease");
        check(FeedEvidence.update(500000,500100,false,true,true,true,true)==0,"overlay clears lease");
        System.out.println("PASS: 20 slow + 20 quick synthetic exposures, 38 transitions, duplicate/loading/action/overlay/exit guards and finalized categories");
    }
}
