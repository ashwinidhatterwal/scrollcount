package in.scrollcount.probe;
final class InstagramDetector implements ShortVideoDetector {
    private final String pkg;
    InstagramDetector() { this(InstagramSignals.PACKAGE); }
    InstagramDetector(String pkg) { this.pkg=pkg; }
    public String packageName() {
        return pkg;
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
        return 360;
    }
    public int maxDepth() {
        return 48;
    }
    public boolean movement(String id,String type,int from,int to) {
        return InstagramSignals.pagerScroll(id,type,from,to);
    }
}
