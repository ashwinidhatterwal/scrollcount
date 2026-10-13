package in.scrollcount.probe;
final class SocialDetector implements ShortVideoDetector {
    private final String pkg,platform;
    SocialDetector(String pkg,String platform) { this.pkg=pkg; this.platform=platform; }
    public String packageName() { return pkg; }
    public String platformName() { return platform; }
    public boolean usesPosition() { return false; }
    public boolean expandedHierarchy() { return true; }
    public int maxNodes() { return 480; }
    public int maxDepth() { return 56; }
    public boolean movement(String id,String type,int from,int to) { return SocialSignals.movement(id,type,from,to,platform); }
}
