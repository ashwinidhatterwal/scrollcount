package in.scrollcount.probe;
/** Lock identity strategy after the first confirmed item, avoiding duplicate counts when indices appear later. */
final class InstagramIdentity {
    private boolean labels;
    private boolean chosen;
    int scanIndex(int eventIndex) { return labels?-1:eventIndex; }
    void baseline(int usedIndex) { if(!chosen){chosen=true;labels=usedIndex<0;} }
    boolean labels() { return labels; }
    void reset() { labels=false;chosen=false; }
}
