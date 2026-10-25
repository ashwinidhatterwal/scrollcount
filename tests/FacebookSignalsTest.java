package in.scrollcount.probe;
public final class FacebookSignalsTest {
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    public static void main(String[] args) {
        String recycler="androidx.recyclerview.widget.RecyclerView";
        check(FacebookSignals.position(recycler,3,4)==3,"observed Facebook range");
        check(FacebookSignals.position(recycler,8,9)==8,"later observed Facebook range");
        check(FacebookSignals.position(recycler,-1,-1)==-1,"missing position");
        check(FacebookSignals.position(recycler,4,3)==-1,"inverted range");
        check(FacebookSignals.position(recycler,3,9)==-1,"multi-row list");
        check(FacebookSignals.position("android.widget.ScrollView",3,4)==-1,"wrong source class");
        check(FacebookSignals.railControl(900,900,1040,1040,1080,2400),"right action control");
        check(!FacebookSignals.railControl(100,900,240,1040,1080,2400),"left control");
        check(!FacebookSignals.railControl(900,100,1040,240,1080,2400),"header control");
        check(!FacebookSignals.railControl(900,2200,1040,2500,1080,2400),"offscreen control");
        check(FacebookSignals.verticalRail(980,1000,980,1350,1080,2400),"vertical action rail");
        check(!FacebookSignals.verticalRail(780,1350,980,1350,1080,2400),"ordinary horizontal feed actions");
        check(!FacebookSignals.verticalRail(980,1350,980,1350,1080,2400),"one combined action label");
        check(!FacebookSignals.verticalRail(980,1350,980,1000,1080,2400),"wrong action order");
        TransitionProbe probe=new TransitionProbe();
        check(probe.observe("facebook:3",0,true,true).contains("stabilizing"),"initial settling");
        probe.observe("facebook:3",700,true,true);
        check(probe.accepted==0 && probe.hasBaseline(),"first reel establishes baseline");
        probe.observe("facebook:3",900,true,true);
        probe.observe("facebook:4",1000,true,true);
        probe.observe("facebook:4",1700,true,true);
        check(probe.accepted==1,"stable new range counted once");
        probe.observe("facebook:4",1800,true,true);
        probe.observe("facebook:4",2000,true,true);
        check(probe.accepted==1,"duplicate range notifications ignored");
        probe.observe("facebook:5",2100,false,true);
        probe.observe("facebook:5",3000,false,true);
        check(probe.accepted==1,"unconfirmed layout cannot count");
        probe.observe("facebook:6",3100,true,false);
        probe.observe("facebook:6",4000,true,false);
        check(probe.accepted==1,"stale position cannot authorize a transition");
        System.out.println("PASS: Facebook redacted-ID ranges, action rail guards, baseline, deduplication and unconfirmed/stale changes");
    }
}
