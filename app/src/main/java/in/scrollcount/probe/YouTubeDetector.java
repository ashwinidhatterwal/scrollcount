package in.scrollcount.probe;
final class YouTubeDetector implements ShortVideoDetector {
    private final String pkg;
    YouTubeDetector() { this(YouTubeSignals.PACKAGE); }
    YouTubeDetector(String pkg) { this.pkg=pkg; }
    public String packageName() {
        return pkg;
    }
    public String platformName() {
        return "YouTube";
    }
    public boolean usesPosition() {
        return false;
    }
    public boolean expandedHierarchy() {
        return true;
    }
    public int maxNodes() {
        return 360;
    }
    public int maxDepth() {
        return 52;
    }
    public boolean movement(String id,String type,int from,int to) {
        return YouTubeSignals.movementSource(id);
    }
}
