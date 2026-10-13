package in.scrollcount.probe;
import java.util.*;
public final class MultiAppTest {
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    public static void main(String[] args) {
        check(Detectors.forPackage("com.instagram.lite").platformName().equals("Instagram"),"Lite remains under Instagram");
        check(Detectors.forPackage("com.facebook.katana").platformName().equals("Facebook"),"Facebook");
        check(Detectors.forPackage("com.facebook.lite").platformName().equals("Facebook"),"Facebook Lite");
        check(Detectors.forPackage("com.snapchat.android").platformName().equals("Snapchat"),"Spotlight");
        check(Detectors.forPackage("unknown.instagram.clone")==null,"name alone never authorizes content access");
        Map<String,Object> saved=new HashMap<>();
        saved.put("renamed.app",YouTubeSignals.PACKAGE);
        saved.put("renamed.ig",InstagramSignals.PACKAGE);
        saved.put("bad.app","unknown");
        Detectors.load(saved);
        check(Detectors.youtube("renamed.app"),"explicit YouTube clone profile");
        check(Detectors.instagram("renamed.ig"),"explicit Instagram clone profile");
        check(Detectors.forPackage("bad.app")==null,"invalid profile ignored");
        check(Detectors.forPackage("renamed.ig").packageName().equals("renamed.ig"),"exact package remains selected");
        check(InstagramSignals.pagerScroll("renamed.ig:id/reels_viewer_view_pager","androidx.viewpager2.widget.ViewPager2",8,8),"older/renamed pager");
        check(!InstagramSignals.pagerScroll("renamed.ig:id/reels_viewer_view_pager","androidx.viewpager2.widget.ViewPager2",8,9),"ambiguous indices rejected");
        check(SocialSignals.player("com.facebook.katana:id/reels_viewer_pager","Facebook"),"Reels player marker");
        check(SocialSignals.player("com.snapchat.android:id/spotlight_video_player","Snapchat"),"Spotlight player marker");
        check(!SocialSignals.player("feed_recycler","Facebook"),"ordinary feed not a player");
        check(!SocialSignals.player("story_video_player","Snapchat"),"Stories not Spotlight");
        check(!SocialSignals.movement("comments_recycler","RecyclerView",1,2,"Facebook"),"comment scrolling not movement");
        check(!SocialSignals.content("like_count"),"changing action counts are not identity");
        Detectors.load(Collections.emptyMap());
        check(Detectors.forPackage("renamed.app")==null,"removal revokes content access");
        System.out.println("PASS: official/Lite profiles, explicit renamed apps, revocation, old ViewPager2 and ordinary-feed/story/comment guards");
    }
}
