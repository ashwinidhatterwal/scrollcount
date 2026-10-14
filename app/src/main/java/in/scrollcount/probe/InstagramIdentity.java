package in.scrollcount.probe;
/** Prefer verified positions, recover through content, and avoid duplicate counts from late indices. */
final class InstagramIdentity {
    private boolean labels;
    private boolean chosen;
    int scanIndex(int eventIndex) { return labels?-1:eventIndex; }
    void baseline(int usedIndex) { if(!chosen){chosen=true;labels=usedIndex<0;} }
    // Stay with content after recovery so a late index cannot count the same reel again.
    void fallback() { chosen=true;labels=true; }
    void position() { chosen=true;labels=false; }
    boolean labels() { return labels; }
    void reset() { labels=false;chosen=false; }
}
