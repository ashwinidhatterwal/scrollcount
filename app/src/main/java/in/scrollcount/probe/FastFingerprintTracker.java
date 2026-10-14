package in.scrollcount.probe;
/** Short confirmation of a changed scoped label after a verified player movement. */
public final class FastFingerprintTracker {
    public static final long CONFIRM_MS=60, MOVEMENT_TTL_MS=4000;
    private String current,candidate;
    private long movedAt=-1,since,acceptedAt;
    public void reset() {
        current=candidate=null;
        movedAt=-1;
    }
    public void seed(String fingerprint) {
        current=fingerprint;
        candidate=null;
    }
    public void pause() {
        candidate=null;
        movedAt=-1;
    }
    public void movement(long at) {
        movedAt=at;
    }
    public boolean active(long now) {
        return movedAt>=0 && now>=movedAt && now-movedAt<=MOVEMENT_TTL_MS;
    }
    public long acceptedAt() {
        return acceptedAt;
    }
    public boolean observe(String fingerprint,long now,boolean eligible) {
        if(!eligible || fingerprint==null || fingerprint.isEmpty() || !active(now)) {
            candidate=null;
            return false;
        }
        if(fingerprint.equals(current)) {
            candidate=null;
            return false;
        }
        if(!fingerprint.equals(candidate)) {
            candidate=fingerprint;
            since=now;
            return false;
        }
        if(now-since<CONFIRM_MS)return false;
        current=fingerprint;
        candidate=null;
        acceptedAt=since;
        movedAt=-1; // One stable content change consumes this movement; further label edits cannot count.
        return true;
    }
}
