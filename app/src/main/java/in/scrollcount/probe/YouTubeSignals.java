package in.scrollcount.probe;
/** Observed Shorts containers; a container is not evidence of a new video. */
public final class YouTubeSignals {
    public static final String PACKAGE="com.google.android.youtube";
    public static boolean recycler(String id) {
        return InstagramSignals.localId(id).equals("reel_recycler");
    }
    public static boolean page(String id) {
        return InstagramSignals.localId(id).equals("reel_player_page_container");
    }
    public static boolean footer(String id) {
        return InstagramSignals.localId(id).equals("reel_player_footer_container");
    }
    public static boolean overlayRoot(String id) {
        return InstagramSignals.localId(id).equals("player_overlay");
    }
    public static boolean movementSource(String id) {
        String name=InstagramSignals.localId(id);
        return name.equals("reel_player_underlay") || name.equals("reel_recycler") || name.equals("reel_player_page_container");
    }
    public static boolean leftRow(int left,int right,int containerLeft,int containerWidth) {
        return containerWidth>0 && left>=containerLeft && right>left && (right-containerLeft)*100<=containerWidth*90;
    }
    public static boolean confirmedContainer(boolean recycler,boolean page) {
        return recycler && page;
    }
}
