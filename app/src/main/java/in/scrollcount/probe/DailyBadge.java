package in.scrollcount.probe;

import java.util.Calendar;

/** Same local start-day attribution as viewing history, including quick skips. */
final class DailyBadge {
    static long start(long at) {
        Calendar day=Calendar.getInstance();
        day.setTimeInMillis(at);
        day.set(Calendar.HOUR_OF_DAY,0);
        day.set(Calendar.MINUTE,0);
        day.set(Calendar.SECOND,0);
        day.set(Calendar.MILLISECOND,0);
        return day.getTimeInMillis();
    }
    static long end(long start) {
        Calendar day=Calendar.getInstance();
        day.setTimeInMillis(start);
        day.add(Calendar.DAY_OF_MONTH,1);
        return day.getTimeInMillis();
    }
}
