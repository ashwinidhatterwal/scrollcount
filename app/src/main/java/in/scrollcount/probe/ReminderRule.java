package in.scrollcount.probe;
/** Pure decision rule; Android delivery is isolated in Wellbeing. */
final class ReminderRule {
    static boolean due(int minutes,long duration,boolean paused,boolean already) {
        return minutes>0 && !paused && !already && duration>=minutes*60000L;
    }
}
