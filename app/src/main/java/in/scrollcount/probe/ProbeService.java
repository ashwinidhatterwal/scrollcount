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
        if("diagnostics".equals(key)) ProbeStore.setDiagnostics(prefs.getBoolean("diagnostics",true),(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
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
            instagramIdentity.reset();instagramFast.reset();instagramAliasPosition=instagramPlayerWindow=-1;pagerIndex=acceptedPagerIndex=-1;
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
        service.instagramIdentity.reset();
        service.instagramAliasPosition=service.instagramPlayerWindow=-1;
        service.instagramFast.reset();
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
        ProbeStore.setDiagnostics(preferences.getBoolean("diagnostics",true),(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
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
    private final ReadLane sourceReader=new ReadLane((java.util.function.Consumer<RuntimeException>)error->handler.post(()->guard("source reader",()->{throw error;})));
    private volatile long readEpoch;
    private volatile boolean destroyed;
    private int playerWindow=-1;
    private String facebookSource="";
    private int facebookIndex=-1,facebookWindow=-1;
    private long facebookAt;
    private long snapchatMovementAt;private int snapchatMovementWindow=-1;
    private void resetFacebook() {
        facebookSource="";facebookIndex=facebookWindow=-1;facebookAt=0;
    }
    private long sourceSequence;
    private final java.util.Set<Runnable> readTimers=new java.util.HashSet<>();
    private void invalidateReads() {
        readEpoch++;reader.cancelPending();sourceReader.cancelPending();
        handler.removeCallbacks(browserTask);browserQueued=false;
        resetFacebook();snapchatMovementAt=0;snapchatMovementWindow=-1;
        instagramPendingAt=-1;instagramPendingWindow=-1;
        for(Runnable timeout:readTimers)handler.removeCallbacks(timeout);
        readTimers.clear();
    }
    private boolean currentRead(String pkg,long epoch) {
        return !destroyed && trackingAllowed() && epoch==readEpoch && pkg.equals(ProbeStore.target);
    }
    private void submitRead(String key,long epoch,Runnable read,Runnable release) {
        submitRead(reader,key,epoch,read,release);
    }
    private void submitRead(ReadLane lane,String key,long epoch,Runnable read,Runnable release) {
        lane.submit(key,()-> {
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
    // Browser Shorts/Reels. Only a platform-scoped salted hash of the video id is kept.
    private String browserPkg,browserIdentity,browserMode="none",browserPlatform="";
    private long browserMovementAt;
    private int browserRetries;
    private boolean browserQueued;
    private long browserReadAt;
    private final Runnable browserTask=()->guard("browser read",this::readBrowser);
    private int verification;
    private boolean expandedRecovery;
    private int unavailableRetries;
    private long scanCooldown=400;
    private long lastScan, dueAt, scrollUntil, lastSourceSample, instagramConfirmedAt, lastPagerAt, youtubeConfirmedAt;
    private final FastFingerprintTracker youtubeFast=new FastFingerprintTracker();
    private final FastFingerprintTracker instagramFast=new FastFingerprintTracker();
    private boolean quickQueued;
    private int quickAttempts;
    private long quickGeneration;
    private final Runnable quickTask=()-> {
        quickQueued=false;
        if(quickGeneration==ProbeStore.generation) guard("footer callback",this::readYouTubeFooter);
    };
    private int pagerIndex=-1, acceptedPagerIndex=-1;
    private int instagramAliasPosition=-1;
    private int instagramPlayerWindow=-1;
    private long instagramPendingAt=-1;
    private int instagramPendingWindow=-1;
    private final InstagramIdentity instagramIdentity=new InstagramIdentity();
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
        if(detector==null || detector.expandedHierarchy() || expandedRecovery || android.os.Build.VERSION.SDK_INT<=31) info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
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
            instagramFast.pause();
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
        // Browsers host arbitrary sites; they get their own address-bar-gated path (see browserEvent).
        if(BrowserSignals.browser(pkg)) { browserEvent(event,pkg); return; }
        if(browserPkg!=null && (Detectors.forPackage(pkg)!=null || event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            invalidateReads();browserPkg=browserIdentity=null;browserMode="none";browserPlatform="";browserMovementAt=0;
        }
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
            instagramIdentity.reset();instagramFast.reset();instagramAliasPosition=instagramPlayerWindow=-1;
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
            instagramFast.pause();
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
    private void browserEvent(AccessibilityEvent event,String pkg) {
        int type=event.getEventType();
        if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type!=AccessibilityEvent.TYPE_VIEW_SCROLLED && type!=AccessibilityEvent.TYPE_VIEW_CLICKED)return;
        if(!pkg.equals(ProbeStore.target)) {
            // Switching app/browser closes the old session and invalidates its pending reads.
            invalidateReads();
            engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
            hideIndicator();
            handler.removeCallbacks(scanTask);
            handler.removeCallbacks(quickTask);
            queued=quickQueued=false;
            youtubeFast.reset();
            instagramConfirmedAt=youtubeConfirmedAt=lastPagerAt=0;
            ProbeStore.reset(pkg);
            browserIdentity=null;browserMode="none";browserPlatform="";browserMovementAt=0;
            applyProfile();
        }
        browserPkg=pkg;
        ProbeStore.events++;browserRetries=0;
        if(type==AccessibilityEvent.TYPE_VIEW_SCROLLED)browserMovementAt=SystemClock.elapsedRealtime();
        ProbeStore.add(SystemClock.elapsedRealtime()+" browser event="+type+" package="+pkg);
        if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)invalidateReads();
        if(browserQueued)return;
        browserQueued=true;
        // Ordinary pages are sampled slowly; a Shorts session is sampled quickly enough to catch swipes.
        long gap=browserIdentity!=null?250:900;
        handler.postDelayed(browserTask,Math.max(0,gap-(SystemClock.elapsedRealtime()-browserReadAt)));
    }
    /** Read toolbar URLs; only a confirmed YouTube domain permits the bounded player fallback. */
    private void readBrowser() {
        browserQueued=false;
        final String pkg=browserPkg;
        if(pkg==null || destroyed || !trackingAllowed())return;
        browserReadAt=SystemClock.elapsedRealtime();
        final long epoch=readEpoch;
        final byte[] salt=ProbeStore.salt.clone();
        submitRead("browser",epoch,()-> {
            BrowserReader.Result result=new BrowserReader.Result();
            AccessibilityNodeInfo root=null;
            long started=SystemClock.elapsedRealtime();
            try {
                root=getRootInActiveWindow();
                result=BrowserReader.read(root,pkg,salt);
            }
            catch(RuntimeException error) { ServiceHealth.failure("browser read",error); }
            finally { if(root!=null)root.recycle(); }
            final BrowserReader.Result reading=result;
            final long duration=SystemClock.elapsedRealtime()-started;
            handler.post(()->guard("browser result",()-> {
                if(epoch!=readEpoch || !pkg.equals(browserPkg) || destroyed || !trackingAllowed())return;
                ProbeStore.scans++;ProbeStore.scanTimeMs+=duration;ProbeStore.maxScanMs=Math.max(ProbeStore.maxScanMs,duration);
                if(reading.cut)ProbeStore.truncated++;
                ProbeStore.signals=reading.metadata();
                ProbeStore.decision(SystemClock.elapsedRealtime()+" "+reading.metadata());
                applyBrowser(epoch,reading.state,reading.identity,reading.mode,reading.platform);
            }));
        },()->{});
    }
    private void applyBrowser(long epoch,int state,String identity,String mode) {
        applyBrowser(epoch,state,identity,mode,"YouTube");
    }
    private void applyBrowser(long epoch,int state,String identity,String mode,String platform) {
        if(epoch!=readEpoch || destroyed || !trackingAllowed())return;
        long now=SystemClock.elapsedRealtime();
        if(state==BrowserSignals.SHORTS && identity!=null && BrowserSignals.platform(platform)) {
            if(!browserPlatform.isEmpty()&&!browserPlatform.equals(platform)){
                engine.close(now,System.currentTimeMillis());ProbeStore.counter.forgetFingerprints();
                browserIdentity=null;browserMode="none";browserMovementAt=0;
            }
            browserPlatform=platform;
            if(browserIdentity!=null && !browserMode.equals(mode)) {
                // Hiding/revealing the toolbar must not turn the same item into a second exposure.
                ProbeStore.counter.aliasIdentity(browserIdentity,identity);
                engine.aliasIdentity(browserIdentity,identity);
                browserIdentity=identity;
            }
            int before=ProbeStore.counter.accepted;
            boolean title="player-title".equals(mode);
            ProbeStore.status=title?ProbeStore.counter.observe(identity,now,true,
                    browserIdentity==null || (browserMovementAt>0 && now-browserMovementAt<=2500))
                    :ProbeStore.counter.verifiedFingerprint(identity,now);
            if(ProbeStore.counter.isCommitted(identity)) {
                browserIdentity=identity;browserMode=mode;
                engine.observe(platform,identity,ProbeStore.counter.committedAt(),now,System.currentTimeMillis());
                indicatorReady=true;
            } else {engine.pause(now);hideIndicator();}
            if(ProbeStore.counter.accepted!=before)ProbeStore.transition(now+" BROWSER_ACCEPTED_TRANSITION #"+ProbeStore.counter.accepted);
            maintain(false);
            queueBrowser(500);
            return;
        }
        engine.pause(now);hideIndicator();ProbeStore.counter.suspend();
        if(state==BrowserSignals.EDITING || state==BrowserSignals.UNAVAILABLE) {
            ProbeStore.status=state==BrowserSignals.EDITING?"Browser address is being edited — paused":"Browser address unavailable — paused";
            if(state==BrowserSignals.UNAVAILABLE && browserRetries++<3)queueBrowser(700);
            return;
        }
        if(state==BrowserSignals.YOUTUBE_HOST_ONLY) {
            // A trimmed toolbar is ambiguous; preserve the last identity while timing is paused.
            ProbeStore.status=platform+" domain visible, but no accessible short-video identity";
            if(browserRetries++<3)queueBrowser(700);
            return;
        }
        if(browserIdentity!=null)engine.close(now,System.currentTimeMillis());
        ProbeStore.counter.forgetFingerprints();
        browserIdentity=null;browserMode="none";browserPlatform="";
        ProbeStore.status="Waiting for browser Shorts or Reels";
    }
    private void queueBrowser(long delay) {
        if(browserQueued || browserPkg==null || destroyed || !trackingAllowed())return;
        browserQueued=true;handler.postDelayed(browserTask,delay);
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
                instagramIdentity.reset();instagramFast.reset();instagramAliasPosition=instagramPlayerWindow=-1;pagerIndex=acceptedPagerIndex=-1;
                instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;youtubeFast.reset();
                ProbeStore.status="Waiting for Reels or Shorts";
            }));
        },()->{});
    }
    private void readSource(AccessibilityEvent event,String pkg,boolean scroll) {
        lastSourceSample=SystemClock.elapsedRealtime();
        final long arrivedAt=lastSourceSample,epoch=readEpoch;
        final int from=event.getFromIndex(),to=event.getToIndex(),eventType=event.getEventType(),changeTypes=event.getContentChangeTypes();
        final int eventWindow=event.getWindowId();
        final String eventClass=String.valueOf(event.getClassName());
        final AccessibilityEvent copy=AccessibilityEvent.obtain(event);
        String key=scroll?"scroll:"+(sourceSequence++):"source";
        submitRead(sourceReader,key,epoch,()-> {
            if(!currentRead(pkg,epoch))return;
            String id="",type=eventClass;
            boolean descendant=false;
            AccessibilityNodeInfo node=null;
            try {
                node=copy.getSource();
                if(node!=null && pkg.equals(String.valueOf(node.getPackageName()))) {
                    id=node.getViewIdResourceName();if(id==null)id="";
                    type=String.valueOf(node.getClassName());
                    if(scroll && Detectors.instagram(pkg) && !InstagramSignals.pager(id)
                            && InstagramSignals.scrollContainer(type)) {
                        String parentPager=instagramPagerAncestor(node,pkg);
                        if(!parentPager.isEmpty()){id=parentPager;descendant=true;}
                    }
                    if(id.isEmpty())type=eventClass;
                    if(scroll && Detectors.forPackage(pkg).platformName().equals("Facebook")) {
                        Rect sourceBounds=new Rect();node.getBoundsInScreen(sourceBounds);
                        if(!FeedEvidence.fullPlayer(sourceBounds.width(),sourceBounds.height(),
                                getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels))type="facebook_partial_source";
                    }
                }
            } catch(RuntimeException error){ServiceHealth.failure("event source",error);}
            finally {if(node!=null)node.recycle();}
            final String sourceId=id,sourceType=type;
            final int sourceFrom=descendant?-1:from,sourceTo=descendant?-1:to;
            handler.post(()->guard("source result",()-> {
                if(currentRead(pkg,epoch))applySource(pkg,sourceId,sourceType,sourceFrom,sourceTo,eventType,changeTypes,scroll,arrivedAt,eventWindow);
            }));
        },copy::recycle);
    }
    private String instagramPagerAncestor(AccessibilityNodeInfo source,String pkg) {
        AccessibilityNodeInfo cursor=source;
        boolean owned=false;
        long deadline=SystemClock.elapsedRealtime()+500;
        try {
            for(int depth=0;cursor!=null && depth<8 && SystemClock.elapsedRealtime()<=deadline;depth++) {
                if(!pkg.equals(String.valueOf(cursor.getPackageName())) || cursor.isEditable() || cursor.isPassword())return "";
                String id=cursor.getViewIdResourceName();
                String name=InstagramSignals.localId(id).toLowerCase(Locale.ROOT);
                if(InstagramSignals.caption(id) || InstagramSignals.author(id) || name.contains("comment")
                        || name.contains("profile") || name.contains("share_sheet") || name.contains("story"))return "";
                if(InstagramSignals.pager(id))return id;
                AccessibilityNodeInfo parent=cursor.getParent();
                if(owned)cursor.recycle();
                cursor=parent;owned=true;
            }
            return "";
        } finally {if(owned && cursor!=null)cursor.recycle();}
    }
    private void applySource(String pkg,String id,String type,int from,int to,int eventType,int changeTypes,boolean scroll,long arrivedAt) {
        applySource(pkg,id,type,from,to,eventType,changeTypes,scroll,arrivedAt,playerWindow);
    }
    private void applySource(String pkg,String id,String type,int from,int to,int eventType,int changeTypes,boolean scroll,long arrivedAt,int eventWindow) {
        ShortVideoDetector detector=Detectors.forPackage(pkg);
        if(detector==null)return;
        sourceSummary="id="+id+" class="+type+" indices="+from+":"+to;
        long now=SystemClock.elapsedRealtime();
        if(scroll) ProbeStore.scroll(now+" "+sourceSummary+" ageMs="+(now-arrivedAt));
        else ProbeStore.source(now+" "+AccessibilityEvent.eventTypeToString(eventType)+" changeTypes="+changeTypes+" "+sourceSummary);
        if(now-arrivedAt>1500) {
            ProbeStore.expiredSources++;
            ProbeStore.decision(now+" SOURCE_REJECTED reason=expired ageMs="+(now-arrivedAt));
            return;
        }
        if(id.isEmpty()) {
            ProbeStore.missingSources++;
            if(detector.platformName().equals("Snapchat")&&SnapchatSignals.movement(type))
                ProbeStore.decision(now+" SOURCE_METADATA_ONLY reason=no_view_id class_and_window_retained=true");
            else ProbeStore.decision(now+" SOURCE_REJECTED reason=no_accessible_source");
        }
        if(scroll && Detectors.instagram(pkg) && id.isEmpty() && InstagramSignals.pagerClass(type) && eventWindow>=0) {
            // Missing source handles do not erase the event's class/window metadata.
            // This authorizes repeated content confirmation, never guessed positions.
            if(eventWindow==instagramPlayerWindow && FeedEvidence.valid(instagramConfirmedAt,now)) {
                pagerIndex=-1;lastPagerAt=0;
                scrollUntil=arrivedAt+2500;
                instagramFast.movement(arrivedAt);
            } else {
                // A long view can outlast the confirmation lease. Validate this
                // movement against a fresh player frame instead of losing it.
                instagramPendingAt=arrivedAt;instagramPendingWindow=eventWindow;
            }
            schedule(180);
            ProbeStore.decision(now+" INSTAGRAM_CONTENT_RECOVERY reason=missing_pager_source");
        }
        if(scroll && detector.platformName().equals("Snapchat") && SnapchatSignals.movement(type)) {
            snapchatMovementAt=arrivedAt;snapchatMovementWindow=eventWindow;
            schedule(180); // Missing resource IDs/indices are not a reason to discard class/window metadata.
        }
        if(scroll && detector.platformName().equals("Facebook")) {
            int index=FacebookSignals.position(type,from,to);
            // The source alone never authorizes counting. A fresh matching full-player
            // snapshot with a vertical action rail must confirm this exact window/source.
            facebookSource=id;facebookIndex=index;facebookWindow=eventWindow;facebookAt=arrivedAt;
            if(index>=0 && !id.isEmpty()) { scrollUntil=arrivedAt+5000;schedule(180); }
        }
        if(scroll && !detector.usesPosition() && detector.movement(id,type,from,to)) {
            scrollUntil=arrivedAt+5000;
            youtubeFast.movement(arrivedAt);
            quickAttempts=0;
            if(FeedEvidence.valid(youtubeConfirmedAt,now)) queueYouTubeRead(20);
        }
        if(scroll && detector.usesPosition() && InstagramSignals.pager(id)) {
            instagramPendingAt=-1;instagramPendingWindow=-1;
            scrollUntil=arrivedAt+2500;
            instagramFast.movement(arrivedAt);
            if(InstagramSignals.pagerScroll(id,type,from,to)) {
                if(pagerIndex!=from || lastPagerAt==0) { lastPagerAt=arrivedAt; }
                pagerIndex=from;
                if(!instagramIdentity.labels() && FeedEvidence.valid(instagramConfirmedAt,now)) {
                    commitInstagramPosition(lastPagerAt,now);
                } else {
                    schedule(180);
                }
            } else {
                pagerIndex=-1;
                lastPagerAt=0;
                // Exact player movement can still confirm a stable caption change when this
                // layout supplies no unambiguous index. Unknown sources never authorize it.
                schedule(180);
            }
        }
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
        instagramIdentity.reset();instagramFast.reset();instagramAliasPosition=instagramPlayerWindow=-1;pagerIndex=acceptedPagerIndex=-1;
        instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;youtubeFast.reset();
        ProbeStore.status=reason;
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
        final int index=Detectors.instagram(pkg)?instagramIdentity.scanIndex(pagerIndex):pagerIndex,window=playerWindow;
        final Snapshot snapshot=new Snapshot(pkg,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
        snapshot.facebookSource=facebookSource;snapshot.facebookIndex=facebookIndex;
        snapshot.facebookAt=facebookAt;snapshot.facebookWindow=facebookWindow;
        submitRead("scan",epoch,()-> {
            if(!currentRead(pkg,epoch))return;
            ScanResult result=readSnapshot(snapshot,index,window,false);
            handler.post(()->guard("scan result",()-> {
                if(!currentRead(pkg,epoch))return;
                ProbeStore.readerDropped=reader.dropped()+sourceReader.dropped();
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
        int index,window=-1;long cost,at;boolean exited;
    }
    private ScanResult readSnapshot(Snapshot snapshot,int index,int window,boolean footerOnly) {
        ScanResult result=new ScanResult();result.index=index;
        long started=SystemClock.elapsedRealtime();
        boolean indexedStructure=false;

        AccessibilityNodeInfo root=null;
        try {
            root=readPlayerRoot(snapshot.pkg,window,result);
            if(root==null)return result;
            result.window=root.getWindowId();
            root.getBoundsInScreen(result.bounds);
            snapshot.deadline=SystemClock.elapsedRealtime()+1000;
            if(footerOnly)collectVisibleYouTubeFooter(root,snapshot);
            else {
                snapshot.walk(root,0);
                if(Detectors.youtube(snapshot.pkg))snapshot.feed|=YouTubeSignals.confirmedContainer(snapshot.ytRecycler,snapshot.ytPage);
                snapshot.snapchatConfirmed=snapshot.platform.equals("Snapchat") && !snapshot.overlay && (snapshot.snapchatGeometryBounds!=null
                        || (snapshot.snapchatSpotlight && snapshot.snapchatPager && snapshot.snapchatFavorite && snapshot.snapchatPlayerWithFavorite));
                snapshot.feed|=snapshot.facebookPlayer||snapshot.snapchatConfirmed;
                boolean structure=Detectors.youtube(snapshot.pkg)?snapshot.ytRecycler&&snapshot.ytPage:
                        Detectors.instagram(snapshot.pkg)?InstagramSignals.playerStructure(snapshot.igPager,snapshot.igVideo,snapshot.igFullVideo,snapshot.feed,snapshot.like,snapshot.share):snapshot.platform.equals("Snapchat")?snapshot.snapchatConfirmed:snapshot.feed&&snapshot.like&&snapshot.share;
                indexedStructure=Detectors.instagram(snapshot.pkg) && index>=0 && structure
                        && !snapshot.overlay && !snapshot.cut && SystemClock.elapsedRealtime()<=snapshot.deadline;
                if(structure && !snapshot.overlay && !snapshot.cut) {
                    if(Detectors.youtube(snapshot.pkg))collectVisibleYouTubeFooter(root,snapshot);
                    else if(snapshot.platform.equals("Snapchat"))snapshot.collectSnapchat(root,0,new int[]{0},false);
                    else if(!snapshot.platform.equals("Facebook"))snapshot.collect(root,0,new int[]{0},false);
                }
            }
            if(SystemClock.elapsedRealtime()>snapshot.deadline)snapshot.cut=true;
            if(indexedStructure && snapshot.cut) {
                // Content is an optional recovery alias when a verified position exists.
                // Exhausting its budget cannot revoke an already complete structural read.
                snapshot.fields=snapshot.captionFields=0;
                snapshot.cut=false;
            }
            result.snapshot=snapshot;result.hash=snapshot.finish();
        } catch(RuntimeException error){ServiceHealth.failure("background screen read",error);}
        finally {if(root!=null)root.recycle();result.at=SystemClock.elapsedRealtime();result.cost=result.at-started;}
        return result;
    }
    /** Only package/window metadata is read until the exact target package is verified. */
    private AccessibilityNodeInfo readPlayerRoot(String pkg,int expectedWindow,ScanResult result) {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        boolean otherForeground=false;
        if(root!=null) {
            if(pkg.equals(String.valueOf(root.getPackageName())))return root;
            boolean ownBadge=getPackageName().equals(String.valueOf(root.getPackageName())) && "android.widget.TextView".contentEquals(String.valueOf(root.getClassName()));
            root.recycle();
            // Our display-only overlay can mask the active root on some OEMs.
            if(!ownBadge)otherForeground=true;
        }
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows=getWindows();
        try {
            for(android.view.accessibility.AccessibilityWindowInfo window:windows) {
                if(window.getType()!=android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                // Normally require the active/focused window. When another package owns the active
                // root (floating sidebar, assistant, chat bubble), accept the target's own visible
                // application window instead of treating the overlay as leaving the feed. Geometry
                // checks later reject small, split-screen and floating windows.
                if(!otherForeground && !(window.isActive() || window.isFocused()))continue;
                if(expectedWindow>=0 && window.getId()!=expectedWindow)continue;
                AccessibilityNodeInfo candidate=window.getRoot();
                if(candidate==null)continue;
                if(pkg.equals(String.valueOf(candidate.getPackageName())))return candidate;
                candidate.recycle();
            }
        } finally {for(android.view.accessibility.AccessibilityWindowInfo window:windows)window.recycle();}
        // Another app really is in front and the target has no visible window: left the feed.
        if(otherForeground)result.exited=true;
        return null;
    }
    private void applyScan(ScanResult result) {
        boolean instagramFrame=Detectors.instagram(ProbeStore.target);
        int position=instagramFrame?instagramIdentity.scanIndex(pagerIndex):pagerIndex;
        if(instagramFrame && !InstagramSignals.freshFrame(result.index,position,lastPagerAt,SystemClock.elapsedRealtime())) {
            ProbeStore.decision(SystemClock.elapsedRealtime()+" FRAME_REJECTED capturedIndex="+result.index+" currentIndex="+position);
            schedule(180);
            return;
        }
        if(!instagramFrame && result.index!=pagerIndex) {
            schedule(180);
            return;
        }
        if(!screenAvailable()){engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());suspend("Screen locked/off");return;}
        Snapshot s=result.snapshot;Rect rootBounds=result.bounds;
        boolean facebook=s.platform.equals("Facebook");
        if(facebook && (s.facebookAt!=facebookAt || s.facebookIndex!=facebookIndex)) {
            schedule(180);return;
        }
        String facebookIdentity="facebook:"+s.facebookSource+":"+s.facebookIndex;
        boolean facebookPosition=facebook && s.facebookPlayer && s.facebookIndex>=0
                && s.facebookWindow==result.window && s.facebookAt>0
                && (SystemClock.elapsedRealtime()-s.facebookAt<=5000 || ProbeStore.counter.isCommitted(facebookIdentity));
        boolean youtube=Detectors.youtube(ProbeStore.target);
        boolean playerStructure=youtube?s.ytRecycler&&s.ytPage:
                instagramFrame?InstagramSignals.playerStructure(s.igPager,s.igVideo,s.igFullVideo,s.feed,s.like,s.share):s.platform.equals("Snapchat")?s.snapchatConfirmed:s.feed&&s.like&&s.share;
        if(ProbeStore.enabled && s.platform.equals("Snapchat") && s.snapchatLayout.length()>0)
            ProbeStore.retainSnapchat(s.snapchatLayout.toString(),s.nodes+(s.snapchatConfirmed?10000:0));
        ProbeStore.scans++;
        if(s.cut) ProbeStore.truncated++;
        ProbeStore.tree=s.structure.toString();

        String snapshotInfo="at="+SystemClock.elapsedRealtime()+" bounds="+rootBounds.flattenToString()+" nodes="+s.nodes+" truncated="+s.cut+ " feed="+s.feed+" instagramPager="+s.igPager+" instagramVideo="+s.igVideo+" youtubeRecycler="+s.ytRecycler+" youtubePage="+s.ytPage+" like="+s.like+" share="+s.share+" contentFields="+s.fields+ " expandedHierarchy="+Detectors.youtube(ProbeStore.target);
        long area=(long)Math.max(0,rootBounds.width())*Math.max(0,rootBounds.height());
        long screenArea=(long)getResources().getDisplayMetrics().widthPixels*getResources().getDisplayMetrics().heightPixels;
        long score=(screenArea>0 ? Math.min(1000000,area*1000000/screenArea) : 0)+s.nodes;
        ProbeStore.retainTree(s.structure.toString(),snapshotInfo,score,s.feed);
        ProbeStore.signals="rootBounds="+rootBounds.flattenToString()+", youtubeRecycler="+s.ytRecycler+", youtubePage="+s.ytPage+", feedMarker="+s.feed+", instagramPager="+s.igPager+", instagramVideo="+s.igVideo+", instagramFullVideo="+s.igFullVideo+", like="+s.like+", share="+s.share+", overlay="+s.overlay+", overlayReasons="+s.overlayReasons+", sheetCandidates="+s.sheetCandidates+ ", snapchatGeometry="+(s.snapchatGeometryBounds!=null)+", snapchatRails="+s.snapchatRails+", snapchatAuthors="+s.snapchatAuthors+", snapchatFooterLeaves="+s.snapchatFooters+", snapchatSpotlight="+s.snapchatSpotlight+", snapchatPager="+s.snapchatPager+", snapchatFavorite="+s.snapchatFavorite+", snapchatConfirmed="+s.snapchatConfirmed+", facebookPlayer="+s.facebookPlayer+", facebookIndex="+s.facebookIndex+", captionFields="+s.captionFields+", pagerIndex="+pagerIndex+", effectiveIndex="+position+", labelIdentity="+instagramIdentity.labels()+", lastScrollSource="+sourceSummary+", contentFields="+s.fields+", nodes="+s.nodes+", truncated="+s.cut;
        boolean fullPlayer=FeedEvidence.fullPlayer(rootBounds.width(),rootBounds.height(),getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
        if(instagramFrame && instagramPendingAt>=0) {
            long now=SystemClock.elapsedRealtime();
            boolean expired=now-instagramPendingAt>FastFingerprintTracker.MOVEMENT_TTL_MS;
            if(expired || s.overlay || s.profile || (!s.cut && !s.feed)) {
                instagramPendingAt=-1;instagramPendingWindow=-1;
            } else if(playerStructure && (s.igFullVideo || (s.igPager && s.igVideo)) && !s.cut && fullPlayer && s.fields>0
                    && result.window==instagramPendingWindow && result.at>=instagramPendingAt) {
                position=pagerIndex=-1;lastPagerAt=0;
                scrollUntil=instagramPendingAt+2500;
                instagramFast.movement(instagramPendingAt);
                instagramPendingAt=-1;instagramPendingWindow=-1;
            }
        }
        boolean eligible=playerStructure && !s.overlay && !s.profile && !s.cut && (facebook ? facebookPosition : FeedEvidence.identityAvailable(s.fields,Detectors.instagram(ProbeStore.target),position)) && fullPlayer;
        indicatorReady=playerStructure && !s.overlay && !s.profile && !s.cut && fullPlayer;
        String hash=result.hash;
        ProbeStore.fingerprint=ProbeStore.enabled?hash:"";
        int before=ProbeStore.counter.accepted;
        ShortVideoDetector detector=Detectors.forPackage(ProbeStore.target);
        boolean instagram=detector.usesPosition();
        // Exact player-pager position distinguishes identical captions and cannot change on a like.
        String identity=facebook ? facebookIdentity : instagram && position>=0 ? "pager:"+position : hash;
        boolean movement=instagram && position>=0 ? (position!=acceptedPagerIndex && SystemClock.elapsedRealtime()<=scrollUntil) : SystemClock.elapsedRealtime()<=scrollUntil;
        long observationTime=SystemClock.elapsedRealtime();
        if(s.platform.equals("Snapchat"))movement=snapchatMovementAt>0 && observationTime>=snapchatMovementAt
                && observationTime-snapchatMovementAt<=4000 && result.at>=snapchatMovementAt && snapchatMovementWindow==result.window;
        if(instagram) {
            instagramConfirmedAt=FeedEvidence.update(instagramConfirmedAt,observationTime,eligible,s.feed,s.overlay||s.profile,!s.cut,fullPlayer);
            if(eligible)instagramPlayerWindow=result.window;
        }
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
        if(!eligible) ProbeStore.decision(observationTime+" SCAN_NOT_CONFIRMED playerStructure="+playerStructure+" instagramPager="+s.igPager+" instagramVideo="+s.igVideo+" instagramFullVideo="+s.igFullVideo+" feed="+s.feed+" overlay="+s.overlay+" overlayReasons="+s.overlayReasons+" sheetCandidates="+s.sheetCandidates+" truncated="+s.cut+" fullPlayer="+fullPlayer+" like="+s.like+" share="+s.share+" youtubeRecycler="+s.ytRecycler+" youtubePage="+s.ytPage+" fields="+s.fields);
        if(instagram && eligible && position>=0) {
            instagramConfirmedAt=observationTime;
            boolean unchangedPosition=ProbeStore.counter.isCommitted(identity);
            if(unchangedPosition && instagramAliasPosition==position && !hash.isEmpty()
                    && instagramFast.observe(hash,observationTime,true)) {
                // Some pagers recycle position zero. A repeated index cannot veto a
                // distinct stable item after verified player movement.
                ProbeStore.status=ProbeStore.counter.verifiedFingerprint(hash,instagramFast.acceptedAt());
                instagramIdentity.fallback();
                identity=hash;
                instagramAliasPosition=-1;
            } else {
                long positionTime=lastPagerAt>0 && observationTime-lastPagerAt<=2500?lastPagerAt:observationTime;
                ProbeStore.status=ProbeStore.counter.verifiedPosition(position,positionTime,true);
            }
        }
        else if(instagram && eligible && position<0 && ProbeStore.counter.hasBaseline()) {
            if(ProbeStore.counter.isCommitted(hash)) {
                instagramFast.seed(hash);
                ProbeStore.status="Stable Reel";
            } else if(instagramFast.observe(hash,observationTime,true)) {
                ProbeStore.status=ProbeStore.counter.verifiedFingerprint(hash,instagramFast.acceptedAt());
                instagramIdentity.fallback();
            } else {
                ProbeStore.status="Waiting for a confirmed Reel change";
            }
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
        if(s.platform.equals("Snapchat") && (ProbeStore.counter.accepted!=before||ProbeStore.status.startsWith("Baseline acquired")))snapchatMovementAt=0;
        if(ProbeStore.counter.accepted!=before) ProbeStore.transition(SystemClock.elapsedRealtime()+" ACCEPTED_TRANSITION #"+ProbeStore.counter.accepted);
        if(eligible && ProbeStore.counter.isCommitted(identity)) {
            if(instagram) {
                instagramIdentity.baseline(position);
                // Retain a content alias if subsequent swipes lose their position metadata.
                if(!hash.isEmpty() && (position<0 || instagramIdentity.labels())) instagramFast.seed(hash);
                else if(!hash.isEmpty() && instagramAliasPosition!=position) {
                    instagramFast.seed(hash);
                    instagramFast.pause(); // This exact position was already counted.
                    instagramAliasPosition=position;
                }
            }
            engine.observe(detector.platformName(),identity,ProbeStore.counter.committedAt(),SystemClock.elapsedRealtime(),System.currentTimeMillis());
            if(instagram && instagramIdentity.labels() && pagerIndex>=0 && hash.equals(identity)) {
                String positionAlias="pager:"+pagerIndex;
                if(engine.aliasIdentity(hash,positionAlias) && ProbeStore.counter.aliasIdentity(hash,positionAlias)) {
                    instagramIdentity.position();
                    instagramAliasPosition=acceptedPagerIndex=pagerIndex;
                    instagramFast.seed(hash);instagramFast.pause();
                }
            }
        }
        else if(!eligible) {
            if(!s.cut && (s.profile || (!s.feed && !s.overlay))) {
                engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
                ProbeStore.counter.forgetFingerprints();
                instagramIdentity.reset();instagramFast.reset();instagramAliasPosition=instagramPlayerWindow=-1;pagerIndex=acceptedPagerIndex=-1;
                instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;
                youtubeFast.reset();
            }
            else {
                engine.pause(SystemClock.elapsedRealtime());
                instagramFast.pause();
                ProbeStore.decision(observationTime+" OBSERVATION_PAUSED baselineRetained="+ProbeStore.counter.hasBaseline()+" incomplete="+s.cut);
            }
        }
        if(!eligible) ProbeStore.status="Unconfirmed feed / overlay / unavailable content signal — no count";
        // Finite confirmation reads, not a permanent polling loop.
        if(eligible && verification++<2) schedule(TransitionProbe.STABLE_MS+50);
        else if(!s.overlay && !s.profile && (s.feed || s.cut) && unavailableRetries++<3) {
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
        instagramIdentity.baseline(pagerIndex);
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
        if(detector==null && !(browserPkg!=null && browserIdentity!=null
                && browserPkg.equals(ProbeStore.target))) { indicator.hide(); return; }
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
        instagramFast.pause();
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
        destroyed=true;invalidateReads();reader.close();sourceReader.close();
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
        int nodes,visitedNodes,fields,captionFields,playerMarkers;
        String overlayReasons="",sheetCandidates="";
        String facebookSource="";
        int facebookIndex=-1,facebookWindow=-1;
        long facebookAt;
        boolean facebookPlayer;
        boolean snapchatSpotlight,snapchatPager,snapchatFavorite,snapchatConfirmed,snapchatPlayerWithFavorite;
        Rect snapchatGeometryBounds;
        int snapchatImages,snapchatAuthors,snapchatFooters,snapchatRails,snapchatBackdrops;
        final java.util.List<Rect> snapchatRows=new java.util.ArrayList<>();
        final java.util.List<Rect> snapchatFavorites=new java.util.ArrayList<>();
        final StringBuilder snapchatLayout=new StringBuilder();
        final java.util.List<Rect> facebookLikes=new java.util.ArrayList<>(),facebookShares=new java.util.ArrayList<>();
        final int maxNodes,maxDepth,screenWidth,screenHeight;
        final boolean verbose;
        long deadline;
        boolean feed,like,share,overlay,cut,ytRecycler,ytPage,profile,igPager,igVideo,igFullVideo;
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
            String type=String.valueOf(n.getClassName());
            // Hidden structural wrappers may be traversed; protected subtrees may not.
            if(n.isPassword() || n.isEditable() || type.contains("EditText")) {
                if(n.isVisibleToUser() && (!Detectors.instagram(pkg) || n.isPassword() || n.isFocused()))
                    blockOverlay(n.isPassword()?"password_input":"focused_input");
                return;
            }
            if(!n.isVisibleToUser()) {
                int visibleBefore=nodes;
                for(int i=0;i<n.getChildCount() && nodes<maxNodes;i++) {
                    AccessibilityNodeInfo child=n.getChild(i);
                    if(child!=null)try { walk(child,depth+1); } finally { child.recycle(); }
                }
                if(nodes>=maxNodes)cut=true;
                if(Detectors.instagram(pkg) && nodes>visibleBefore && !cut) {
                    String marker=n.getViewIdResourceName();
                    Rect bounds=new Rect();n.getBoundsInScreen(bounds);
                    if(visiblePlayerBounds(bounds)) {
                        boolean pager=InstagramSignals.pager(marker),video=InstagramSignals.video(marker);
                        igPager|=pager;igVideo|=video;igFullVideo|=video;feed|=pager||video;
                        if(pager || video)playerMarkers++;
                    }
                }
                return;
            }
            if(nodes>=maxNodes){cut=true;return;}
            nodes++;
            String id=n.getViewIdResourceName();
            id=id==null?"":id;
            String lowerId=id.toLowerCase(Locale.ROOT);
            boolean instagram=Detectors.instagram(pkg);
            boolean opener=instagram && (lowerId.endsWith("_button") || lowerId.endsWith("_icon")
                    || lowerId.endsWith("_button_container") || type.endsWith("Button") || type.endsWith("ImageView"));
            boolean genericSheet=lowerId.contains("bottom_sheet") && !opener;
            int markersBefore=playerMarkers,ownNodeCount=nodes;
            if(verbose) {
                Rect bounds=new Rect();
                n.getBoundsInScreen(bounds);
                for(int j=0;j<Math.min(depth,maxDepth);j++) structure.append(" ");
                structure.append(type).append(" id=").append(id) .append(" bounds=").append(bounds.flattenToString()).append(" children=").append(n.getChildCount()) .append(" selected=").append(n.isSelected()).append(" clickable=").append(n.isClickable()) .append(" text=").append(n.getText()==null?"none":"[redacted]") .append(" description=").append(n.getContentDescription()==null?"none":"[redacted]").append('\n');
            }
            boolean snapchat=platform.equals("Snapchat");
            int snapchatFavoritesBefore=snapchatFavorites.size();
            int snapRowsBefore=snapchatRows.size(),snapImagesBefore=snapchatImages,snapAuthorsBefore=snapchatAuthors,
                    snapFootersBefore=snapchatFooters,snapRailsBefore=snapchatRails,snapBackdropsBefore=snapchatBackdrops;
            int facebookLikesBefore=facebookLikes.size(),facebookSharesBefore=facebookShares.size();
            boolean control=type.endsWith("Button") || type.endsWith("ImageView") || n.isSelected() || n.isClickable();
            Rect nodeBounds=new Rect();n.getBoundsInScreen(nodeBounds);
            boolean snapchatUi=snapchat && ((nodeBounds.top>=0&&nodeBounds.bottom<=screenHeight/5)
                    || (n.isSelected()&&nodeBounds.top>=screenHeight*4L/5)
                    || ((type.endsWith("Button")||type.endsWith("ImageView")||(n.isClickable()&&!type.endsWith("TextView")))
                        &&FacebookSignals.railControl(nodeBounds.left,nodeBounds.top,nodeBounds.right,nodeBounds.bottom,screenWidth,screenHeight)));
            boolean readControl=snapchat?snapchatUi:control;
            CharSequence rawText=readControl?n.getText():null,rawDesc=readControl?n.getContentDescription():null;
            String text=control?String.valueOf(rawText).toLowerCase(Locale.ROOT):"";
            String desc=control?String.valueOf(rawDesc).toLowerCase(Locale.ROOT):"";
            String controls=lowerId+" "+text+" "+desc;
            if(snapchat){
                if(type.endsWith("ImageView") && nodeBounds.left>=screenWidth*4L/5)snapchatImages++;
                if(type.endsWith("TextView") && n.getChildCount()==0
                        && SnapchatSignals.footer(nodeBounds.left,nodeBounds.top,nodeBounds.right,nodeBounds.bottom,screenWidth,screenHeight)){
                    snapchatFooters++;
                    if(n.isClickable()&&SnapchatSignals.author(nodeBounds.left,nodeBounds.top,nodeBounds.right,nodeBounds.bottom,screenWidth,screenHeight))snapchatAuthors++;
                }
                if(n.getChildCount()==0 && (type.equals("android.widget.FrameLayout")||type.equals("android.view.View"))&&visiblePlayerBounds(nodeBounds))snapchatBackdrops++;
                // Read only to recognise fixed UI vocabulary; never export these labels.
                String st=String.valueOf(rawText),sd=String.valueOf(rawDesc);
                snapchatSpotlight|=SnapchatSignals.spotlight(st,n.isSelected(),n.isClickable(),nodeBounds.top,screenHeight)
                        || SnapchatSignals.spotlight(sd,n.isSelected(),n.isClickable(),nodeBounds.top,screenHeight)
                        || (SocialSignals.player(id,platform)&&visiblePlayerBounds(nodeBounds));
                boolean pager=(FacebookSignals.recycler(type)||InstagramSignals.pagerClass(type)||n.isScrollable())&&visiblePlayerBounds(nodeBounds);
                snapchatPager|=pager;
                if(FacebookSignals.recycler(type)&&!pager&&nodeBounds.width()>=screenWidth*85L/100
                        &&nodeBounds.height()>=screenHeight*30L/100)blockOverlay("snapchat_list_panel");
                boolean favorite=(SnapchatSignals.favorite(st)||SnapchatSignals.favorite(sd))
                        && FacebookSignals.railControl(nodeBounds.left,nodeBounds.top,nodeBounds.right,nodeBounds.bottom,screenWidth,screenHeight);
                snapchatFavorite|=favorite;if(favorite)snapchatFavorites.add(new Rect(nodeBounds));
                if(ProbeStore.enabled && snapchatLayout.length()<24000){
                    snapchatLayout.append("depth=").append(depth).append(" type=").append(type).append(" bounds=").append(nodeBounds.flattenToString())
                            .append(" selected=").append(n.isSelected()).append(" clickable=").append(n.isClickable()).append(" scrollable=").append(n.isScrollable())
                            .append(" children=").append(n.getChildCount()).append(" labelsSampled=").append(readControl).append(" textPresent=").append(rawText!=null)
                            .append(" descriptionPresent=").append(rawDesc!=null).append(" spotlight=")
                            .append(SnapchatSignals.label(st).equals("spotlight")||SnapchatSignals.label(sd).equals("spotlight"))
                            .append(" favorite=").append(favorite).append(" pager=").append(pager).append('\n');
                }
            }
            boolean socialPlayer=SocialSignals.player(id,platform) && FeedEvidence.fullPlayer(nodeBounds.width(),nodeBounds.height(),screenWidth,screenHeight);
            feed |= (Detectors.instagram(pkg) ? InstagramSignals.player(id) : Detectors.youtube(pkg) ? lowerId.contains("shorts") : socialPlayer) || ((Detectors.instagram(pkg) || Detectors.youtube(pkg)) && (n.isSelected() || !n.isClickable()) && (text.equals("reels") || text.equals("shorts") || text.equals("spotlight") || desc.equals("reels") || desc.equals("spotlight")));
            igPager |= Detectors.instagram(pkg) && InstagramSignals.pager(id) && visiblePlayerBounds(nodeBounds);
            igVideo |= Detectors.instagram(pkg) && InstagramSignals.video(id);
            igFullVideo |= Detectors.instagram(pkg) && InstagramSignals.video(id) && visiblePlayerBounds(nodeBounds);
            if(instagram && InstagramSignals.player(id) && visiblePlayerBounds(nodeBounds))playerMarkers++;
            ytRecycler |= Detectors.youtube(pkg) && YouTubeSignals.recycler(id);
            ytPage |= Detectors.youtube(pkg) && YouTubeSignals.page(id);
            like |= controls.contains("like") || controls.contains("पसंद") || controls.contains("favorite") || controls.contains("favourite");
            share |= controls.contains("share") || controls.contains("send") || controls.contains("शेयर");
            if(platform.equals("Facebook") && control && FacebookSignals.railControl(nodeBounds.left,nodeBounds.top,nodeBounds.right,nodeBounds.bottom,screenWidth,screenHeight)) {
                if(controls.contains("like") || controls.contains("पसंद"))facebookLikes.add(new Rect(nodeBounds));
                if(controls.contains("share") || controls.contains("शेयर"))facebookShares.add(new Rect(nodeBounds));
            }
            profile |= lowerId.contains("profile_header");
            if(!instagram) {
                if(lowerId.contains("comment_sheet") || lowerId.contains("comments_panel") || lowerId.contains("bottom_sheet")
                        || lowerId.contains("profile_header") || lowerId.contains("share_sheet") || lowerId.contains("chat_input") || lowerId.contains("story_viewer"))blockOverlay("panel");
            } else if(!opener) {
                if(lowerId.contains("comment_sheet") || lowerId.contains("comments_panel"))blockOverlay("comments_panel");
                if(lowerId.contains("share_sheet"))blockOverlay("share_panel");
                if(lowerId.contains("profile_header"))blockOverlay("profile");
                if(lowerId.contains("story_viewer"))blockOverlay("story");
            }
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
            if(snapchat){
                if(type.equals("android.view.ViewGroup") && n.isClickable() && n.getChildCount()>=2 && snapchatImages>snapImagesBefore
                        && SnapchatSignals.actionRow(nodeBounds.left,nodeBounds.top,nodeBounds.right,nodeBounds.bottom,screenWidth,screenHeight))snapchatRows.add(new Rect(nodeBounds));
                if(type.equals("android.widget.LinearLayout") && SnapchatSignals.rail(nodeBounds.left,nodeBounds.top,nodeBounds.right,nodeBounds.bottom,screenWidth,screenHeight)){
                    java.util.TreeSet<Integer> tops=new java.util.TreeSet<>();
                    for(int i=snapRowsBefore;i<snapchatRows.size();i++)tops.add(snapchatRows.get(i).top);
                    int distinct=0,last=-screenHeight;for(int top:tops)if(top-last>=screenHeight/25){distinct++;last=top;}
                    if(distinct>=4)snapchatRails++;
                }
                // Snapchat 14.29's observed non-scrollable ViewGroup: video backdrop,
                // lower-left author/text and a tall rail with >=4 distinct action rows.
                if(type.equals("android.view.ViewGroup")&&visiblePlayerBounds(nodeBounds)
                        &&snapchatRails>snapRailsBefore&&snapchatAuthors>snapAuthorsBefore
                        &&snapchatFooters-snapFootersBefore>=2&&snapchatBackdrops>snapBackdropsBefore){
                    if(snapchatGeometryBounds==null || (long)nodeBounds.width()*nodeBounds.height()<(long)snapchatGeometryBounds.width()*snapchatGeometryBounds.height())snapchatGeometryBounds=new Rect(nodeBounds);
                }
            }
            if(snapchat && (FacebookSignals.recycler(type)||InstagramSignals.pagerClass(type)||n.isScrollable()) && visiblePlayerBounds(nodeBounds)) {
                snapchatPager=true;
                snapchatPlayerWithFavorite|=snapchatFavorites.size()>snapchatFavoritesBefore;
            }
            if(platform.equals("Facebook") && !facebookSource.isEmpty() && facebookSource.equals(id)
                    && FacebookSignals.recycler(type) && visiblePlayerBounds(nodeBounds)) {
                for(int a=facebookLikesBefore;a<facebookLikes.size();a++) {
                    Rect l=facebookLikes.get(a);
                    for(int b=facebookSharesBefore;b<facebookShares.size();b++) {
                        Rect r=facebookShares.get(b);
                        if(FacebookSignals.verticalRail(l.centerX(),l.centerY(),r.centerX(),r.centerY(),screenWidth,screenHeight))facebookPlayer=true;
                    }
                }
            }
            // Generic sheet hosts also occur as empty placeholders and small sibling
            // controls. A name alone cannot establish that a modal panel is open.
            if(instagram && genericSheet) {
                int visibleChildren=nodes-ownNodeCount;
                boolean panel=playerMarkers==markersBefore && visibleChildren>0 && panelBounds(nodeBounds);
                if(panel)blockOverlay("visible_sheet_panel");
                if(ProbeStore.enabled && sheetCandidates.length()<500)
                    sheetCandidates+=(sheetCandidates.isEmpty()?"":";")+InstagramSignals.localId(id)
                            +":"+nodeBounds.flattenToString()+" children="+visibleChildren+" panel="+panel;
            }
        }
        private void blockOverlay(String reason) {
            overlay=true;
            if(!overlayReasons.contains(reason) && overlayReasons.length()<160)
                overlayReasons+=(overlayReasons.isEmpty()?"":",")+reason;
        }
        private boolean panelBounds(Rect bounds) {
            int width=Math.max(0,Math.min(screenWidth,bounds.right)-Math.max(0,bounds.left));
            int height=Math.max(0,Math.min(screenHeight,bounds.bottom)-Math.max(0,bounds.top));
            return (long)width*100>=screenWidth*85L && (long)height*100>=screenHeight*30L;
        }
        private boolean visiblePlayerBounds(Rect bounds) {
            int width=Math.max(0,Math.min(screenWidth,bounds.right)-Math.max(0,bounds.left));
            int height=Math.max(0,Math.min(screenHeight,bounds.bottom)-Math.max(0,bounds.top));
            return FeedEvidence.fullPlayer(width,height,screenWidth,screenHeight);
        }
        void collectSnapchat(AccessibilityNodeInfo n,int depth,int[] visited,boolean inPlayer){
            if(SystemClock.elapsedRealtime()>deadline||visited[0]++>=maxNodes||depth>maxDepth){cut=true;return;}
            String type=String.valueOf(n.getClassName());
            if(n.isPassword()||n.isEditable()||type.contains("EditText"))return;
            Rect bounds=new Rect();n.getBoundsInScreen(bounds);
            inPlayer|=((FacebookSignals.recycler(type)||InstagramSignals.pagerClass(type)||n.isScrollable())&&visiblePlayerBounds(bounds))
                    || (snapchatGeometryBounds!=null && type.equals("android.view.ViewGroup") && bounds.left==snapchatGeometryBounds.left
                        &&bounds.top==snapchatGeometryBounds.top&&bounds.right==snapchatGeometryBounds.right&&bounds.bottom==snapchatGeometryBounds.bottom);
            if(inPlayer && n.isVisibleToUser() && n.getChildCount()==0 && type.endsWith("TextView")
                    && SnapchatSignals.footer(bounds.left,bounds.top,bounds.right,bounds.bottom,screenWidth,screenHeight)){
                CharSequence raw=n.getText();if(raw==null||raw.toString().trim().isEmpty())raw=n.getContentDescription();
                String value=raw==null?"":raw.toString().trim();
                if(SnapchatSignals.content(value)){socialLabels.add(value);fields++;captionFields++;}
            }
            for(int i=0;i<n.getChildCount();i++){
                AccessibilityNodeInfo child=n.getChild(i);if(child!=null)try{collectSnapchat(child,depth+1,visited,inPlayer);}finally{child.recycle();}
                if(cut)break;
            }
        }
        void collect(AccessibilityNodeInfo n,int depth,int[] visited,boolean inCaption) {
            if(SystemClock.elapsedRealtime()>deadline){cut=true;return;}
            if(visited[0]++>=maxNodes || depth>maxDepth){cut=true;return;}
            if(n.isPassword() || n.isEditable() || String.valueOf(n.getClassName()).contains("EditText"))return;
            String id=n.getViewIdResourceName();
            id=id==null?"":id;
            String lowerId=id.toLowerCase(Locale.ROOT);
            inCaption |= Detectors.instagram(pkg) ? InstagramSignals.caption(id) : SocialSignals.content(id);
            // Caption content is exposed on anonymous descendants in this Instagram version.
            boolean contentId=Detectors.instagram(pkg) ? (InstagramSignals.author(id) || inCaption) :inCaption;
            if(contentId && n.isVisibleToUser()) {
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
                if(visited[0]>=maxNodes){cut=true;break;}
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
            if(visited[0]++>=maxNodes || depth>maxDepth){cut=true;return;}
            if(n.isPassword() || n.isEditable() || String.valueOf(n.getClassName()).contains("EditText"))return;
            String id=n.getViewIdResourceName();
            inFooter |= YouTubeSignals.footer(id);
            if(inFooter && n.isVisibleToUser() && YouTubeSignals.overlayRoot(id)) {
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
