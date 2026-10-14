package in.scrollcount.probe;
/** Experimental fingerprint-change counter, not a production video counter. */
public final class TransitionProbe {
    public static final long STABLE_MS = 650;
    private String current, candidate;
    private long since, committedAt;
    public int accepted;
    public void reset() {
        current = candidate = null;
        accepted = 0;
    }
    public void forgetFingerprints() {
        current = candidate = null;
    }
    public boolean hasBaseline() {
        return current!=null;
    }
    public boolean isCommitted(String identity) {
        return identity!=null && identity.equals(current);
    }
    public long committedAt() {
        return committedAt;
    }
    public boolean aliasIdentity(String previous,String next) {
        if(previous==null || next==null || !previous.equals(current))return false;
        current=next;
        candidate=null;
        return true;
    }
    public void suspend() {
        candidate = null;
    }
    /** Only for an exact, validated player position; no inference from generic scroll events. */
    public String verifiedPosition(int position,long at,boolean confirmedFeed) {
        if(!confirmedFeed || position<0) return "Player position unavailable";
        String identity="pager:"+position;
        if(identity.equals(current)) return "Stable player position";
        boolean first=current==null;
        current=identity;
        candidate=null;
        committedAt=at;
        if(!first) accepted++;
        return first?"Baseline acquired (not counted)":"Verified player transition accepted";
    }
    public String verifiedFingerprint(String fingerprint,long at) {
        if(fingerprint==null || fingerprint.isEmpty()) return "Fingerprint unavailable";
        if(fingerprint.equals(current)) return "Stable fingerprint";
        boolean first=current==null;
        current=fingerprint;
        candidate=null;
        committedAt=at;
        if(!first)accepted++;
        return first?"Baseline acquired (not counted)":"Player fingerprint transition accepted";
    }
    public String observe(String fingerprint, long now, boolean eligible) {
        return observe(fingerprint,now,eligible,true);
    }
    public String observe(String fingerprint, long now, boolean eligible, boolean transitionAllowed) {
        if (!eligible || fingerprint == null) {
            suspend();
            return "Suspended / insufficient evidence";
        }
        if (fingerprint.equals(current)) {
            candidate = null;
            return "Stable baseline";
        }
        if(current != null && !transitionAllowed) {
            suspend();
            return "Content changed without verified pager movement — no count";
        }
        if (!fingerprint.equals(candidate)) {
            candidate = fingerprint;
            since = now;
            return "Candidate stabilizing";
        }
        if (now - since < STABLE_MS) return "Candidate stabilizing";
        boolean first = current == null;
        current = candidate;
        committedAt=since;
        candidate = null;
        if (!first) accepted++;
        return first ? "Baseline acquired (not counted)" : "Fingerprint transition accepted";
    }
}
