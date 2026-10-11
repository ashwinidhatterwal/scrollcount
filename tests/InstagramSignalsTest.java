package in.scrollcount.probe;
public final class InstagramSignalsTest {
    static void check(boolean b,String m) { if(!b) throw new AssertionError(m); }
    public static void main(String[] args) {
        String prefix="com.instagram.android:id/";
        check(InstagramSignals.player(prefix+"clips_viewer_view_pager"),"observed player must be recognized");
        check(InstagramSignals.player(prefix+"clips_video_container"),"observed video container");
        check(!InstagramSignals.player(prefix+"clips_tab"),"navigation alone is not a feed");
        check(!InstagramSignals.player(prefix+"swipeable_tab_view_pager"),"main navigation pager is not video pager");
        check(InstagramSignals.caption(prefix+"clips_caption_component"),"caption subtree marker");
        check(InstagramSignals.author(prefix+"clips_author_username"),"author marker");
        check(!InstagramSignals.author(prefix+"like_count"),"mutable counts excluded");
        check(InstagramSignals.pagerScroll(prefix+"clips_viewer_view_pager","androidx.viewpager.widget.ViewPager",28,28),"observed settled indices with verified source");
        check(!InstagramSignals.pagerScroll(prefix+"clips_caption_component","android.widget.ScrollView",28,28),"caption scroll cannot authorize transition");
        check(!InstagramSignals.pagerScroll(prefix+"swipeable_tab_view_pager","androidx.viewpager.widget.ViewPager",2,2),"tab switch cannot authorize transition");
        check(!InstagramSignals.pagerScroll(null,"androidx.viewpager.widget.ViewPager",28,28),"unknown source must remain diagnostic");
        check(!InstagramSignals.pagerScroll(prefix+"clips_viewer_view_pager","androidx.viewpager.widget.ViewPager",-1,-1),"unknown index rejected");
        System.out.println("PASS: observed Instagram IDs, unrelated navigation, caption scroll, unknown source/index");
    }
}
