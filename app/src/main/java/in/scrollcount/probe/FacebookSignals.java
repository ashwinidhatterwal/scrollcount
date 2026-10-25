package in.scrollcount.probe;
/** Facebook may redact every view ID. Confirm geometry before trusting list positions. */
final class FacebookSignals {
    static boolean recycler(String type) {
        return type!=null && (type.endsWith(".RecyclerView") || type.equals("RecyclerView"));
    }
    static int position(String type,int from,int to) {
        return recycler(type) && from>=0 && to>=from && (long)to-from<=1 ? from : -1;
    }
    static boolean railControl(int left,int top,int right,int bottom,int width,int height) {
        return width>0 && height>0 && left>=width*2L/3 && right<=width
                && top>=height/5 && bottom<=height && right>left && bottom>top
                && right-left<=width/3 && bottom-top<=height/5;
    }
    static boolean verticalRail(int likeX,int likeY,int shareX,int shareY,int width,int height) {
        return width>0 && height>0 && Math.abs((long)likeX-shareX)<=width/8
                && shareY-likeY>=height/30 && shareY-likeY<=height/2;
    }
}
