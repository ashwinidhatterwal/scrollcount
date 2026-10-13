package in.scrollcount.probe;
import java.util.concurrent.ConcurrentHashMap;
final class Detectors {
    private static final java.util.Map<String,ShortVideoDetector> CUSTOM=new ConcurrentHashMap<>();
    private static final ShortVideoDetector[] ALL={
        new InstagramDetector(),new YouTubeDetector(),
        new SocialDetector("com.instagram.lite","Instagram"),
        new SocialDetector("com.facebook.katana","Facebook"),
        new SocialDetector("com.facebook.lite","Facebook"),
        new SocialDetector("com.snapchat.android","Snapchat")
    };
    static ShortVideoDetector forPackage(String name) {
        if(name==null)return null;
        for(ShortVideoDetector detector:ALL)if(detector.packageName().equals(name))return detector;
        return CUSTOM.get(name);
    }
    static boolean instagram(String pkg) { return forPackage(pkg) instanceof InstagramDetector; }
    static boolean youtube(String pkg) { return forPackage(pkg) instanceof YouTubeDetector; }
    static void load(java.util.Map<String,?> saved) {
        CUSTOM.clear();
        for(java.util.Map.Entry<String,?> entry:saved.entrySet()) {
            if(!(entry.getValue() instanceof String))continue;
            ShortVideoDetector base=forPackage((String)entry.getValue());
            if(base instanceof InstagramDetector)CUSTOM.put(entry.getKey(),new InstagramDetector(entry.getKey()));
            else if(base instanceof YouTubeDetector)CUSTOM.put(entry.getKey(),new YouTubeDetector(entry.getKey()));
            else if(base!=null)CUSTOM.put(entry.getKey(),new SocialDetector(entry.getKey(),base.platformName()));
        }
    }
}
