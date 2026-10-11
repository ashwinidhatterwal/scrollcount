package in.scrollcount.probe;
/** Platform rules; shared timing/storage do not depend on social-app UI identifiers. */
interface ShortVideoDetector {
    String packageName();
    String platformName();
    boolean usesPosition();
    boolean expandedHierarchy();
    int maxNodes();
    int maxDepth();
    boolean movement(String id,String type,int from,int to);
}
