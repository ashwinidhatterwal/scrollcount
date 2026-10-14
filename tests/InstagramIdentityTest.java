package in.scrollcount.probe;
public final class InstagramIdentityTest {
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    public static void main(String[] args) {
        InstagramIdentity mode=new InstagramIdentity();TransitionProbe counter=new TransitionProbe();
        FastFingerprintTracker fast=new FastFingerprintTracker();
        CountingEngineTest.Sink sink=new CountingEngineTest.Sink();CountingEngine engine=new CountingEngine(sink);
        for(int i=0;i<20;i++) {
            long now=10000+i*3000;String hash="caption"+i;
            int raw=i==0?-1:i,position=mode.scanIndex(raw);
            check(position==-1,"label session remains label based when index metadata appears later");
            if(i==0) {counter.observe(hash,now,true,true);counter.observe(hash,now+700,true,true);fast.seed(hash);}
            else {
                fast.movement(now);
                check(!fast.observe(hash,now+100,true),"new label needs repeat confirmation");
                check(fast.observe(hash,now+700,true),"one stable change accepted after exact pager movement");
                counter.verifiedFingerprint(hash,fast.acceptedAt());
                check(!fast.observe("same-video action edit",now+800,true),"spent movement cannot authorize another count");
            }
            check(counter.isCommitted(hash),"scoped label with exact pager movement confirms");
            mode.baseline(position);
            engine.observe("Instagram",hash,counter.committedAt(),now+700,now+100700);
        }
        engine.close(71000,171000);
        check(counter.accepted==19&&sink.durations.size()==20,"20 label exposures, no identity-mode duplicates");
        check(!fast.observe("action changed",73000,true),"label changes without verified movement cannot count");
        fast.movement(74000);fast.reset();check(!fast.observe("overlay edit",74100,true),"overlay/reset invalidates movement");
        mode.reset();mode.baseline(7);check(mode.scanIndex(8)==8&&!mode.labels(),"next session may use exact positions");
        System.out.println("PASS: unindexed Instagram label counting, stable strategy, later indices, one count per movement, action/overlay guards and reset");
    }
}
