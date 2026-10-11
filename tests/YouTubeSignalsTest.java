package in.scrollcount.probe;
public final class YouTubeSignalsTest {
    static void check(boolean b,String m) { if(!b) throw new AssertionError(m); }
    public static void main(String[] args) {
        String p="com.google.android.youtube:id/";
        check(YouTubeSignals.recycler(p+"reel_recycler"),"observed Shorts recycler");
        check(YouTubeSignals.page(p+"reel_player_page_container"),"observed Shorts page");
        check(YouTubeSignals.confirmedContainer(true,true),"both markers confirm a container");
        check(!YouTubeSignals.confirmedContainer(true,false),"recycler alone not sufficient");
        check(!YouTubeSignals.recycler(p+"results"),"ordinary results list excluded");
        check(!YouTubeSignals.page(p+"reel_time_bar"),"playback time bar not page identity");
        check(!YouTubeSignals.recycler(null),"missing ID excluded");
        check(YouTubeSignals.footer(p+"reel_player_footer_container"),"observed footer scope");
        check(YouTubeSignals.overlayRoot(p+"player_overlay"),"observed content container");
        check(YouTubeSignals.leftRow(0,904,0,1080),"observed title row selected");
        check(!YouTubeSignals.leftRow(904,1080,0,1080),"action rail excluded");
        check(YouTubeSignals.movementSource(p+"reel_player_underlay"),"observed underlay movement source");
        check(!YouTubeSignals.movementSource(p+"browse_fragment_layout_coordinator_layout"),"ordinary scroll not movement evidence");
        check(!YouTubeSignals.movementSource(p+"reel_time_bar"),"playback progress not movement evidence");
        System.out.println("PASS: observed YouTube container, unrelated results, time bar and missing markers");
    }
}
