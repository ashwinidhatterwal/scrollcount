package in.scrollcount.probe;

import java.util.*;

public final class DailyBadgeTest {
    static final class Sink extends CountingEngineTest.Sink {
        final Map<String,long[]> items=new HashMap<>();
        public void detected(String session,String platform,long start) {
            items.putIfAbsent(session+":"+start,new long[]{platform.equals("Instagram")?0:1,start});
        }
        long total(String platform,long day) {
            int index=platform.equals("Instagram")?0:1;
            return items.values().stream().filter(v->v[0]==index && v[1]>=day && v[1]<DailyBadge.end(day)).count();
        }
    }
    static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            long day=DailyBadge.start(1700000000000L),end=DailyBadge.end(day);
            Sink sink=new Sink();CountingEngine engine=new CountingEngine(sink);
            engine.observe("YouTube","first",1000,1000,day+1000);
            check(sink.total("YouTube",day)==1,"first confirmed item persisted before timing finishes");
            engine.close(1000,day+1000);
            check(sink.durations.isEmpty() && sink.total("YouTube",day)==1,"zero-time interruption cannot erase detected count");
            engine.observe("YouTube","second",2000,2000,day+2000);
            engine.observe("YouTube","second",2000,2100,day+2100);
            check(sink.total("YouTube",day)==2,"new session continues daily count; duplicate ignored");
            engine.pause(2200); // volume/system overlay
            check(sink.total("YouTube",day)==2,"volume overlay pause cannot reset count");
            engine.observe("YouTube","second",2000,3000,day+3000);
            check(sink.total("YouTube",day)==2,"volume overlay resume cannot add a duplicate");
            engine.close(3500,day+3500); // ordinary feed
            check(sink.total("YouTube",day)==2,"feed switch retains accumulated count");
            engine.observe("YouTube","third",4000,4000,day+4000);
            check(sink.total("YouTube",day)==3,"return from feed continues count");
            engine.close(4500,day+4500); // minimized app
            engine.observe("YouTube","fourth",5000,5000,day+5000);
            check(sink.total("YouTube",day)==4,"minimize/return continues count");
            engine.observe("Instagram","reel",6000,6000,day+6000);
            check(sink.total("Instagram",day)==1 && sink.total("YouTube",day)==4,"platform totals independent");
            check(sink.total("YouTube",end)==0,"midnight resets only the new-day total");
            engine.observe("YouTube","new day",7000,7000,end+1000);
            check(sink.total("YouTube",end)==1 && sink.total("YouTube",day)==4,"previous day retained and new day starts at one");
            engine.close(8000,end+2000);
            CountingEngine restarted=new CountingEngine(sink);
            check(sink.total("YouTube",end)==1,"process/session replacement cannot lose persisted total");
            restarted.observe("YouTube","after restart",9000,9000,end+3000);
            check(sink.total("YouTube",end)==2,"new process continues persisted count");
            sink.items.clear();check(sink.total("YouTube",end)==0,"explicit history deletion clears totals");
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Calendar dst=Calendar.getInstance();dst.clear();dst.set(2024,Calendar.MARCH,10);
            long spring=DailyBadge.start(dst.getTimeInMillis());
            check(DailyBadge.end(spring)-spring==23*60*60*1000L,"calendar day handles DST");
        } finally { TimeZone.setDefault(original); }
        System.out.println("PASS: persisted daily detections survive zero-time interruption, volume, feed switching, minimize, session/process changes, duplicates; midnight/DST/platform/reset guards");
    }
}
