package in.scrollcount.probe;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.graphics.Rect;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
public final class ProbeService extends AccessibilityService {
    private static ProbeService activeService;
    private CountingEngine engine;
    private PlayerIndicator indicator;
    private boolean indicatorReady,indicatorQuery;
    private long indicatorDay=-1,indicatorRevision=-1,indicatorItemStart=-1,indicatorInstagram,indicatorYouTube;
    private String indicatorSession="";
    private final Wellbeing wellbeing=new Wellbeing();
    private long checkpointStart=-1,checkpointDuration;
    private android.content.SharedPreferences preferences;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceListener=(prefs,key)-> guard("preferences",()->preferencesChanged(prefs,key));
    private void preferencesChanged(android.content.SharedPreferences prefs,String key) {
        if("liveBadge".equals(key)) {
            if(!prefs.getBoolean("liveBadge",false)) { if(indicator!=null)indicator.hide(); }
            else refreshIndicator();
        }
        if("diagnostics".equals(key)) ProbeStore.setDiagnostics(prefs.getBoolean("diagnostics",false),(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        if("running".equals(key) || "consentVersion".equals(key)) {
            if(!trackingAllowed()) {
                invalidateReads();
                ProbeService.this.handler.removeCallbacks(ProbeService.this.scanTask);
                queued=false;
                if(engine!=null) engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
                hideIndicator();
                ProbeStore.reset("");
                applyProfile();
            }
        }
    }
    private final android.content.BroadcastReceiver screenReceiver=new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context,android.content.Intent intent) {
            guard("screen event",()->screenChanged(intent));
        }
        private void screenChanged(android.content.Intent intent) {
            if(!android.content.Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                indicatorDay=-1;
                refreshIndicator();
                return;
            }
            invalidateReads();
            if(engine!=null) engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
            hideIndicator();
            ProbeStore.counter.forgetFingerprints();
            instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;
            youtubeFast.reset();
        }
    };
    static void reloadApps() {
        if(activeService==null)return;
        stopForReset();
        finishReset();
    }
    static void stopForReset() {
        ProbeService service=activeService;
        if(service==null)return;
        service.resettingHistory=true;
        service.invalidateReads();
        if(service.engine!=null)service.engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
        service.hideIndicator();
        service.handler.removeCallbacks(service.quickTask);
        service.handler.removeCallbacks(service.scanTask);
        service.queued=service.quickQueued=false;
        service.youtubeFast.reset();
        service.instagramConfirmedAt=service.youtubeConfirmedAt=service.lastPagerAt=0;
        service.pagerIndex=service.acceptedPagerIndex=-1;
        service.checkpointStart=-1;
        service.checkpointDuration=0;
        service.indicatorDay=service.indicatorRevision=-1;
        ProbeStore.reset("");
        service.applyProfile();
    }
    static void finishReset() {
        if(activeService!=null)activeService.resettingHistory=false;
    }
    static CountingEngine.Live live() {
        return activeService==null || activeService.engine==null?null:activeService.engine.live(SystemClock.elapsedRealtime());
    }
    private volatile boolean resettingHistory;
    private boolean trackingAllowed() {
        return !resettingHistory && preferences!=null && preferences.getBoolean("running",false) && preferences.getInt("consentVersion",0)>=3;
    }
    @Override public void onCreate() {
        super.onCreate();
        activeService=this;
        ServiceHealth.attach(this);
        ServiceHealth.event("service created");
        preferences=getSharedPreferences("privacy",0);
        Detectors.load(getSharedPreferences("supported_apps",0).getAll());
        engine=new CountingEngine(HistoryStore.get(this));
        guard("sharing initialization",()->FriendSync.get(this));
        indicator=new PlayerIndicator(this);
        ProbeStore.setDiagnostics(preferences.getBoolean("diagnostics",false),(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        android.content.IntentFilter filter=new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF);
        filter.addAction(android.content.Intent.ACTION_DATE_CHANGED);
        filter.addAction(android.content.Intent.ACTION_TIME_CHANGED);
        filter.addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED);
        guard("screen receiver registration",()-> {
            if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(screenReceiver,filter,android.content.Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver,filter);
        });
    }
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());
    private final ReadLane reader=new ReadLane((java.util.function.Consumer<RuntimeException>)error->handler.post(()->guard("background reader",()->{throw error;})));
    private volatile long readEpoch;
    private volatile boolean destroyed;
    private int playerWindow=-1;
    private long sourceSequence;
    private final java.util.Set<Runnable> readTimers=new java.util.HashSet<>();
    private void invalidateReads() {
        readEpoch++;reader.cancelPending();
        for(Runnable timeout:readTimers)handler.removeCallbacks(timeout);
        readTimers.clear();
    }
    private boolean currentRead(String pkg,long epoch) {
        return !destroyed && trackingAllowed() && epoch==readEpoch && pkg.equals(ProbeStore.target);
    }
    private void submitRead(String key,long epoch,Runnable read,Runnable release) {
        reader.submit(key,()-> {
            if(destroyed || epoch!=readEpoch)return;
            final Runnable timeout=()-> {
                if(destroyed || epoch!=readEpoch)return;
                invalidateReads();
                ServiceHealth.event("accessibility read exceeded two seconds; observation paused");
                guard("slow reader",()-> {
                    suspend("Social app is responding slowly — timing paused");
                    if(unavailableRetries++<3)schedule(600);
                });
            };
            handler.post(()-> {if(!destroyed && epoch==readEpoch){readTimers.add(timeout);handler.postDelayed(timeout,2000);}});
            try {read.run();}
            finally {handler.post(()->{handler.removeCallbacks(timeout);readTimers.remove(timeout);});}
        },release);
    }
    private boolean queued;
    private long generation;
    private int verification;
    private boolean expandedRecovery;
    private int unavailableRetries;
    private long scanCooldown=400;
    private long lastScan, dueAt, scrollUntil, lastSourceSample, instagramConfirmedAt, lastPagerAt, youtubeConfirmedAt;
    private final FastFingerprintTracker youtubeFast=new FastFingerprintTracker();
    private boolean quickQueued;
    private int quickAttempts;
    private long quickGeneration;
    private final Runnable quickTask=()-> {
        quickQueued=false;
        if(quickGeneration==ProbeStore.generation) guard("footer callback",this::readYouTubeFooter);
    };
    private int pagerIndex=-1, acceptedPagerIndex=-1;
    private String sourceSummary="none";
    private final Runnable scanTask = () -> {
        queued=false;
        if(generation != ProbeStore.generation) return;
        guard("scan callback",this::scan);
    };
    @Override protected void onServiceConnected() {
        ProbeStore.connected=true;
        ServiceHealth.event("service connected");
        guard("service profile",this::applyProfile);
    }
    private void applyProfile() {
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null) return;
        // Expanded hierarchy recovers labels hidden by Lite layouts and older Android versions.
        ShortVideoDetector detector=Detectors.forPackage(ProbeStore.target);
        if(detector!=null && (detector.expandedHierarchy() || expandedRecovery || android.os.Build.VERSION.SDK_INT<=31)) info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        guard("accessibility event",()->handleEvent(event));
    }
    private void guard(String phase,Runnable action) {
        CallbackGuard.run(action,error-> {
            ServiceHealth.failure(phase,error);
            invalidateReads();
            handler.removeCallbacks(scanTask);
            handler.removeCallbacks(quickTask);
            queued=quickQueued=false;
            indicatorQuery=false;
            hideIndicator();
            instagramConfirmedAt=youtubeConfirmedAt=0;
            youtubeFast.reset();
            ProbeStore.counter.suspend();
            ProbeStore.status="Temporary service error — timing paused";
            if(engine!=null)engine.pause(SystemClock.elapsedRealtime());
        });
    }
    private void handleEvent(AccessibilityEvent event) {
        if(!trackingAllowed()) return;
        String pkg=String.valueOf(event.getPackageName());
        // Our badge is a display-only window. Its own events must not close the feed
        // or schedule another scan; opening the actual ScrollCount activity still closes it.
        if(indicator!=null && (pkg.equals(getPackageName()) || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)
                && indicator.ownsWindow(event.getWindowId()))return;
        ShortVideoDetector detector=Detectors.forPackage(pkg);
        if(detector==null) {
            if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getEventType()!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
            if(pkg.equals("null"))return;
            invalidateReads();
            engine.pause(SystemClock.elapsedRealtime());
            if(pkg.equals("android") || pkg.equals("com.android.systemui")) { suspend("System overlay — timing paused"); return; }
            inspectWindow(pkg);
            return;
        }
        if(!pkg.equals(ProbeStore.target)) {
            invalidateReads();
            engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
            hideIndicator();
            ProbeStore.reset(pkg);
        }
        if(generation != ProbeStore.generation) {
            hideIndicator();
            handler.removeCallbacks(scanTask);
            handler.removeCallbacks(quickTask);
            quickQueued=false;
            queued=false;
            scrollUntil=instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;
            youtubeFast.reset();
            pagerIndex=acceptedPagerIndex=-1;
            sourceSummary="none";
            generation=ProbeStore.generation;
            lastScan=lastSourceSample=0;
            expandedRecovery=false;
            applyProfile();
        }
        playerWindow=event.getWindowId();
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)invalidateReads();
        ProbeStore.events++;
        boolean scroll=event.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED;
        boolean sampleSource=scroll || (Detectors.youtube(pkg) && SystemClock.elapsedRealtime()-lastSourceSample>=1000);
        if(sampleSource)readSource(event,pkg,scroll);
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            hideIndicator();
            instagramConfirmedAt=youtubeConfirmedAt=0;
            youtubeFast.reset();
            engine.pause(SystemClock.elapsedRealtime());
            maintain(true);
            ProbeStore.decision(SystemClock.elapsedRealtime()+" FEED_CONFIRMATION_CLEARED reason=window_change");
        }
        ProbeStore.add(SystemClock.elapsedRealtime()+" "+AccessibilityEvent.eventTypeToString(event.getEventType())+ " scroll="+event.getScrollY()+" indices="+event.getFromIndex()+":"+event.getToIndex());
        verification=0;
        unavailableRetries=0;
        // Content-change storms are sampled, not scanned at every progress update.
        schedule(scroll ? 180 : 800);
        maintain(false);
    }
    private void inspectWindow(String eventPackage) {
        final long epoch=readEpoch;
        final String target=ProbeStore.target;
        submitRead("window",epoch,()-> {
            String activePackage="",activeClass="";
            AccessibilityNodeInfo root=null;
            try {
                root=getRootInActiveWindow();
                if(root!=null){activePackage=String.valueOf(root.getPackageName());activeClass=String.valueOf(root.getClassName());}
            } catch(RuntimeException error){ServiceHealth.failure("window metadata",error);}
            finally {if(root!=null)root.recycle();}
            final String foreground=activePackage,kind=activeClass;
            handler.post(()->guard("window result",()-> {
                if(epoch!=readEpoch || destroyed || !trackingAllowed())return;
                if(eventPackage.equals(getPackageName()) && (foreground.equals(target) || kind.equals("android.widget.TextView"))) {
                    schedule(180);return;
                }
                if(!target.isEmpty() && foreground.equals(target)) {suspend("Temporary window — timing paused");schedule(180);return;}
                engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
                hideIndicator();ProbeStore.counter.forgetFingerprints();
                instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;youtubeFast.reset();
                ProbeStore.status="Waiting for Reels or Shorts";
            }));
        },()->{});
    }
    private void readSource(AccessibilityEvent event,String pkg,boolean scroll) {
        lastSourceSample=SystemClock.elapsedRealtime();
        final long arrivedAt=lastSourceSample,epoch=readEpoch;
        final int from=event.getFromIndex(),to=event.getToIndex(),eventType=event.getEventType(),changeTypes=event.getContentChangeTypes();
        final AccessibilityEvent copy=AccessibilityEvent.obtain(event);
        String key=scroll?"scroll:"+(sourceSequence++):"source";
        submitRead(key,epoch,()-> {
            if(!currentRead(pkg,epoch))return;
            String id="",type="";
            AccessibilityNodeInfo node=null;
            try {
                node=copy.getSource();
                if(node!=null && pkg.equals(String.valueOf(node.getPackageName()))) {
                    id=node.getViewIdResourceName();if(id==null)id="";
                    type=String.valueOf(node.getClassName());
                }
            } catch(RuntimeException error){ServiceHealth.failure("event source",error);}
            finally {if(node!=null)node.recycle();}
            final String sourceId=id,sourceType=type;
            handler.post(()->guard("source result",()-> {
                if(currentRead(pkg,epoch))applySource(pkg,sourceId,sourceType,from,to,eventType,changeTypes,scroll,arrivedAt);
            }));
        },copy::recycle);
    }
    private void applySource(String pkg,String id,String type,int from,int to,int eventType,int changeTypes,boolean scroll,long arrivedAt) {
        ShortVideoDetector detector=Detectors.forPackage(pkg);
        if(detector==null)return;
            sourceSummary="id="+id+" class="+type+" indices="+from+":"+to;
            long now=SystemClock.elapsedRealtime();
            if(now-arrivedAt>1500)return;
            if(scroll && !detector.usesPosition() && detector.movement(id,type,from,to)) {
                scrollUntil=arrivedAt+5000;
                youtubeFast.movement(arrivedAt);
                quickAttempts=0;
                if(FeedEvidence.valid(youtubeConfirmedAt,now)) queueYouTubeRead(20);
            }
            if(scroll && detector.usesPosition() && detector.movement(id,type,from,to)) {
                if(pagerIndex != from || lastPagerAt==0) {
                    scrollUntil=arrivedAt+2500;
                    lastPagerAt=arrivedAt;
                }
                pagerIndex=from;
                // Exact indexed player events preserve fast exposures; tree checks stay coalesced.
                if(FeedEvidence.valid(instagramConfirmedAt,now)) commitInstagramPosition(lastPagerAt,now);
                else ProbeStore.decision(now+" INDEX_NOT_ACCEPTED index="+pagerIndex+" reason=no_recent_feed_confirmation");
            }
            if(scroll) ProbeStore.scroll(SystemClock.elapsedRealtime()+" "+sourceSummary);
            else ProbeStore.source(SystemClock.elapsedRealtime()+" "+AccessibilityEvent.eventTypeToString(eventType)+" changeTypes="+changeTypes+" "+sourceSummary);

    }
    private void schedule(long delay) {
        long now=SystemClock.elapsedRealtime();
        long requested=Math.max(now+delay,lastScan+scanCooldown);
        if(queued && requested>=dueAt) return;
        if(queued) handler.removeCallbacks(scanTask);
        generation=ProbeStore.generation;
        queued=true;
        dueAt=requested;
        handler.postDelayed(scanTask,Math.max(0,requested-now));
    }
    private void leaveFeed(String reason) {
        invalidateReads();
        engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
        hideIndicator();ProbeStore.counter.forgetFingerprints();
        instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;youtubeFast.reset();
        pagerIndex=acceptedPagerIndex=-1;ProbeStore.status=reason;
    }
    private boolean screenAvailable() {
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return power!=null && keyguard!=null && power.isInteractive() && !keyguard.isKeyguardLocked();
    }
    private void scan() {
        if(ProbeStore.target.isEmpty() || !trackingAllowed()) return;
        lastScan=SystemClock.elapsedRealtime();
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(!power.isInteractive() || keyguard.isKeyguardLocked()) {
            engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
            suspend("Screen locked/off");
            return;
        }
        final String pkg=ProbeStore.target;final long epoch=readEpoch;
        final int index=pagerIndex,window=playerWindow;
        final Snapshot snapshot=new Snapshot(pkg,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
        snapshot.indexed=index>=0;
        submitRead("scan",epoch,()-> {
            if(!currentRead(pkg,epoch))return;
            ScanResult result=readSnapshot(snapshot,index,window,false);
            handler.post(()->guard("scan result",()-> {
                if(!currentRead(pkg,epoch))return;
                ProbeStore.readerDropped=reader.dropped();
                ProbeStore.scanTimeMs+=result.cost;
                ProbeStore.maxScanMs=Math.max(ProbeStore.maxScanMs,result.cost);
                scanCooldown=Math.max(400,Math.min(1600,result.cost*2));
                if(result.exited){leaveFeed("Waiting for Reels or Shorts");return;}
                if(result.snapshot==null || SystemClock.elapsedRealtime()-result.at>1500) {
                    suspend("Accessible player unavailable — timing paused");
                    if(unavailableRetries++<3)schedule(300L*unavailableRetries);
                    return;
                }
                applyScan(result);maintain(false);
            }));
        },()->{});
    }
    private static final class ScanResult {
        Snapshot snapshot;final Rect bounds=new Rect();String hash="";
        int index;long cost,at;boolean exited;
    }
    private ScanResult readSnapshot(Snapshot snapshot,int index,int window,boolean footerOnly) {
        ScanResult result=new ScanResult();result.index=index;
        long started=SystemClock.elapsedRealtime();
        snapshot.deadline=started+1000;
        AccessibilityNodeInfo root=null;
        try {
            root=readPlayerRoot(snapshot.pkg,window,result);
            if(root==null)return result;
            root.getBoundsInScreen(result.bounds);
            if(footerOnly)collectVisibleYouTubeFooter(root,snapshot);
            else {
                snapshot.walk(root,0);
                if(Detectors.youtube(snapshot.pkg))snapshot.feed|=YouTubeSignals.confirmedContainer(snapshot.ytRecycler,snapshot.ytPage);
                boolean structure=Detectors.youtube(snapshot.pkg)?snapshot.ytRecycler&&snapshot.ytPage:snapshot.feed&&snapshot.like&&snapshot.share;
                if(structure && !snapshot.overlay && !snapshot.cut) {
                    if(Detectors.youtube(snapshot.pkg))collectVisibleYouTubeFooter(root,snapshot);
                    else if(index<0)snapshot.collect(root,0,new int[]{0},false);
                }
            }
            if(SystemClock.elapsedRealtime()>snapshot.deadline)snapshot.cut=true;
            result.snapshot=snapshot;result.hash=snapshot.finish();
        } catch(RuntimeException error){ServiceHealth.failure("background screen read",error);}
        finally {if(root!=null)root.recycle();result.at=SystemClock.elapsedRealtime();result.cost=result.at-started;}
        return result;
    }
    /** Only package/window metadata is read until the exact target package is verified. */
    private AccessibilityNodeInfo readPlayerRoot(String pkg,int expectedWindow,ScanResult result) {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null) {
            if(pkg.equals(String.valueOf(root.getPackageName())))return root;
            boolean ownBadge=getPackageName().equals(String.valueOf(root.getPackageName())) && "android.widget.TextView".contentEquals(String.valueOf(root.getClassName()));
            root.recycle();
            // Our display-only overlay can mask the active root on some OEMs.
            // Never use this fallback for an unrelated foreground app.
            if(!ownBadge){result.exited=true;return null;}
        }
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows=getWindows();
        try {
            for(android.view.accessibility.AccessibilityWindowInfo window:windows) {
                if(window.getType()!=android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION || !(window.isActive() || window.isFocused()))continue;
                if(expectedWindow>=0 && window.getId()!=expectedWindow)continue;
                AccessibilityNodeInfo candidate=window.getRoot();
                if(candidate==null)continue;
                if(pkg.equals(String.valueOf(candidate.getPackageName())))return candidate;
                candidate.recycle();
            }
        } finally {for(android.view.accessibility.AccessibilityWindowInfo window:windows)window.recycle();}
        return null;
    }
    private void applyScan(ScanResult result) {
        if(result.index!=pagerIndex){schedule(180);return;}
        if(!screenAvailable()){engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());suspend("Screen locked/off");return;}
        Snapshot s=result.snapshot;Rect rootBounds=result.bounds;
        boolean youtube=Detectors.youtube(ProbeStore.target);
        boolean playerStructure=youtube?s.ytRecycler&&s.ytPage:s.feed&&s.like&&s.share;
            ProbeStore.scans++;
            if(s.cut) ProbeStore.truncated++;
            ProbeStore.tree=s.structure.toString();

            String snapshotInfo="at="+SystemClock.elapsedRealtime()+" bounds="+rootBounds.flattenToString()+" nodes="+s.nodes+" truncated="+s.cut+ " feed="+s.feed+" youtubeRecycler="+s.ytRecycler+" youtubePage="+s.ytPage+" like="+s.like+" share="+s.share+" contentFields="+s.fields+ " expandedHierarchy="+Detectors.youtube(ProbeStore.target);
            long area=(long)Math.max(0,rootBounds.width())*Math.max(0,rootBounds.height());
            long screenArea=(long)getResources().getDisplayMetrics().widthPixels*getResources().getDisplayMetrics().heightPixels;
            long score=(screenArea>0 ? Math.min(1000000,area*1000000/screenArea) : 0)+s.nodes;
            ProbeStore.retainTree(s.structure.toString(),snapshotInfo,score,s.feed);
            ProbeStore.signals="rootBounds="+rootBounds.flattenToString()+", youtubeRecycler="+s.ytRecycler+", youtubePage="+s.ytPage+", feedMarker="+s.feed+", like="+s.like+", share="+s.share+", overlay="+s.overlay+ ", captionFields="+s.captionFields+", pagerIndex="+pagerIndex+", lastScrollSource="+sourceSummary+", contentFields="+s.fields+", nodes="+s.nodes+", truncated="+s.cut;
            boolean fullPlayer=FeedEvidence.fullPlayer(rootBounds.width(),rootBounds.height(),getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
            boolean eligible=playerStructure && !s.overlay && !s.cut && FeedEvidence.identityAvailable(s.fields,Detectors.instagram(ProbeStore.target),pagerIndex) && fullPlayer;
            indicatorReady=playerStructure && !s.overlay && !s.profile && !s.cut && fullPlayer;
            String hash=result.hash;
            ProbeStore.fingerprint=ProbeStore.enabled?hash:"";
            int before=ProbeStore.counter.accepted;
            ShortVideoDetector detector=Detectors.forPackage(ProbeStore.target);
            boolean instagram=detector.usesPosition();
            // Exact player-pager position distinguishes identical captions and cannot change on a like.
            String identity=instagram && pagerIndex>=0 ? "pager:"+pagerIndex : hash;
            boolean movement=instagram ? (pagerIndex>=0 && pagerIndex!=acceptedPagerIndex && SystemClock.elapsedRealtime()<=scrollUntil) : SystemClock.elapsedRealtime()<=scrollUntil;
            long observationTime=SystemClock.elapsedRealtime();
            if(instagram) instagramConfirmedAt=FeedEvidence.update(instagramConfirmedAt,observationTime,eligible,s.feed,s.overlay||s.profile,!s.cut,fullPlayer);
            else if(youtube) {
                // Container confirmation and label availability are separate. A loading title must
                // not prevent later scoped retries, but overlays/exits still invalidate the lease.
                boolean shortsContainer=s.ytRecycler && s.ytPage;
                youtubeConfirmedAt=FeedEvidence.update(youtubeConfirmedAt,observationTime,
                        shortsContainer && !s.overlay && !s.profile && !s.cut && fullPlayer,
                        shortsContainer,s.overlay||s.profile,!s.cut,fullPlayer);
                if(FeedEvidence.valid(youtubeConfirmedAt,observationTime) && youtubeFast.active(observationTime))
                    queueYouTubeRead(20);
            }
            if(!eligible) ProbeStore.decision(observationTime+" SCAN_NOT_CONFIRMED feed="+s.feed+" overlay="+s.overlay+" truncated="+s.cut+" fullPlayer="+fullPlayer+" like="+s.like+" share="+s.share+" youtubeRecycler="+s.ytRecycler+" youtubePage="+s.ytPage+" fields="+s.fields);
            if(instagram && eligible && pagerIndex>=0) {
                instagramConfirmedAt=observationTime;
                long positionTime=lastPagerAt>0 && observationTime-lastPagerAt<=2500?lastPagerAt:observationTime;
                ProbeStore.status=ProbeStore.counter.verifiedPosition(pagerIndex,positionTime,true);
            }
            else if(youtube && eligible && ProbeStore.counter.hasBaseline()) {
                youtubeConfirmedAt=observationTime;
                acceptYouTubeFingerprint(hash,observationTime);
                // The fast helper logs its own accepted change.
                before=ProbeStore.counter.accepted;
                if(youtubeFast.active(observationTime)) queueYouTubeRead(20);
            }
            else {
                ProbeStore.status=ProbeStore.counter.observe(identity,observationTime,eligible,movement);
                if(youtube && ProbeStore.counter.isCommitted(identity)) youtubeFast.seed(identity);
            }
            if(ProbeStore.counter.accepted!=before || ProbeStore.status.startsWith("Baseline acquired")) acceptedPagerIndex=pagerIndex;
            if(ProbeStore.counter.accepted!=before) ProbeStore.transition(SystemClock.elapsedRealtime()+" ACCEPTED_TRANSITION #"+ProbeStore.counter.accepted);
            if(eligible && ProbeStore.counter.isCommitted(identity)) engine.observe(detector.platformName(),identity,ProbeStore.counter.committedAt(),SystemClock.elapsedRealtime(),System.currentTimeMillis());
            else if(!eligible) {
                if(s.profile || (!s.feed && !s.overlay)) {
                    engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
                    ProbeStore.counter.forgetFingerprints();
                    instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;
                    youtubeFast.reset();
                }
                else engine.pause(SystemClock.elapsedRealtime());
            }
            if(!eligible) ProbeStore.status="Unconfirmed feed / overlay / unavailable content signal — no count";
            // Finite confirmation reads, not a permanent polling loop.
            if(eligible && verification++<2) schedule(TransitionProbe.STABLE_MS+50);
            else if(!s.overlay && !s.profile && s.feed && unavailableRetries++<3) {
                if(!expandedRecovery){expandedRecovery=true;applyProfile();}
                schedule(600);
            }

    }
    private void queueYouTubeRead(long delay) {
        if(quickQueued || quickAttempts>=36) return;
        quickQueued=true;
        quickGeneration=ProbeStore.generation;
        handler.postDelayed(quickTask,delay);
    }
    private void acceptYouTubeFingerprint(String hash,long now) {
        if(!youtubeFast.observe(hash,now,true)) return;
        int before=ProbeStore.counter.accepted;
        ProbeStore.status=ProbeStore.counter.verifiedFingerprint(hash,youtubeFast.acceptedAt());
        engine.observe(Detectors.forPackage(ProbeStore.target).platformName(),hash,ProbeStore.counter.committedAt(),now,System.currentTimeMillis());
        refreshIndicator();
        if(ProbeStore.counter.accepted!=before) ProbeStore.transition(now+" ACCEPTED_TRANSITION #"+ProbeStore.counter.accepted);
    }
    private void readYouTubeFooter() {
        long now=SystemClock.elapsedRealtime();
        if(!trackingAllowed() || !Detectors.youtube(ProbeStore.target) || !FeedEvidence.valid(youtubeConfirmedAt,now) || !youtubeFast.active(now)) return;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(!power.isInteractive() || keyguard.isKeyguardLocked())return;
        quickAttempts++;
        final String pkg=ProbeStore.target;final long epoch=readEpoch;final int window=playerWindow;
        Snapshot snapshot=new Snapshot(pkg,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
        submitRead("footer",epoch,()-> {
            if(!currentRead(pkg,epoch))return;
            ScanResult result=readSnapshot(snapshot,-1,window,true);
            handler.post(()->guard("footer result",()-> {
                if(!currentRead(pkg,epoch))return;
                long at=SystemClock.elapsedRealtime();
                if(result.exited){leaveFeed("Waiting for Reels or Shorts");return;}
                if(!screenAvailable())return;
                if(!FeedEvidence.valid(youtubeConfirmedAt,at) || !youtubeFast.active(at))return;
                if(result.snapshot!=null && !result.snapshot.cut && at-result.at<=1500
                        && FeedEvidence.fullPlayer(result.bounds.width(),result.bounds.height(),snapshot.screenWidth,snapshot.screenHeight)) {
                    ProbeStore.quickReads++;
                    if(result.snapshot.fields>0){ProbeStore.fingerprint=ProbeStore.enabled?result.hash:"";acceptYouTubeFingerprint(result.hash,at);}
                    else youtubeFast.observe(null,at,false);
                }
                continueYouTubeRead();
            }));
        },()->{});
    }
    private void continueYouTubeRead() {
        long now=SystemClock.elapsedRealtime();
        if(FeedEvidence.valid(youtubeConfirmedAt,now) && youtubeFast.active(now))
            queueYouTubeRead(quickAttempts<12?70:120);
    }
    private void collectVisibleYouTubeFooter(AccessibilityNodeInfo root,Snapshot snapshot) {
        java.util.List<AccessibilityNodeInfo> footers=root.findAccessibilityNodeInfosByViewId(snapshot.pkg+":id/reel_player_footer_container");
        try {
            AccessibilityNodeInfo best=null;
            long largest=0;
            for(AccessibilityNodeInfo footer:footers) {
                if(!footer.isVisibleToUser())continue;
                Rect bounds=new Rect();
                footer.getBoundsInScreen(bounds);
                long area=(long)Math.max(0,bounds.width())*Math.max(0,bounds.height());
                if(area>largest) {
                    best=footer;
                    largest=area;
                }
            }
            if(best!=null)snapshot.findYouTubeFooter(best,0,new int[] {
                0
            },false);
            else snapshot.findYouTubeFooter(root,0,new int[]{0},false);
        }
        finally {
            for(AccessibilityNodeInfo footer:footers)footer.recycle();
        }
    }
    private void commitInstagramPosition(long at,long now) {
        int before=ProbeStore.counter.accepted;
        ProbeStore.status=ProbeStore.counter.verifiedPosition(pagerIndex,at,true);
        acceptedPagerIndex=pagerIndex;
        engine.observe(Detectors.forPackage(ProbeStore.target).platformName(),"pager:"+pagerIndex,ProbeStore.counter.committedAt(),now,System.currentTimeMillis());
        refreshIndicator();
        if(ProbeStore.counter.accepted!=before) ProbeStore.transition(now+" ACCEPTED_TRANSITION #"+ProbeStore.counter.accepted);
    }
    private void maintain(boolean force) {
        refreshIndicator();
        if(engine==null)return;
        long now=SystemClock.elapsedRealtime();
        CountingEngine.Live live=engine.live(now);
        if(live==null)return;
        boolean different=checkpointStart!=live.startedAt;
        if((force && (different || live.duration!=checkpointDuration)) || (live.duration>=2000 && (different || live.duration-checkpointDuration>=15000))) {
            engine.checkpoint(now,System.currentTimeMillis());
            checkpointStart=live.startedAt;
            checkpointDuration=live.duration;
        }
        try {
            wellbeing.observe(this,live);
        }
        catch(RuntimeException ignored) {
            /* Notification settings must not interrupt counting. */
        }
    }
    private void refreshIndicator() {
        if(indicator==null)return;
        if(!trackingAllowed() || !ProbeStore.connected || !preferences.getBoolean("liveBadge",false)
                || !indicatorReady) {
            indicator.hide();
            return;
        }
        CountingEngine.Live live=engine==null?null:engine.live(SystemClock.elapsedRealtime());
        if(live!=null && live.paused) { indicator.hide(); return; }
        ShortVideoDetector detector=Detectors.forPackage(ProbeStore.target);
        if(detector==null) { indicator.hide(); return; }
        long day=DailyBadge.start(System.currentTimeMillis()),end=DailyBadge.end(day);
        long itemStart=live==null?-1:live.startedAt;
        String session=live==null?"":live.sessionId;
        HistoryStore history=HistoryStore.get(this);
        if(indicatorDay==day && indicatorRevision==history.revision.get()
                && indicatorItemStart==itemStart && indicatorSession.equals(session)) {
            indicator.show(indicatorInstagram+indicatorYouTube);
            return;
        }
        if(indicatorDay!=day)indicator.show(-1);
        if(indicatorQuery)return;
        indicatorQuery=true;
        history.dailyItems(day,end,totals->guard("badge history result",()-> {
            indicatorQuery=false;
            if(activeService!=this)return;
            CountingEngine.Live current=engine.live(SystemClock.elapsedRealtime());
            long currentStart=current==null?-1:current.startedAt;
            String currentSession=current==null?"":current.sessionId;
            // A transition/exit while the read was queued invalidates its active-item snapshot.
            if(day!=DailyBadge.start(System.currentTimeMillis()) || currentStart!=itemStart
                    || !session.equals(currentSession)) {
                refreshIndicator();
                return;
            }
            if(totals[3]==0) { indicator.hide(); return; }
            indicatorDay=day;
            indicatorRevision=totals[2];
            indicatorItemStart=itemStart;
            indicatorSession=session;
            indicatorInstagram=totals[0];
            indicatorYouTube=totals[1];
            refreshIndicator();
        }));
    }
    private void hideIndicator() {
        indicatorReady=false;
        if(indicator!=null)indicator.hide();
    }
    private void suspend(String reason) {
        hideIndicator();
        instagramConfirmedAt=youtubeConfirmedAt=0;
        if(engine!=null) engine.pause(SystemClock.elapsedRealtime());
        ProbeStore.counter.suspend();
        maintain(true);
        ProbeStore.status=reason;
        ProbeStore.fingerprint="";
    }
    @Override public void onInterrupt() {
        invalidateReads();
        ServiceHealth.event("service interrupted");
        handler.removeCallbacks(scanTask);
        handler.removeCallbacks(quickTask);
        queued=quickQueued=false;
        guard("interrupt",()->suspend("Accessibility interrupted"));
    }
    @Override public void onDestroy() {
        destroyed=true;invalidateReads();reader.close();
        ServiceHealth.event("service destroyed");
        handler.removeCallbacks(scanTask);
        handler.removeCallbacks(quickTask);
        CallbackGuard.run(this::hideIndicator,error->ServiceHealth.failure("badge cleanup",error));
        CallbackGuard.run(()-> { if(engine!=null)engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis()); },error->ServiceHealth.failure("session cleanup",error));
        CallbackGuard.run(()-> { if(preferences!=null)preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener); },error->ServiceHealth.failure("preference cleanup",error));
        CallbackGuard.run(()->unregisterReceiver(screenReceiver),error->ServiceHealth.failure("receiver cleanup",error));
        activeService=null;
        ProbeStore.connected=false;
        ProbeStore.reset("");
        super.onDestroy();
    }
    private static final class Snapshot {
        final String pkg;
        final String platform;
        final java.util.Set<String> socialLabels=new java.util.TreeSet<>();
        final StringBuilder structure=new StringBuilder();
        final Mac mac;
        final java.util.Set<String> youtubeLabels=new java.util.TreeSet<>();
        int nodes,visitedNodes,fields,captionFields;
        final int maxNodes,maxDepth,screenWidth,screenHeight;
        final boolean verbose;
        long deadline;
        boolean feed,like,share,overlay,cut,ytRecycler,ytPage,profile,indexed;
        Snapshot(String pkg,int screenWidth,int screenHeight) {
            this.screenWidth=screenWidth; this.screenHeight=screenHeight;
            verbose=ProbeStore.verbose;
            this.pkg=pkg;
            platform=Detectors.forPackage(pkg).platformName();
            ShortVideoDetector detector=Detectors.forPackage(pkg);
            maxNodes=detector.maxNodes();
            maxDepth=detector.maxDepth();
            try {
                mac=Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(ProbeStore.salt.clone(),"HmacSHA256"));
            }
            catch(java.security.GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
        void walk(AccessibilityNodeInfo n,int depth) {
            if(visitedNodes++>=maxNodes*4 || depth>maxDepth || SystemClock.elapsedRealtime()>deadline) {
                cut=true;
                return;
            }
            if(!n.isVisibleToUser()) {
                for(int i=0;i<n.getChildCount() && nodes<maxNodes;i++) {
                    AccessibilityNodeInfo child=n.getChild(i);
                    if(child!=null)try { walk(child,depth+1); } finally { child.recycle(); }
                }
                if(nodes>=maxNodes)cut=true;
                return;
            }
            if(nodes>=maxNodes){cut=true;return;}
            nodes++;
            String id=n.getViewIdResourceName();
            id=id==null?"":id;
            String lowerId=id.toLowerCase(Locale.ROOT);
            String type=String.valueOf(n.getClassName());
            // Never process typed/password content, even transiently.
            if(n.isPassword() || n.isEditable() || type.contains("EditText")) {
                overlay=true;
                return;
            }
            if(verbose) {
                Rect bounds=new Rect();
                n.getBoundsInScreen(bounds);
                for(int j=0;j<Math.min(depth,maxDepth);j++) structure.append(" ");
                structure.append(type).append(" id=").append(id) .append(" bounds=").append(bounds.flattenToString()).append(" children=").append(n.getChildCount()) .append(" selected=").append(n.isSelected()).append(" clickable=").append(n.isClickable()) .append(" text=").append(n.getText()==null?"none":"[redacted]") .append(" description=").append(n.getContentDescription()==null?"none":"[redacted]").append('\n');
            }
            boolean control=type.endsWith("Button") || n.isSelected();
            String text=control?String.valueOf(n.getText()).toLowerCase(Locale.ROOT):"";
            String desc=control?String.valueOf(n.getContentDescription()).toLowerCase(Locale.ROOT):"";
            String controls=lowerId+" "+text+" "+desc;
            Rect nodeBounds=new Rect();
            n.getBoundsInScreen(nodeBounds);
            boolean socialPlayer=SocialSignals.player(id,platform) && FeedEvidence.fullPlayer(nodeBounds.width(),nodeBounds.height(),screenWidth,screenHeight);
            feed |= (Detectors.instagram(pkg) ? InstagramSignals.player(id) : Detectors.youtube(pkg) ? lowerId.contains("shorts") : socialPlayer) || ((Detectors.instagram(pkg) || Detectors.youtube(pkg)) && (n.isSelected() || !n.isClickable()) && (text.equals("reels") || text.equals("shorts") || text.equals("spotlight") || desc.equals("reels") || desc.equals("spotlight")));
            if(Detectors.instagram(pkg) && indexed) {
                if(InstagramSignals.author(id) || InstagramSignals.caption(id))fields++;
                if(InstagramSignals.caption(id))captionFields++;
            }
            ytRecycler |= Detectors.youtube(pkg) && YouTubeSignals.recycler(id);
            ytPage |= Detectors.youtube(pkg) && YouTubeSignals.page(id);
            like |= controls.contains("like") || controls.contains("पसंद") || controls.contains("favorite") || controls.contains("favourite");
            share |= controls.contains("share") || controls.contains("send") || controls.contains("शेयर");
            profile |= lowerId.contains("profile_header");
            overlay |= lowerId.contains("comment_sheet") || lowerId.contains("comments_panel") || lowerId.contains("bottom_sheet") || lowerId.contains("profile_header") || lowerId.contains("share_sheet") || lowerId.contains("chat_input") || lowerId.contains("story_viewer");
            for(int i=0;i<n.getChildCount();i++) {
                if(nodes>=maxNodes) {
                    cut=true;
                    break;
                }
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null) {
                    try {
                        walk(child,depth+1);
                    }
                    finally {
                        child.recycle();
                    }
                }
            }
        }
        void collect(AccessibilityNodeInfo n,int depth,int[] visited,boolean inCaption) {
            if(SystemClock.elapsedRealtime()>deadline){cut=true;return;}
            if(visited[0]++>=maxNodes || depth>maxDepth || !n.isVisibleToUser() || n.isPassword() || n.isEditable()) return;
            String id=n.getViewIdResourceName();
            id=id==null?"":id;
            String lowerId=id.toLowerCase(Locale.ROOT);
            inCaption |= Detectors.instagram(pkg) ? InstagramSignals.caption(id) : SocialSignals.content(id);
            // Caption content is exposed on anonymous descendants in this Instagram version.
            boolean contentId=Detectors.instagram(pkg) ? (InstagramSignals.author(id) || inCaption) :inCaption;
            if(contentId) {
                String raw=n.getText()!=null?n.getText().toString():n.getContentDescription()!=null?n.getContentDescription().toString():"";
                if(!raw.trim().isEmpty()) {
                    byte[] value=(id+"\u0000"+raw.trim()+"\u0000").getBytes(StandardCharsets.UTF_8);
                    if(Detectors.instagram(pkg))mac.update(value);
                    else socialLabels.add(raw.trim());
                    java.util.Arrays.fill(value,(byte)0);
                    fields++;
                    if(inCaption) captionFields++;
                }
            }
            for(int i=0;i<n.getChildCount();i++) {
                if(visited[0]>=maxNodes) break;
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null) {
                    try {
                        collect(child,depth+1,visited,inCaption);
                    }
                    finally {
                        child.recycle();
                    }
                }
            }
        }
        void findYouTubeFooter(AccessibilityNodeInfo n,int depth,int[] visited,boolean inFooter) {
            if(SystemClock.elapsedRealtime()>deadline){cut=true;return;}
            if(visited[0]++>=maxNodes || depth>maxDepth || !n.isVisibleToUser() || n.isPassword() || n.isEditable()) return;
            String id=n.getViewIdResourceName();
            inFooter |= YouTubeSignals.footer(id);
            if(inFooter && YouTubeSignals.overlayRoot(id)) {
                // Observed layout: player_overlay -> content group -> left title/channel/music rows + right action rail.
                AccessibilityNodeInfo group=n.getChildCount()>0?n.getChild(0):null;
                if(group!=null) {
                    try {
                        Rect parent=new Rect();
                        group.getBoundsInScreen(parent);
                        if(group.getChildCount()>=2) {
                            for(int rowIndex=0;rowIndex<2;rowIndex++) {
                                AccessibilityNodeInfo rowNode=group.getChild(rowIndex);
                                if(rowNode==null) continue;
                                try {
                                    Rect row=new Rect();
                                    rowNode.getBoundsInScreen(row);
                                    if(!YouTubeSignals.leftRow(row.left,row.right,parent.left,parent.width())) continue;
                                    if(rowIndex==0) collectTitle(rowNode,depth+2,new int[] {
                                        0
                                    });
                                    else {
                                        // Observed second row: content at child 0; adjacent action at child 1.
                                        AccessibilityNodeInfo content=rowNode.getChildCount()>0?rowNode.getChild(0):null;
                                        if(content!=null) {
                                            try {
                                                collectTitle(content,depth+3,new int[] {
                                                    0
                                                });
                                            }
                                            finally {
                                                content.recycle();
                                            }
                                        }
                                    }
                                }
                                finally {
                                    rowNode.recycle();
                                }
                            }
                        }
                    }
                    finally {
                        group.recycle();
                    }
                }
                return;
            }
            for(int i=0;i<n.getChildCount() && visited[0]<maxNodes;i++) {
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null) {
                    try {
                        findYouTubeFooter(child,depth+1,visited,inFooter);
                    }
                    finally {
                        child.recycle();
                    }
                }
            }
        }
        void collectTitle(AccessibilityNodeInfo n,int depth,int[] visited) {
            YouTubeLabels.collect(new LabelNode(n),Math.max(0,maxDepth-depth),youtubeLabels::add);
            fields=youtubeLabels.size();
            captionFields=fields;
        }
        private static final class LabelNode implements YouTubeLabels.Node {
            private final AccessibilityNodeInfo node;
            LabelNode(AccessibilityNodeInfo node) { this.node=node; }
            public boolean readable() {
                return node.isVisibleToUser() && !node.isPassword() && !node.isEditable()
                        && !String.valueOf(node.getClassName()).contains("EditText");
            }
            public CharSequence text() { return node.getText(); }
            public CharSequence description() { return node.getContentDescription(); }
            public int children() { return node.getChildCount(); }
            public YouTubeLabels.Node child(int index) {
                AccessibilityNodeInfo child=node.getChild(index);
                return child==null?null:new LabelNode(child);
            }
            public void close() { node.recycle(); }
        }
        String finish() {
            if(fields==0) return "";
            // Sort/deduplicate labels so an accessibility wrapper or row-order change does
            // not create a different identity for the same title/channel.
            for(String label:socialLabels)mac.update(("social-content\u0000"+label+"\u0000").getBytes(StandardCharsets.UTF_8));
            for(String label:youtubeLabels) {
                byte[] value=("youtube-footer-label\u0000"+label+"\u0000").getBytes(StandardCharsets.UTF_8);
                mac.update(value);
                java.util.Arrays.fill(value,(byte)0);
            }
            byte[] hash=mac.doFinal();
            StringBuilder out=new StringBuilder();
            for(byte b:hash) out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }
    }
}
