package in.scrollcount.probe;
final class Detectors {
    private static final ShortVideoDetector[] ALL= {
        new InstagramDetector(),new YouTubeDetector()
    };
    static ShortVideoDetector forPackage(String name) {
        for(ShortVideoDetector detector:ALL)if(detector.packageName().equals(name))return detector;
        return null;
    }
}
