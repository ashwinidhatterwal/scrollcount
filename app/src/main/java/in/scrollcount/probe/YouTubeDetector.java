package in.scrollcount.probe;
final class YouTubeDetector implements ShortVideoDetector {
    public String packageName() {
        return YouTubeSignals.PACKAGE;
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
