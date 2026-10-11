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
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceListener=(prefs,key)-> {
        if("liveBadge".equals(key)) {
            if(!prefs.getBoolean("liveBadge",false)) { if(indicator!=null)indicator.hide(); }
            else refreshIndicator();
        }
        if("diagnostics".equals(key)) ProbeStore.setDiagnostics(prefs.getBoolean("diagnostics",false),(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        if("running".equals(key) || "consentVersion".equals(key)) {
            if(!trackingAllowed()) {
                ProbeService.this.handler.removeCallbacks(ProbeService.this.scanTask);
                queued=false;
                if(engine!=null) engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
                hideIndicator();
                ProbeStore.reset("");
                applyProfile();
            }
        }
    };
    private final android.content.BroadcastReceiver screenReceiver=new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context,android.content.Intent intent) {
            if(!android.content.Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                indicatorDay=-1;
                refreshIndicator();
                return;
            }
            if(engine!=null) engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
            hideIndicator();
            ProbeStore.counter.forgetFingerprints();
            instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;
            youtubeFast.reset();
        }
    };
    static void stopForReset() {
        ProbeService service=activeService;
        if(service==null)return;
        service.resettingHistory=true;
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
    private boolean resettingHistory;
    private boolean trackingAllowed() {
        return !resettingHistory && preferences!=null && preferences.getBoolean("running",false) && preferences.getInt("consentVersion",0)>=2;
    }
    @Override public void onCreate() {
        super.onCreate();
        activeService=this;
        preferences=getSharedPreferences("privacy",0);
        engine=new CountingEngine(HistoryStore.get(this));
        FriendSync.get(this);
        indicator=new PlayerIndicator(this);
        ProbeStore.setDiagnostics(preferences.getBoolean("diagnostics",false),(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        android.content.IntentFilter filter=new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF);
        filter.addAction(android.content.Intent.ACTION_DATE_CHANGED);
        filter.addAction(android.content.Intent.ACTION_TIME_CHANGED);
        filter.addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED);
        if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(screenReceiver,filter,android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver,filter);
    }
    private final Handler handler = new Handler();
    private boolean queued;
    private long generation;
    private int verification;
    private long lastScan, dueAt, scrollUntil, lastSourceSample, instagramConfirmedAt, lastPagerAt, youtubeConfirmedAt;
    private final FastFingerprintTracker youtubeFast=new FastFingerprintTracker();
    private boolean quickQueued;
    private int quickAttempts;
    private long quickGeneration;
    private final Runnable quickTask=()-> {
        quickQueued=false;
        if(quickGeneration==ProbeStore.generation) readYouTubeFooter();
    };
    private int pagerIndex=-1, acceptedPagerIndex=-1;
    private String sourceSummary="none";
    private final Runnable scanTask = () -> {
        queued=false;
        if(generation != ProbeStore.generation) return;
        scan();
    };
    @Override protected void onServiceConnected() {
        ProbeStore.connected=true;
        applyProfile();
    }
    private void applyProfile() {
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null) return;
        // YouTube exposes its content labels in the expanded hierarchy; only enable it for YouTube.
        ShortVideoDetector detector=Detectors.forPackage(ProbeStore.target);
        if(detector!=null && detector.expandedHierarchy()) info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(!trackingAllowed()) return;
        String pkg=String.valueOf(event.getPackageName());
        // Our badge is a display-only window. Its own events must not close the feed
        // or schedule another scan; opening the actual ScrollCount activity still closes it.
        if(indicator!=null && (pkg.equals(getPackageName()) || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)
                && indicator.ownsWindow(event.getWindowId()))return;
        if(pkg.equals(getPackageName())) {
            // Some OEMs omit the badge's accessibility window ID. Use active-window
            // package metadata only, never a node traversal, to distinguish our app
            // opening from our display-only badge changing.
            if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
            AccessibilityNodeInfo active=getRootInActiveWindow();
            if(active==null) { suspend("Window unavailable — timing paused"); return; }
            try {
                if(!getPackageName().contentEquals(String.valueOf(active.getPackageName()))
                        || "android.widget.TextView".contentEquals(String.valueOf(active.getClassName())))return;
            } finally {
                active.recycle();
            }
        }
        ShortVideoDetector detector=Detectors.forPackage(pkg);
        if(detector==null) {
            if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && !pkg.equals("android") && !pkg.equals("com.android.systemui")) {
                AccessibilityNodeInfo active=getRootInActiveWindow();
                if(active!=null) {
                    boolean stillPlayer;
                    try {
                        stillPlayer=ProbeStore.target.contentEquals(String.valueOf(active.getPackageName()));
                    } finally {
                        active.recycle();
                    }
                    if(stillPlayer) {
                        suspend("Temporary window — timing paused");
                        schedule(180);
                        return;
                    }
                }
            }
            // Foreground package metadata closes sessions. No unrelated app source/tree/text is accessed.
            if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !pkg.equals("android") && !pkg.equals("com.android.systemui") && !pkg.equals("null")) {
                engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
                hideIndicator();
                ProbeStore.counter.forgetFingerprints();
                instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;
                youtubeFast.reset();
                ProbeStore.status="Waiting for Reels or Shorts";
            }
            if((pkg.equals("android") || pkg.equals("com.android.systemui")) && (event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED))suspend("System overlay — timing paused");
            return;
        }
        if(!pkg.equals(ProbeStore.target)) {
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
            applyProfile();
        }
        ProbeStore.events++;
        boolean scroll=event.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED;
        boolean sampleSource=scroll || (YouTubeSignals.PACKAGE.equals(pkg) && SystemClock.elapsedRealtime()-lastSourceSample>=1000);
        if(sampleSource) {
            lastSourceSample=SystemClock.elapsedRealtime();
            String id="", type="";
            AccessibilityNodeInfo source=event.getSource();
            if(source!=null) {
                try {
                    if(pkg.equals(String.valueOf(source.getPackageName()))) {
                        id=source.getViewIdResourceName();
                        if(id==null) id="";
                        type=String.valueOf(source.getClassName());
                    }
                }
                finally {
                    source.recycle();
                }
            }
            sourceSummary="id="+id+" class="+type+" indices="+event.getFromIndex()+":"+event.getToIndex();
            long now=SystemClock.elapsedRealtime();
            if(scroll && !detector.usesPosition() && detector.movement(id,type,event.getFromIndex(),event.getToIndex())) {
                scrollUntil=now+5000;
                youtubeFast.movement(now);
                quickAttempts=0;
                if(FeedEvidence.valid(youtubeConfirmedAt,now)) queueYouTubeRead(20);
            }
            if(scroll && detector.usesPosition() && detector.movement(id,type,event.getFromIndex(),event.getToIndex())) {
                if(pagerIndex != event.getFromIndex() || lastPagerAt==0) {
                    scrollUntil=now+2500;
                    lastPagerAt=now;
                }
                pagerIndex=event.getFromIndex();
                // Exact indexed player events preserve fast exposures; tree checks stay coalesced.
                if(FeedEvidence.valid(instagramConfirmedAt,now)) commitInstagramPosition(lastPagerAt,now);
                else ProbeStore.decision(now+" INDEX_NOT_ACCEPTED index="+pagerIndex+" reason=no_recent_feed_confirmation");
            }
            if(scroll) ProbeStore.scroll(SystemClock.elapsedRealtime()+" "+sourceSummary);
            else ProbeStore.source(SystemClock.elapsedRealtime()+" "+AccessibilityEvent.eventTypeToString(event.getEventType())+" changeTypes="+event.getContentChangeTypes()+" "+sourceSummary);
        }
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
        // Content-change storms are sampled, not scanned at every progress update.
        schedule(scroll ? 180 : 800);
        maintain(false);
    }
    private void schedule(long delay) {
        long now=SystemClock.elapsedRealtime();
        long requested=Math.max(now+delay,lastScan+400);
        if(queued && requested>=dueAt) return;
        if(queued) handler.removeCallbacks(scanTask);
        generation=ProbeStore.generation;
        queued=true;
        dueAt=requested;
        handler.postDelayed(scanTask,Math.max(0,requested-now));
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
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) {
            suspend("No accessible root");
            return;
        }
        try {
            // Package metadata only; never traverse an unrelated app's tree.
            if(!ProbeStore.target.contentEquals(String.valueOf(root.getPackageName()))) {
                engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
                hideIndicator();
                ProbeStore.counter.forgetFingerprints();
                instagramConfirmedAt=lastPagerAt=youtubeConfirmedAt=0;
                youtubeFast.reset();
                suspend("Waiting for Reels or Shorts");
                return;
            }
            Snapshot s=new Snapshot(ProbeStore.target);
            s.indexed=pagerIndex>=0;
            s.walk(root,0);
            if(YouTubeSignals.PACKAGE.equals(ProbeStore.target)) s.feed |= YouTubeSignals.confirmedContainer(s.ytRecycler,s.ytPage);
            boolean youtube=YouTubeSignals.PACKAGE.equals(ProbeStore.target);
            boolean playerStructure=youtube ? s.ytRecycler && s.ytPage : s.feed && s.like && s.share;
            if(playerStructure && !s.overlay && !s.cut) {
                if(YouTubeSignals.PACKAGE.equals(ProbeStore.target)) collectVisibleYouTubeFooter(root,s);
                else if(pagerIndex<0)s.collect(root,0,new int[] {
                    0
                },false);
            }
            ProbeStore.scans++;
            if(s.cut) ProbeStore.truncated++;
            ProbeStore.tree=s.structure.toString();
            Rect rootBounds=new Rect();
            root.getBoundsInScreen(rootBounds);
            String snapshotInfo="at="+SystemClock.elapsedRealtime()+" bounds="+rootBounds.flattenToString()+" nodes="+s.nodes+" truncated="+s.cut+ " feed="+s.feed+" youtubeRecycler="+s.ytRecycler+" youtubePage="+s.ytPage+" like="+s.like+" share="+s.share+" contentFields="+s.fields+ " expandedHierarchy="+YouTubeSignals.PACKAGE.equals(ProbeStore.target);
            long area=(long)Math.max(0,rootBounds.width())*Math.max(0,rootBounds.height());
            long screenArea=(long)getResources().getDisplayMetrics().widthPixels*getResources().getDisplayMetrics().heightPixels;
            long score=(screenArea>0 ? Math.min(1000000,area*1000000/screenArea) : 0)+s.nodes;
            ProbeStore.retainTree(s.structure.toString(),snapshotInfo,score,s.feed);
            ProbeStore.signals="rootBounds="+rootBounds.flattenToString()+", youtubeRecycler="+s.ytRecycler+", youtubePage="+s.ytPage+", feedMarker="+s.feed+", like="+s.like+", share="+s.share+", overlay="+s.overlay+ ", captionFields="+s.captionFields+", pagerIndex="+pagerIndex+", lastScrollSource="+sourceSummary+", contentFields="+s.fields+", nodes="+s.nodes+", truncated="+s.cut;
            boolean fullPlayer=FeedEvidence.fullPlayer(rootBounds.width(),rootBounds.height(),getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
            boolean eligible=playerStructure && !s.overlay && !s.cut && s.fields>0 && fullPlayer;
            indicatorReady=playerStructure && !s.overlay && !s.profile && !s.cut && fullPlayer;
            String hash=s.finish();
            ProbeStore.fingerprint=ProbeStore.enabled?hash:"";
            int before=ProbeStore.counter.accepted;
            ShortVideoDetector detector=Detectors.forPackage(ProbeStore.target);
            boolean instagram=detector.usesPosition();
            // Exact player-pager position distinguishes identical captions and cannot change on a like.
            String identity=instagram && pagerIndex>=0 ? "pager:"+pagerIndex : hash;
            boolean movement=instagram ? (pagerIndex>=0 && pagerIndex!=acceptedPagerIndex && SystemClock.elapsedRealtime()<=scrollUntil) : SystemClock.elapsedRealtime()<=scrollUntil;
            long observationTime=SystemClock.elapsedRealtime();
            if(instagram) instagramConfirmedAt=FeedEvidence.update(instagramConfirmedAt,observationTime,eligible,s.feed,s.overlay||s.profile,!s.cut,fullPlayer);
            else {
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
            else if(!instagram && eligible && ProbeStore.counter.hasBaseline()) {
                youtubeConfirmedAt=observationTime;
                acceptYouTubeFingerprint(hash,observationTime);
                // The fast helper logs its own accepted change.
                before=ProbeStore.counter.accepted;
                if(youtubeFast.active(observationTime)) queueYouTubeRead(20);
            }
            else {
                ProbeStore.status=ProbeStore.counter.observe(identity,observationTime,eligible,movement);
                if(!instagram && ProbeStore.counter.isCommitted(identity)) youtubeFast.seed(identity);
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
        }
        catch(RuntimeException e) {
            suspend("Tree unavailable — no count");
        }
        finally {
            root.recycle();
            long cost=SystemClock.elapsedRealtime()-lastScan;
            ProbeStore.scanTimeMs+=cost;
            ProbeStore.maxScanMs=Math.max(ProbeStore.maxScanMs,cost);
            maintain(false);
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
        engine.observe("YouTube",hash,ProbeStore.counter.committedAt(),now,System.currentTimeMillis());
        refreshIndicator();
        if(ProbeStore.counter.accepted!=before) ProbeStore.transition(now+" ACCEPTED_TRANSITION #"+ProbeStore.counter.accepted);
    }
    private void readYouTubeFooter() {
        long now=SystemClock.elapsedRealtime();
        if(!trackingAllowed() || !YouTubeSignals.PACKAGE.equals(ProbeStore.target) || !FeedEvidence.valid(youtubeConfirmedAt,now) || !youtubeFast.active(now)) return;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(!power.isInteractive() || keyguard.isKeyguardLocked())return;
        quickAttempts++;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) {
            continueYouTubeRead();
            return;
        }
        try {
            if(!YouTubeSignals.PACKAGE.contentEquals(String.valueOf(root.getPackageName())))return;
            Rect bounds=new Rect();
            root.getBoundsInScreen(bounds);
            if(!FeedEvidence.fullPlayer(bounds.width(),bounds.height(),getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels))return;
            Snapshot snapshot=new Snapshot(YouTubeSignals.PACKAGE);
            collectVisibleYouTubeFooter(root,snapshot);
            ProbeStore.quickReads++;
            if(snapshot.fields>0) {
                String hash=snapshot.finish();
                ProbeStore.fingerprint=ProbeStore.enabled?hash:"";
                acceptYouTubeFingerprint(hash,now);
            }
            else youtubeFast.observe(null,now,false);
        }
        catch(RuntimeException ignored) {
            /* An unavailable label is not a transition. */
        }
        finally {
            root.recycle();
        }
        continueYouTubeRead();
    }
    private void continueYouTubeRead() {
        long now=SystemClock.elapsedRealtime();
        if(FeedEvidence.valid(youtubeConfirmedAt,now) && youtubeFast.active(now))
            queueYouTubeRead(quickAttempts<12?70:120);
    }
    private void collectVisibleYouTubeFooter(AccessibilityNodeInfo root,Snapshot snapshot) {
        java.util.List<AccessibilityNodeInfo> footers=root.findAccessibilityNodeInfosByViewId(YouTubeSignals.PACKAGE+":id/reel_player_footer_container");
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
        }
        finally {
            for(AccessibilityNodeInfo footer:footers)footer.recycle();
        }
    }
    private void commitInstagramPosition(long at,long now) {
        int before=ProbeStore.counter.accepted;
        ProbeStore.status=ProbeStore.counter.verifiedPosition(pagerIndex,at,true);
        acceptedPagerIndex=pagerIndex;
        engine.observe("Instagram","pager:"+pagerIndex,ProbeStore.counter.committedAt(),now,System.currentTimeMillis());
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
        history.dailyItems(day,end,totals-> {
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
        });
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
        handler.removeCallbacks(scanTask);
        queued=false;
        suspend("Accessibility interrupted");
    }
    @Override public void onDestroy() {
        hideIndicator();
        handler.removeCallbacks(scanTask);
        handler.removeCallbacks(quickTask);
        if(engine!=null) engine.close(SystemClock.elapsedRealtime(),System.currentTimeMillis());
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        unregisterReceiver(screenReceiver);
        activeService=null;
        ProbeStore.connected=false;
        ProbeStore.reset("");
        super.onDestroy();
    }
    private static final class Snapshot {
        final String pkg;
        final StringBuilder structure=new StringBuilder();
        final Mac mac;
        final java.util.Set<String> youtubeLabels=new java.util.TreeSet<>();
        int nodes,fields,captionFields;
        final int maxNodes,maxDepth;
        boolean feed,like,share,overlay,cut,ytRecycler,ytPage,profile,indexed;
        Snapshot(String pkg) {
            this.pkg=pkg;
            ShortVideoDetector detector=Detectors.forPackage(pkg);
            maxNodes=detector.maxNodes();
            maxDepth=detector.maxDepth();
            try {
                mac=Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(ProbeStore.salt,"HmacSHA256"));
            }
            catch(java.security.GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
        void walk(AccessibilityNodeInfo n,int depth) {
            if(nodes>=maxNodes || depth>maxDepth) {
                cut=true;
                return;
            }
            nodes++;
            if(!n.isVisibleToUser()) return;
            String id=n.getViewIdResourceName();
            id=id==null?"":id;
            String lowerId=id.toLowerCase(Locale.ROOT);
            String type=String.valueOf(n.getClassName());
            // Never process typed/password content, even transiently.
            if(n.isPassword() || n.isEditable() || type.contains("EditText")) {
                overlay=true;
                return;
            }
            if(ProbeStore.verbose) {
                Rect bounds=new Rect();
                n.getBoundsInScreen(bounds);
                for(int j=0;j<Math.min(depth,maxDepth);j++) structure.append(" ");
                structure.append(type).append(" id=").append(id) .append(" bounds=").append(bounds.flattenToString()).append(" children=").append(n.getChildCount()) .append(" selected=").append(n.isSelected()).append(" clickable=").append(n.isClickable()) .append(" text=").append(n.getText()==null?"none":"[redacted]") .append(" description=").append(n.getContentDescription()==null?"none":"[redacted]").append('\n');
            }
            boolean control=type.endsWith("Button") || n.isSelected();
            String text=control?String.valueOf(n.getText()).toLowerCase(Locale.ROOT):"";
            String desc=control?String.valueOf(n.getContentDescription()).toLowerCase(Locale.ROOT):"";
            String controls=lowerId+" "+text+" "+desc;
            feed |= (InstagramSignals.PACKAGE.equals(pkg) ? InstagramSignals.player(id) : lowerId.contains("shorts")) || ((n.isSelected() || !n.isClickable()) && (text.equals("reels") || text.equals("shorts")));
            if(InstagramSignals.PACKAGE.equals(pkg) && indexed) {
                if(InstagramSignals.author(id) || InstagramSignals.caption(id))fields++;
                if(InstagramSignals.caption(id))captionFields++;
            }
            ytRecycler |= YouTubeSignals.PACKAGE.equals(pkg) && YouTubeSignals.recycler(id);
            ytPage |= YouTubeSignals.PACKAGE.equals(pkg) && YouTubeSignals.page(id);
            like |= controls.contains("like") || controls.contains("पसंद");
            share |= controls.contains("share") || controls.contains("send") || controls.contains("शेयर");
            profile |= lowerId.contains("profile_header");
            overlay |= lowerId.contains("comment_sheet") || lowerId.contains("comments_panel") || lowerId.contains("bottom_sheet") || lowerId.contains("profile_header") || lowerId.contains("share_sheet");
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
            if(visited[0]++>=maxNodes || depth>maxDepth || !n.isVisibleToUser() || n.isPassword() || n.isEditable()) return;
            String id=n.getViewIdResourceName();
            id=id==null?"":id;
            String lowerId=id.toLowerCase(Locale.ROOT);
            inCaption |= InstagramSignals.PACKAGE.equals(pkg) && InstagramSignals.caption(id);
            // Caption content is exposed on anonymous descendants in this Instagram version.
            boolean contentId=InstagramSignals.PACKAGE.equals(pkg) ? (InstagramSignals.author(id) || inCaption) :lowerId.contains("caption") || lowerId.contains("video_title") || lowerId.contains("reel_description") || lowerId.contains("shorts_description") || lowerId.contains("channel_name") || lowerId.contains("reel_author") || lowerId.contains("video_description");
            if(contentId) {
                String raw=n.getText()!=null?n.getText().toString():n.getContentDescription()!=null?n.getContentDescription().toString():"";
                if(!raw.trim().isEmpty()) {
                    byte[] value=(id+"\u0000"+raw.trim()+"\u0000").getBytes(StandardCharsets.UTF_8);
                    mac.update(value);
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
                        if(group.getChildCount()>=3) {
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
