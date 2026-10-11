package in.scrollcount.probe;
final class InstagramDetector implements ShortVideoDetector {
    public String packageName() {
        return InstagramSignals.PACKAGE;
    }
    public String platformName() {
        return "Instagram";
    }
    public boolean usesPosition() {
        return true;
    }
    public boolean expandedHierarchy() {
        return false;
    }
    public int maxNodes() {
        return 180;
    }
    public int maxDepth() {
        return 24;
    }
    public boolean movement(String id,String type,int from,int to) {
        return InstagramSignals.pagerScroll(id,type,from,to);
    }
}
