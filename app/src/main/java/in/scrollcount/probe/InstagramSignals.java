package in.scrollcount.probe;
/** IDs observed in the user's Instagram 449.0.0.52.84 sanitized tree. */
public final class InstagramSignals {
    public static final String PACKAGE = "com.instagram.android";
    public static String localId(String id) {
        if(id==null) return "";
        int separator=id.indexOf(":id/");
        return separator<0?id:id.substring(separator+4);
    }
    public static boolean player(String id) {
        String name=localId(id);
        return name.equals("clips_viewer_view_pager") || name.equals("clips_video_container") || name.equals("reels_viewer_view_pager") || name.equals("reel_viewer_view_pager");
    }
    public static boolean pager(String id) {
        String name=localId(id);
        return name.equals("clips_viewer_view_pager") || name.equals("reels_viewer_view_pager") || name.equals("reel_viewer_view_pager");
    }
    public static boolean video(String id) {
        return localId(id).equals("clips_video_container");
    }
    public static boolean playerStructure(boolean pager,boolean video,boolean feed,boolean like,boolean share) {
        return pager && (video || (feed && like && share));
    }
    public static boolean freshFrame(int capturedIndex,int currentIndex,long lastPositionAt,long now) {
        return capturedIndex==currentIndex || (currentIndex>=0 && lastPositionAt>0 && now>=lastPositionAt && now-lastPositionAt<=1500);
    }
    public static boolean author(String id) {
        return localId(id).equals("clips_author_username") || localId(id).equals("reel_author_username");
    }
    public static boolean caption(String id) {
        return localId(id).equals("clips_caption_component") || localId(id).equals("reel_caption");
    }
    public static boolean pagerScroll(String id,String type,int from,int to) {
        return pager(id) && from>=0 && from==to;
    }
    public static boolean pagerClass(String type) {
        return type!=null && (type.endsWith("ViewPager") || type.endsWith("ViewPager2"));
    }
    public static boolean scrollContainer(String type) {
        return pagerClass(type) || (type!=null && type.endsWith("RecyclerView"));
    }
}
