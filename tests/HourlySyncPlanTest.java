package in.scrollcount.probe;
import java.util.*;
public final class HourlySyncPlanTest {
    public static void main(String[] args){
        long start=1791462600000L;
        for(String zone:new String[]{"UTC","Asia/Kolkata","America/New_York","Pacific/Chatham"}){
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            long boundary=HourlySyncPlan.next(start);
            if(boundary%HourlySyncPlan.HOUR!=0||boundary<=start)throw new AssertionError("Peers must target the same UTC boundary");
            if(HourlySyncPlan.next(boundary)!=boundary+HourlySyncPlan.HOUR)throw new AssertionError("No zero-delay rescheduling at an exact boundary");
            long delayed=boundary+3*HourlySyncPlan.HOUR+30*60000L;
            if(HourlySyncPlan.delay(delayed)!=30*60000L)throw new AssertionError("Delayed jobs must skip missed hours, not replay them");
        }
        System.out.println("PASS: common hourly targets across time zones, exact-boundary scheduling, and delayed-job catch-up without bursts");
    }
}
