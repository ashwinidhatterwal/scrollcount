package in.scrollcount.probe;
public final class SnapchatSignalsTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        check(SnapchatSignals.spotlight("Spotlight",true,true,2200,2400));
        check(SnapchatSignals.spotlight("Spotlight",false,false,100,2400));
        check(!SnapchatSignals.spotlight("Spotlight",false,true,2200,2400));
        check(!SnapchatSignals.spotlight("Spotlight",false,false,1800,2400));
        check(!SnapchatSignals.spotlight("Stories",true,true,2200,2400));
        check(SnapchatSignals.movement("android.widget.FrameLayout"));
        check(SnapchatSignals.movement("androidx.recyclerview.widget.RecyclerView"));
        check(!SnapchatSignals.movement("android.widget.Button"));
        check(SnapchatSignals.footer(0,1500,650,1700,1080,2400));
        check(!SnapchatSignals.footer(900,1500,1040,1700,1080,2400));
        check(SnapchatSignals.content("Creator Alpha"));check(SnapchatSignals.content("An actual caption"));
        for(String label:new String[]{"123K","10:00","Subscribe","Send To","14 comments","3h","Spotlight","More"})check(!SnapchatSignals.content(label));
        System.out.println("PASS: Snapchat explicit Spotlight proof, unindexed source classes, scoped footer and action/counter exclusions");
    }
}
