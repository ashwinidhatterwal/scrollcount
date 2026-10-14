package in.scrollcount.probe;
import java.util.ArrayDeque;
import java.security.SecureRandom;
final class ProbeStore {
    static volatile String target = "", status = "Waiting for Reels or Shorts", tree = "", fingerprint = "", signals = "";
    static final ArrayDeque<String> scrolls=new ArrayDeque<>();
    static final ArrayDeque<String> decisions=new ArrayDeque<>();
    static final ArrayDeque<String> sources=new ArrayDeque<>();
    static String richestTree="", richestInfo="", feedTree="", feedInfo="";
    static long richestScore=-1, feedScore=-1;
    static String testPackage="";
    static final ArrayDeque<String> transitions=new ArrayDeque<>();
    static String appVersions = "unknown";
    static volatile boolean connected, enabled, verbose;
    static long scanTimeMs,maxScanMs,readerDropped;
    static int events, scans, truncated, quickReads;
    static byte[] salt = new byte[32];
    static final TransitionProbe counter = new TransitionProbe();
    static final ArrayDeque<String> log = new ArrayDeque<>();
    static volatile long generation;
    static void setDiagnostics(boolean on,boolean debug) {
        enabled=on;
        verbose=on&&debug;
        if(!on) {
            log.clear();
            transitions.clear();
            scrolls.clear();
            sources.clear();
            decisions.clear();
            tree=fingerprint=richestTree=richestInfo=feedTree=feedInfo="";
            richestScore=feedScore=-1;
        }
    }
    static void reset(String pkg) {
        generation++;
        target = pkg;
        testPackage=pkg;
        transitions.clear();
        decisions.clear();
        scrolls.clear();
        sources.clear();
        richestTree=richestInfo=feedTree=feedInfo="";
        richestScore=feedScore=-1;
        status = pkg.isEmpty() ? "Stopped" : "Waiting for Reels or Shorts";
        tree = fingerprint = signals = "";
        events = scans = truncated = quickReads = 0;
        scanTimeMs=maxScanMs=readerDropped=0;
        counter.reset();
        log.clear();
        new SecureRandom().nextBytes(salt);
    }
    static void add(String line) {
        if(!enabled)return;
        if (log.size() == 100) log.removeFirst();
        log.addLast(line);
    }
    static void retainTree(String tree,String info,long score,boolean feed) {
        if(!verbose)return;
        if(score>=richestScore) {
            richestScore=score;
            richestTree=tree;
            richestInfo=info;
        }
        if(feed && score>=feedScore) {
            feedScore=score;
            feedTree=tree;
            feedInfo=info;
        }
    }
    static void decision(String line) {
        if(!enabled)return;
        if(decisions.size()==100) decisions.removeFirst();
        decisions.addLast(line);
    }
    static void source(String line) {
        if(!enabled)return;
        if(sources.size()==100) sources.removeFirst();
        sources.addLast(line);
    }
    static void scroll(String line) {
        if(!enabled)return;
        if(scrolls.size()==200) scrolls.removeFirst();
        scrolls.addLast(line);
    }
    static void transition(String line) {
        if(!enabled)return;
        if(transitions.size()==200) transitions.removeFirst();
        transitions.addLast(line);
    }
    static String report() {
        return "ScrollCount 1.2.10 — service reliability candidate\nAndroid " + android.os.Build.VERSION.RELEASE + " / " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\nApp versions: " + appVersions + "\nTest package: " + testPackage + " / armed: " + !target.isEmpty() + "\nService connected: " + connected + "\nStatus: " + status + "\nEvents: " + events + " / tree scans: " + scans + " / truncated scans: " + truncated + " / YouTube footer checks: " + quickReads + "\nDiagnostics enabled: "+enabled+" / verbose debug trees: "+verbose+" / scan time total: "+scanTimeMs+"ms / maximum: "+maxScanMs+"ms"+ "\nReader queue drops/replacements: "+readerDropped+"\nAccepted fingerprint transitions: " + counter.accepted + "\nSignals: " + signals + "\nTemporary fingerprint: " + fingerprint + "\n\nAccepted transition timestamps (last 200)\n" + String.join("\n",transitions) + "\n\nScroll source diagnostics (last 200)\n" + String.join("\n",scrolls) + "\n\nRecent counting decisions (last 100; no text)\n"+String.join("\n",decisions)+"\n\nRecent sanitized events\n" + String.join("\n", log) + "\n\nSampled UI event sources (last 100; no text)\n" + String.join("\n",sources)+ (verbose?"\n\nRetained richest Shorts/Reels tree\n"+feedInfo+"\n"+feedTree+ "\n\nRetained richest supported-app tree\n"+richestInfo+"\n"+(richestTree.equals(feedTree)?"(same as retained feed tree above)":richestTree)+ "\n\nLatest sanitized tree (Instagram cap 360 / YouTube cap 360 / social cap 480)\n" + tree:"\n\nUI tree dumps are disabled in this build.");
    }
}
