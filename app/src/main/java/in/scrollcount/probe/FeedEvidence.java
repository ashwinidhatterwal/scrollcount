package in.scrollcount.probe;
/** Preserve recent confirmation through partial swipe layouts, never through exits/overlays. */
public final class FeedEvidence {
    public static final long TTL_MS=5000;
    public static long update(long previous,long now,boolean confirmed,boolean feed,boolean blocked,boolean complete,boolean fullPlayer) {
        if(blocked || !feed || !complete || !fullPlayer)return 0;
        return confirmed?now:previous;
    }
    public static boolean valid(long at,long now) {
        return at>0 && now>=at && now-at<=TTL_MS;
    }
    public static boolean fullPlayer(int width,int height,int screenWidth,int screenHeight) {
        return screenWidth>0 && screenHeight>0 && (long)width*100>=screenWidth*85L && (long)height*100>=screenHeight*70L;
    }
}
