package in.scrollcount.probe;
public final class FeedEvidenceTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        long confirmed=FeedEvidence.update(0,10000,true,true,false,true,true);
        confirmed=FeedEvidence.update(confirmed,10600,false,true,false,true,true);
        check(FeedEvidence.valid(confirmed,10830),"partial swipe cannot discard recent feed");
        check(!FeedEvidence.valid(confirmed,15001),"partial scans cannot extend expired confirmation");
        check(!FeedEvidence.valid(confirmed,9999),"clock reversal excluded");
        for(int reason=0;reason<4;reason++){
            long cleared=FeedEvidence.update(confirmed,11000,false,reason!=0,reason==1,reason!=2,reason!=3);
            check(cleared==0,"exit/overlay/truncated/small player invalidates immediately");
        }
        check(FeedEvidence.fullPlayer(1080,2400,1080,2400),"full screen");
        check(!FeedEvidence.fullPlayer(396,704,1080,2400),"supplied YouTube small root excluded");
        check(!FeedEvidence.fullPlayer(1080,1100,1080,2400),"split screen excluded");
        // Reproduce positions 19..47, introducing the partial scan before previously missed 21.
        TransitionProbe probe=new TransitionProbe();long at=20000;confirmed=at;
        probe.verifiedPosition(19,at,true);
        for(int i=20;i<=47;i++){
            at+=i==21?830:1000;
            confirmed=FeedEvidence.update(confirmed,at-100,i!=21,true,false,true,true);
            probe.verifiedPosition(i,at,FeedEvidence.valid(confirmed,at));
            probe.verifiedPosition(i,at+110,FeedEvidence.valid(confirmed,at+110));
        }
        check(probe.accepted==28,"all 28 indexed transitions survive partial layout and duplicates");
        confirmed=FeedEvidence.update(confirmed,at+200,false,true,true,true,true);
        probe.verifiedPosition(48,at+300,FeedEvidence.valid(confirmed,at+300));
        check(probe.accepted==28,"overlay excludes player event");
        probe.forgetFingerprints();probe.verifiedPosition(0,at+1000,true);
        check(probe.accepted==28,"reentry starts baseline without inventing transition");
        System.out.println("PASS: 19→47 partial-layout regression, duplicate/overlay/exit/truncation/expiry guards, reentry, PiP and split-screen geometry");
    }
}
