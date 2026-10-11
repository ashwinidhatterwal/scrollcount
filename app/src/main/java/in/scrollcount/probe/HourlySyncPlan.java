package in.scrollcount.probe;
/** Targets the next UTC hour; Android can delay this network job. */
final class HourlySyncPlan {
    static final long HOUR=3600000L,WINDOW=90000L;
    static long next(long now){return now-Math.floorMod(now,HOUR)+HOUR;}
    static long delay(long now){return next(now)-now;}
}
