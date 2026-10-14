package in.scrollcount.probe;
/** Reproduce a changing pager while full player reads are still in flight. */
public final class InstagramAsyncRegressionTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        TransitionProbe counter=new TransitionProbe();
        CountingEngineTest.Sink sink=new CountingEngineTest.Sink();CountingEngine engine=new CountingEngine(sink);
        int rejectedByOldGate=0;
        for(int i=0;i<20;i++) {
            int captured=i-1,current=i;long event=10000+i*1800,now=event+400;
            if(captured!=current)rejectedByOldGate++;
            check(InstagramSignals.freshFrame(captured,current,event,now),"fresh exact event belongs to confirmed player even when capture index advanced");
            check(InstagramSignals.playerStructure(true,true,false,true,false,false),"two exact player markers do not require labeled icons");
            check(FeedEvidence.identityAvailable(0,true,current),"position identity needs no captions");
            counter.verifiedPosition(current,event,true);
            engine.observe("Instagram","pager:"+current,counter.committedAt(),now,now+100000);
        }
        engine.close(45000,145000);
        check(rejectedByOldGate==20,"old captured-index gate would discard every one of these frames");
        check(counter.accepted==19&&sink.durations.size()==20,"twenty confirmed exposures retained");
        check(!InstagramSignals.freshFrame(2,3,1000,4000),"old mismatched positions still rejected");
        check(!InstagramSignals.playerStructure(false,true,false,true,false,false),"video preview alone does not prove a viewer");
        check(!InstagramSignals.playerStructure(true,false,false,false,false,false),"incomplete player markers do not prove a viewer");
        check(InstagramSignals.pagerScroll("com.instagram.android:id/clips_viewer_view_pager","androidx.recyclerview.widget.RecyclerView",5,5),"known pager ID is independent of wrapper class");
        check(!InstagramSignals.pagerScroll("com.instagram.android:id/swipeable_tab_view_pager","androidx.recyclerview.widget.RecyclerView",5,5),"navigation rejected");
        check(!InstagramSignals.pagerScroll("com.instagram.android:id/clips_viewer_view_pager","android.view.ViewGroup",5,6),"ambiguous indices rejected");
        System.out.println("PASS: 20 moving-pager frames formerly discarded, caption/icon independence, wrapper changes and stale/preview/navigation/ambiguous negatives");
    }
}
