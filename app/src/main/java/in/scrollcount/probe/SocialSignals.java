package in.scrollcount.probe;
import java.util.Locale;
/** Conservative rules: a short-video player marker is required, never a generic feed scroll. */
final class SocialSignals {
    static String id(String value) { return InstagramSignals.localId(value).toLowerCase(Locale.ROOT); }
    static boolean player(String value,String platform) {
        String n=id(value);
        if(platform.equals("Snapchat")) return n.contains("spotlight") && (n.contains("player") || n.contains("pager") || n.contains("viewer") || n.contains("recycler") || n.contains("video"));
        return (n.contains("reel") || n.contains("clips")) && (n.contains("player") || n.contains("pager") || n.contains("viewer") || n.contains("recycler") || n.contains("video_container"));
    }
    static boolean content(String value) {
        String n=id(value);
        return n.contains("caption") || n.contains("description") || n.contains("author") || n.contains("username") || n.contains("video_title") || n.contains("creator_name");
    }
    static boolean movement(String value,String type,int from,int to,String platform) {
        String n=id(value);
        return player(value,platform) && (n.contains("pager") || n.contains("recycler") || type.endsWith("ViewPager") || type.endsWith("ViewPager2"));
    }
}
