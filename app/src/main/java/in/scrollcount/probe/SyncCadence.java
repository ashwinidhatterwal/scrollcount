package in.scrollcount.probe;
/** Upload soon after first change, then batch background activity for two minutes. */
final class SyncCadence {
    static long delay(long now,long last){return Math.max(2000L,120000L-Math.max(0L,now-last));}
}
