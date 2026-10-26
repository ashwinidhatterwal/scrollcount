package in.scrollcount.probe;
import java.util.Locale;
/** Explicit Spotlight evidence; generic Stories, Discover and chat scrolling are insufficient. */
final class SnapchatSignals {
    static String label(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    static boolean spotlight(String value,boolean selected,boolean clickable,int top,int height){
        return label(value).equals("spotlight") && (selected || (!clickable && height>0 && top>=0 && top<height/5));
    }
    static boolean favorite(String value){String s=label(value);return s.contains("favorite")||s.contains("favourite")||s.equals("like")||s.equals("unlike")||s.contains("पसंद");}
    static boolean movement(String type){return type!=null && (type.endsWith("RecyclerView")||type.endsWith("ViewPager")||type.endsWith("ViewPager2")||type.equals("android.widget.FrameLayout"));}
    static boolean footer(int left,int top,int right,int bottom,int width,int height){
        return width>0&&height>0&&left>=0&&right>left&&right<=width*87L/100&&top>=height/2&&bottom>top&&bottom<=height*92L/100;
    }
    static boolean actionRow(int left,int top,int right,int bottom,int width,int height){
        return width>0&&height>0&&left>=width*4L/5&&right<=width&&right>left&&right-left<=width/5
                &&top>=height/3&&bottom<=height*92L/100&&bottom-top>=height/25&&bottom-top<=height/7;
    }
    static boolean rail(int left,int top,int right,int bottom,int width,int height){
        return width>0&&height>0&&left>=width*4L/5&&right<=width&&right>left&&right-left<=width/5
                &&top>=height/3&&bottom<=height*92L/100&&bottom-top>=height/3;
    }
    static boolean author(int left,int top,int right,int bottom,int width,int height){
        return footer(left,top,right,bottom,width,height)&&left<width/5&&right-left<=width*3L/5&&top>=height*65L/100;
    }
    static boolean content(String raw){
        String s=label(raw);
        return !s.isEmpty()&&s.length()<=2000&&!s.matches("[\\d\\s.,:kmb%+−/-]+")
                && !s.matches("(?:subscribe|subscribed|follow|following|spotlight|stories|chat|map|camera|share|send to|send|more|sounds?|comments?|repl(?:y|ies)|report|like|unlike|favorites?|favourites?)(?:[\\s,:].*)?")
                && !s.matches(".*\\b(?:views?|likes?|comments?|seconds?|minutes?|hours?)\\b.*")
                && !s.matches("\\d+\\s*[smhd]");
    }
}
