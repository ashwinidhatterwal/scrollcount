package in.scrollcount.probe;
import java.text.SimpleDateFormat;
import java.util.*;
/** Small, content-free peer payload. Date belongs to the sender's local calendar. */
final class FriendSnapshot {
    static final long MAX=10000000;
    static String day(long at){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(at));}
    static boolean valid(String day,long reels,long shorts){
        if(day==null || !day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || reels<0 || shorts<0 || reels>MAX || shorts>MAX)return false;
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);
        try{return f.format(f.parse(day)).equals(day);}catch(Exception e){return false;}
    }
    static String comparison(long yours,long theirs){
        long gap=Math.abs(yours-theirs);
        if(gap==0)return "A perfect tie!";
        return yours<theirs?"You scrolled "+String.format(Locale.getDefault(),"%,d",gap)+" fewer":"Your friend scrolled "+String.format(Locale.getDefault(),"%,d",gap)+" fewer";
    }
}
