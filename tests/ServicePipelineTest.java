package in.scrollcount.probe;
import java.lang.reflect.*;
import android.content.SharedPreferences;
/** Production orchestration: never replace applySource/applyScan with a test algorithm. */
public final class ServicePipelineTest {
    static Class<?> snapshot,result;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void set(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
        if(name.equals("reader"))try {Field source=object.getClass().getDeclaredField("sourceReader");source.setAccessible(true);source.set(object,value);}catch(NoSuchFieldException ignored){}
    }
    static Object get(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception {
        Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(object,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    static void time(long now)throws Exception {Class.forName("android.os.SystemClock").getField("now").setLong(null,now);}
    static final class Sink implements CountingEngine.Sink {
        int detected,ends;
        public void startSession(String s,String p,long at){}
        public void detected(String s,String p,long at){detected++;}
        public void exposure(String s,String p,long start,long end,long duration,String c){}
        public void endSession(String s,long at){ends++;}
    }
    static ProbeService service(String pkg,Sink sink)throws Exception {
        ProbeStore.reset(pkg);time(10000);
        ProbeService s=new ProbeService();
        SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(ServicePipelineTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->m.getName().equals("getInt")?(a[0].equals("consentVersion")?3:0):true);
        set(s,"preferences",prefs);Class.forName("android.content.Context").getField("prefs").set(null,prefs);
        set(s,"engine",new CountingEngine(sink));
        set(s,"handler",new android.os.Handler());
        set(s,"reader",new ReadLane((java.util.concurrent.Executor)r->{}));
        set(s,"readTimers",new java.util.HashSet<Runnable>());
        set(s,"instagramIdentity",new InstagramIdentity());
        set(s,"instagramFast",new FastFingerprintTracker());
        set(s,"youtubeFast",new FastFingerprintTracker());
        set(s,"instagramAliasPosition",-1);set(s,"instagramPlayerWindow",-1);set(s,"playerWindow",11);
        set(s,"pagerIndex",-1);set(s,"acceptedPagerIndex",-1);
        set(s,"sourceSummary","");
        // Suppress scheduled work: this harness supplies deterministic frames explicitly.
        set(s,"queued",true);set(s,"dueAt",Long.MIN_VALUE);
        return s;
    }
    static Object frame(String pkg,int index,String hash,boolean complete,boolean feed)throws Exception {
        Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance(pkg,1080,2400);
        set(snap,"feed",feed);set(snap,"igPager",feed);set(snap,"igVideo",feed);
        set(snap,"like",feed);set(snap,"share",feed);set(snap,"cut",!complete);set(snap,"fields",1);
        Constructor<?> rc=result.getDeclaredConstructor();rc.setAccessible(true);Object out=rc.newInstance();
        set(out,"snapshot",snap);set(out,"index",index);set(out,"hash",hash);set(out,"window",11);set(out,"at",android.os.SystemClock.elapsedRealtime());
        ((android.graphics.Rect)get(out,"bounds")).set(0,0,1080,2400);
        return out;
    }
    static void scan(ProbeService s,int index,String hash,boolean complete,boolean feed,long now)throws Exception {
        time(now);call(s,"applyScan",new Class<?>[]{result},frame(ProbeStore.target,index,hash,complete,feed));
    }
    static void source(ProbeService s,int from,int to,long now)throws Exception {
        time(now);call(s,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class},
            ProbeStore.target,ProbeStore.target+":id/clips_viewer_view_pager","androidx.viewpager.widget.ViewPager",from,to,4096,0,true,now);
    }
    static void missingIndices()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        source(s,10,10,10000);scan(s,10,"reel-A",true,true,10100);
        check(sink.detected==1,"indexed baseline records one exposure");
        source(s,-1,-1,12000);
        // Use the production effective index; a lost index must not keep the stale 10.
        int index=((InstagramIdentity)get(s,"instagramIdentity")).scanIndex((Integer)get(s,"pagerIndex"));
        scan(s,index,"reel-B",true,true,12200);scan(s,index,"reel-B",true,true,13000);
        check(sink.detected==2,"MISSING_INDEX_FREEZE: verified swipe + stable new content must recover after indices disappear");
        source(s,11,11,13200);scan(s,((InstagramIdentity)get(s,"instagramIdentity")).scanIndex((Integer)get(s,"pagerIndex")),"reel-B",true,true,13300);
        check(sink.detected==2,"late index aliases the same exposure; no duplicate");
        scan(s,-1,"reel-B action edit",true,true,18000);scan(s,-1,"reel-B action edit",true,true,19000);
        check(sink.detected==2,"an expired movement cannot authorize an action/label change");
        source(s,-1,-1,20000);scan(s,-1,"reel-C",true,true,20100);scan(s,-1,"reel-C",true,true,20800);
        check(sink.detected==3,"later unindexed swipes continue counting after recovery");
    }
    static void partialRead()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        source(s,10,10,10000);scan(s,10,"reel-A",true,true,10100);
        scan(s,10,"",false,false,11000);
        check(sink.ends==0 && ProbeStore.counter.hasBaseline(),"PARTIAL_READ_EXIT: truncated frame must pause, not close or forget the baseline");
        scan(s,10,"reel-A",true,true,12000);
        check(sink.detected==1,"recovering the same reel cannot increment the total");
        scan(s,10,"",true,false,13000);
        check(sink.ends==1,"a complete non-player frame still closes the session");
    }
    static Object node(String id,String label,boolean visible,Object...children)throws Exception {
        Class<?> type=Class.forName("android.view.accessibility.AccessibilityNodeInfo");
        Object n=type.getConstructor().newInstance();
        type.getField("id").set(n,id);type.getField("label").set(n,label);type.getField("visible").setBoolean(n,visible);
        Object array=Array.newInstance(type,children.length);
        for(int i=0;i<children.length;i++)Array.set(array,i,children[i]);
        type.getField("children").set(n,array);return n;
    }
    static void hiddenWrapper()throws Exception {
        Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance(InstagramSignals.PACKAGE,1080,2400);set(snap,"deadline",20000L);time(10000);
        Object text=node("","Scoped reel caption",true);
        Object caption=node("com.instagram.android:id/clips_caption_component","",true,text);
        Object root=node("","",false,caption);
        call(snap,"collect",new Class<?>[]{Class.forName("android.view.accessibility.AccessibilityNodeInfo"),int.class,int[].class,boolean.class},root,0,new int[]{0},false);
        check((Integer)get(snap,"fields")==1,"HIDDEN_WRAPPER: visible captions must survive a non-visible structural parent");
        Object protectedRoot=node("","",false,caption);protectedRoot.getClass().getField("editable").setBoolean(protectedRoot,true);
        Object safe=ctor.newInstance(InstagramSignals.PACKAGE,1080,2400);set(safe,"deadline",20000L);
        call(safe,"collect",new Class<?>[]{Class.forName("android.view.accessibility.AccessibilityNodeInfo"),int.class,int[].class,boolean.class},protectedRoot,0,new int[]{0},false);
        check((Integer)get(safe,"fields")==0,"editable subtrees remain excluded");
    }
    static void youtubeWrapper()throws Exception {
        Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance(YouTubeSignals.PACKAGE,1080,2400);set(snap,"deadline",20000L);time(10000);
        Object title=node("","Short title",true);
        Object channel=node("","Channel",true);
        Object group=node("","",true,node("","",true,title),node("","",true,channel,node("","Subscribe",true)));
        group.getClass().getField("right").setInt(group,1080);
        Object overlay=node("com.google.android.youtube:id/player_overlay","",true,group);
        Object footer=node("com.google.android.youtube:id/reel_player_footer_container","",true,overlay);
        Object root=node("","",false,footer);
        call(snap,"findYouTubeFooter",new Class<?>[]{Class.forName("android.view.accessibility.AccessibilityNodeInfo"),int.class,int[].class,boolean.class},root,0,new int[]{0},false);
        check((Integer)get(snap,"fields")==2,"YouTube scoped title/channel survive a hidden root; adjacent action excluded");
    }
    static void optionalAliasBudget()throws Exception {
        ProbeService s=service(InstagramSignals.PACKAGE,new Sink());
        Object caption=node("com.instagram.android:id/clips_caption_component","Slow caption",true);
        caption.getClass().getField("labelDelay").setLong(caption,1200L);
        Object root=node("com.instagram.android:id/clips_viewer_view_pager","",true,
            node("com.instagram.android:id/clips_video_container","",true),caption);
        root.getClass().getField("right").setInt(root,1080);
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,root);
        Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance(InstagramSignals.PACKAGE,1080,2400);
        Object out=call(s,"readSnapshot",new Class<?>[]{snapshot,int.class,int.class,boolean.class},snap,10,-1,false);
        check(!(Boolean)get(snap,"cut") && ((String)get(out,"hash")).isEmpty(),"slow optional alias cannot discard a complete indexed player; partial content must not seed recovery");
        time(10000);Object unindexed=ctor.newInstance(InstagramSignals.PACKAGE,1080,2400);
        call(s,"readSnapshot",new Class<?>[]{snapshot,int.class,int.class,boolean.class},unindexed,-1,-1,false);
        check((Boolean)get(unindexed,"cut"),"unindexed content still requires a complete read");
    }
    static void staleSource()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        source(s,10,10,10000);scan(s,10,"reel-A",true,true,10100);
        time(14000);call(s,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class},
            ProbeStore.target,ProbeStore.target+":id/clips_viewer_view_pager","ViewPager",11,11,4096,0,true,12000L);
        check(sink.detected==1 && (Integer)get(s,"pagerIndex")==10 && ProbeStore.expiredSources==1,"late source is rejected and diagnosed, never guessed into a count");
    }
    static void instagramProfile()throws Exception {
        ProbeService s=service(InstagramSignals.PACKAGE,new Sink());
        Object info=Class.forName("android.accessibilityservice.AccessibilityService").getField("info").get(null);
        info.getClass().getField("flags").setInt(info,0);
        call(s,"applyProfile",new Class<?>[]{});
        check((info.getClass().getField("flags").getInt(info)&2)!=0,"INSTAGRAM_HIERARCHY: expanded nodes must be requested before a player marker is found");
    }
    static void reusedIndex()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        for(int i=0;i<20;i++) {
            long now=10000+i*3000;
            source(s,0,0,now);
            InstagramIdentity mode=(InstagramIdentity)get(s,"instagramIdentity");
            scan(s,mode.scanIndex((Integer)get(s,"pagerIndex")),"distinct-reel-"+i,true,true,now+200);
            scan(s,mode.scanIndex((Integer)get(s,"pagerIndex")),"distinct-reel-"+i,true,true,now+900);
        }
        check(sink.detected==20,"REUSED_INDEX_FREEZE: verified player movement and distinct stable content must work when every index is zero");
    }
    static void hiddenPlayerContainers()throws Exception {
        Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance(InstagramSignals.PACKAGE,1080,2400);set(snap,"deadline",20000L);time(10000);
        Object video=node("com.instagram.android:id/clips_video_container","",false,node("","Visible video surface",true));
        video.getClass().getField("right").setInt(video,1080);
        Object pager=node("com.instagram.android:id/clips_viewer_view_pager","",false,video);
        pager.getClass().getField("right").setInt(pager,1080);
        Object root=node("","",true,pager);
        call(snap,"walk",new Class<?>[]{Class.forName("android.view.accessibility.AccessibilityNodeInfo"),int.class},root,0);
        check((Boolean)get(snap,"igPager") && (Boolean)get(snap,"igVideo"),"HIDDEN_PLAYER_MARKERS: structural wrappers with visible full-player descendants must establish the player");
        pager.getClass().getField("top").setInt(pager,2600);pager.getClass().getField("bottom").setInt(pager,5000);
        Object offscreen=ctor.newInstance(InstagramSignals.PACKAGE,1080,2400);set(offscreen,"deadline",20000L);
        call(offscreen,"walk",new Class<?>[]{Class.forName("android.view.accessibility.AccessibilityNodeInfo"),int.class},root,0);
        check(!(Boolean)get(offscreen,"igPager"),"offscreen player wrappers cannot establish a visible viewer");
        Object protectedRoot=node("","Private typed text",false,root);
        protectedRoot.getClass().getField("editable").setBoolean(protectedRoot,true);
        Object guarded=ctor.newInstance(InstagramSignals.PACKAGE,1080,2400);set(guarded,"deadline",20000L);
        call(guarded,"walk",new Class<?>[]{Class.forName("android.view.accessibility.AccessibilityNodeInfo"),int.class},protectedRoot,0);
        check((Integer)get(guarded,"nodes")==0 && protectedRoot.getClass().getField("reads").getInt(protectedRoot)==0,"hidden editable subtree is not read or traversed");
    }
    static void missingSourceMetadata()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        source(s,10,10,10000);scan(s,10,"reel-A",true,true,10100);
        time(12000);call(s,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class},
            ProbeStore.target,"","androidx.viewpager.widget.ViewPager",11,11,4096,0,true,12000L);
        InstagramIdentity mode=(InstagramIdentity)get(s,"instagramIdentity");
        int index=mode.scanIndex((Integer)get(s,"pagerIndex"));
        scan(s,index,"reel-B",true,true,12200);scan(s,index,"reel-B",true,true,12900);
        check(sink.detected==2,"MISSING_SOURCE_FREEZE: current full player + pager class/window metadata confirms stable content without guessing index 11");
        for(String kind:new String[]{"ScrollView","RecyclerView"}) {
            time(14000);call(s,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class},
                ProbeStore.target,"",kind,12,12,4096,0,true,14000L);
            scan(s,-1,"action edit",true,true,14200);scan(s,-1,"action edit",true,true,14900);
        }
        check(sink.detected==2,"unknown sources cannot authorize caption/comment scrolling");
        time(16000);call(s,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class,int.class},
            ProbeStore.target,"","ViewPager",12,12,4096,0,true,16000L,999);
        scan(s,-1,"wrong-window",true,true,16200);scan(s,-1,"wrong-window",true,true,16900);
        check(sink.detected==2,"a different window cannot use player confirmation");
    }
    static void ancestorSource()throws Exception {
        ProbeService s=service(InstagramSignals.PACKAGE,new Sink());
        Object pager=node("com.instagram.android:id/clips_viewer_view_pager","",true);
        Object recycler=node("","",true);recycler.getClass().getField("type").set(recycler,"androidx.recyclerview.widget.RecyclerView");
        recycler.getClass().getField("parent").set(recycler,pager);
        Class<?> nt=Class.forName("android.view.accessibility.AccessibilityNodeInfo");
        check(((String)call(s,"instagramPagerAncestor",new Class<?>[]{nt,String.class},recycler,InstagramSignals.PACKAGE)).endsWith("clips_viewer_view_pager"),"descendant recycler inherits verified player scope");
        for(String excluded:new String[]{"clips_caption_component","comments_panel","profile_header","story_viewer"}) {
            Object blocked=node("com.instagram.android:id/"+excluded,"",true);
            blocked.getClass().getField("parent").set(blocked,pager);
            recycler.getClass().getField("parent").set(recycler,blocked);
            check(((String)call(s,"instagramPagerAncestor",new Class<?>[]{nt,String.class},recycler,InstagramSignals.PACKAGE)).isEmpty(),"blocked source scope: "+excluded);
        }
    }
    static Object event(int from,int type,Object source)throws Exception {
        Class<?> c=Class.forName("android.view.accessibility.AccessibilityEvent");Object e=c.getConstructor().newInstance();
        c.getField("from").setInt(e,from);c.getField("to").setInt(e,from);
        c.getField("eventType").setInt(e,type);c.getField("source").set(e,source);return e;
    }
    static Object player(String caption,boolean hidden)throws Exception {
        Object author=node("com.instagram.android:id/clips_author_username","same-author",true);
        Object label=node("com.instagram.android:id/clips_caption_component",caption,true);
        Object surface=node("","",true);surface.getClass().getField("type").set(surface,"android.view.SurfaceView");
        Object video=node("com.instagram.android:id/clips_video_container","",!hidden,surface);
        video.getClass().getField("right").setInt(video,1080);
        Object pager=node("com.instagram.android:id/clips_viewer_view_pager","",!hidden,video,author,label);
        pager.getClass().getField("right").setInt(pager,1080);
        pager.getClass().getField("type").set(pager,"androidx.viewpager.widget.ViewPager");return pager;
    }
    static void pump(ProbeService s,long end)throws Exception {
        Object handler=get(s,"handler");handler.getClass().getMethod("runUntil",long.class).invoke(handler,end);
    }
    static void eventPipeline()throws Exception {
        // Drive real onAccessibilityEvent, readSource, scanTask, readSnapshot and applyScan.
        // Do not prefill any eligible/player/content fields in this test.
        for(int variant=0;variant<6;variant++) {
            Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
            set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));
            set(s,"queued",false);set(s,"generation",ProbeStore.generation);
            for(int i=0;i<20;i++) {
                long now=10000+i*(variant==5?20000:3000);time(now);
                Object tree=player("reel-"+i,variant==3);
                Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
                Object source=(variant==2 || variant==5) && i>0?null:tree;
                if(variant==4) {
                    source=node("","",true);source.getClass().getField("type").set(source,"androidx.recyclerview.widget.RecyclerView");
                    source.getClass().getField("parent").set(source,tree);
                }
                Object e=event(variant==1?0:i,4096,source);
                if(variant==4)e.getClass().getField("type").set(e,"androidx.recyclerview.widget.RecyclerView");
                s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)e);
                pump(s,now+2200);
            }
            check(sink.detected==20,"EVENT_PIPELINE variant="+variant+" expected 20, got "+sink.detected);
            check(sink.ends==0,"no false feed exits in the event pipeline");
        }
    }
    static void noCountForActions()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));set(s,"queued",false);set(s,"generation",ProbeStore.generation);
        Object tree=player("baseline",false);
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
        s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(0,4096,tree));pump(s,12200);
        int action=0;
        for(String blocked:new String[]{"clips_caption_component","comments_panel","swipeable_tab_view_pager"}) {
            long now=15000+action++*5000;time(now);Object source=node("com.instagram.android:id/"+blocked,"",true);
            source.getClass().getField("type").set(source,"androidx.viewpager.widget.ViewPager");
            Object next=player("expanded or edited label",false);
            Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,next);
            s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(1,4096,source));pump(s,now+2200);
            check(sink.detected==1,"action/navigation scroll cannot authorize a new Reel: "+blocked);
        }
    }
    static void lateIndexedPipeline()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));
        set(s,"queued",false);set(s,"generation",ProbeStore.generation);
        Object tree=player("same caption on different reels",false);
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
        s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(-1,32,tree));
        pump(s,12200);
        check(sink.detected==1,"window entry establishes content baseline before any index exists");
        time(13000);s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(0,4096,tree));pump(s,15200);
        check(sink.detected==1,"first index binds to the already confirmed item without counting twice");
        for(int i=1;i<20;i++) {
            long now=16000+i*3000;time(now);
            s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(i,4096,tree));pump(s,now+2200);
        }
        check(sink.detected==20,"LATE_INDEX_LOCK: later verified indices must count identical captions after an initial content baseline");
    }
    static void pendingSuperseded()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        source(s,0,0,10000);scan(s,0,"reel-A",true,true,10100);
        time(30000);call(s,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class},
            ProbeStore.target,"","ViewPager",1,1,4096,0,true,30000L);
        time(30100);Object emptyAlias=frame(ProbeStore.target,0,"",true,true);set(get(emptyAlias,"snapshot"),"fields",0);
        call(s,"applyScan",new Class<?>[]{result},emptyAlias);
        source(s,1,1,30200);
        scan(s,1,"reel-B",true,true,30400);
        scan(s,((InstagramIdentity)get(s,"instagramIdentity")).scanIndex((Integer)get(s,"pagerIndex")),"reel-B",true,true,31200);
        check(sink.detected==2,"new exact position supersedes pending missing-source recovery without duplicating its exposure");
    }
    static Object legacyPlayer(String caption,boolean clippedPager,boolean preview)throws Exception {
        Object video=node("com.instagram.android:id/clips_video_container","",true);
        video.getClass().getField("right").setInt(video,1080);
        if(preview)video.getClass().getField("bottom").setInt(video,700);
        Object root=node("","",true,video,
            node("com.instagram.android:id/like_button","Like",true),
            node("com.instagram.android:id/share_button","Share",true),
            node("com.instagram.android:id/clips_author_username","same-author",true),
            node("com.instagram.android:id/clips_caption_component",caption,true));
        root.getClass().getField("right").setInt(root,1080);
        if(clippedPager) {
            root.getClass().getField("id").set(root,"com.instagram.android:id/clips_viewer_view_pager");
            root.getClass().getField("bottom").setInt(root,1600); // accessibility wrapper bounds omit system/navigation space
            Object window=node("","",true,root);window.getClass().getField("right").setInt(window,1080);root=window;
        }
        return root;
    }
    static void legacyLayoutPipeline()throws Exception {
        for(int variant=0;variant<3;variant++) {
            Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
            set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));set(s,"queued",false);set(s,"generation",ProbeStore.generation);
            for(int i=0;i<20;i++) {
                long now=10000+i*3000;time(now);
                Object tree=legacyPlayer("legacy-reel-"+i,variant==1,false);
                Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
                Object source=node("com.instagram.android:id/clips_viewer_view_pager","",true);
                source.getClass().getField("type").set(source,"androidx.viewpager.widget.ViewPager");
                if(variant==2 && i>0)source=null;
                s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(i,4096,source));pump(s,now+2200);
            }
            check(sink.detected==20,"LEGACY_LAYOUT_REJECTED variant="+variant+" expected 20, got "+sink.detected);
            check(sink.ends==0,"legacy player observations cannot falsely close the session");
        }
    }
    static void legacyPreviewExcluded()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));set(s,"queued",false);set(s,"generation",ProbeStore.generation);
        Object tree=legacyPlayer("inline video",false,true);
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
        Object source=node("com.instagram.android:id/clips_viewer_view_pager","",true);
        source.getClass().getField("type").set(source,"androidx.viewpager.widget.ViewPager");
        s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(0,4096,source));pump(s,12200);
        check(sink.detected==0,"inline video with like/share is not a full-screen Reel");
        time(15000);tree=legacyPlayer("comment overlay",false,false);
        tree.getClass().getField("id").set(tree,"com.instagram.android:id/comments_panel");
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
        s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(1,4096,source));pump(s,17200);
        check(sink.detected==0,"comments overlay cannot use legacy full-video fallback");
    }
    static Object overlayLayout(String caption,int variant,boolean focused)throws Exception {
        Object tree=player(caption,false);
        if(variant==0) {
            Object input=node("com.instagram.android:id/comment_input","PRIVATE INPUT",true,node("","PRIVATE CHILD",true));
            input.getClass().getField("editable").setBoolean(input,true);input.getClass().getField("focused").setBoolean(input,focused);
            Object root=node("","",true,tree,input);root.getClass().getField("right").setInt(root,1080);return root;
        }
        if(variant==1) {
            Object action=node("com.instagram.android:id/share_sheet_button","Share",true);
            action.getClass().getField("type").set(action,"android.widget.ImageView");
            Object root=node("","",true,tree,action);root.getClass().getField("right").setInt(root,1080);return root;
        }
        Object root=node("com.instagram.android:id/reels_bottom_sheet","",true,tree);
        root.getClass().getField("right").setInt(root,1080);return root;
    }
    static void overlayAffordancePipeline()throws Exception {
        for(int variant=0;variant<3;variant++) {
            Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
            set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));set(s,"queued",false);set(s,"generation",ProbeStore.generation);
            for(int i=0;i<20;i++) {
                long now=10000+i*3000;time(now);Object tree=overlayLayout("reel-"+i,variant,false);
                Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
                Object source=player("",false);
                s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(i,4096,source));pump(s,now+2200);
                if(variant==0) {
                    Object[] children=(Object[])tree.getClass().getField("children").get(tree);Object input=children[1];
                    check(input.getClass().getField("reads").getInt(input)==0,"inline typed text is never read");
                    Object[] privateChildren=(Object[])input.getClass().getField("children").get(input);
                    check(privateChildren[0].getClass().getField("reads").getInt(privateChildren[0])==0,"private subtree is never read");
                }
            }
            check(sink.detected==20,"FALSE_OVERLAY variant="+variant+" expected 20 exposures, observed "+sink.detected);
        }
    }
    static void actualOverlayPanels()throws Exception {
        for(int kind=0;kind<6;kind++) {
            Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
            set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));set(s,"queued",false);set(s,"generation",ProbeStore.generation);
            Object tree=(kind==0 || kind==5)?overlayLayout("typed comment",0,kind==0):node("","",true,player("background reel",false),
                node("com.instagram.android:id/"+new String[]{"unused","comments_panel","share_sheet","bottom_sheet","story_viewer"}[kind],"",true,node("","Visible panel content",true)));
            tree.getClass().getField("right").setInt(tree,1080);
            if(kind==3) {
                Object[] children=(Object[])tree.getClass().getField("children").get(tree);
                children[1].getClass().getField("right").setInt(children[1],1080);
                children[1].getClass().getField("top").setInt(children[1],1200);
            }
            if(kind==5) {
                Object[] children=(Object[])tree.getClass().getField("children").get(tree);
                children[1].getClass().getField("password").setBoolean(children[1],true);
            }
            Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
            s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(0,4096,player("",false)));pump(s,12200);
            check(sink.detected==0,"actual blocking overlay cannot count background Reels, kind="+kind);
        }
    }
    static void siblingSheetPipeline()throws Exception {
        for(int variant=0;variant<2;variant++) {
            Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
            set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));set(s,"queued",false);set(s,"generation",ProbeStore.generation);
            for(int i=0;i<20;i++) {
                long now=10000+i*3000;time(now);
                Object sheet=variant==0?node("com.instagram.android:id/reels_controls_bottom_sheet","",true,node("","Share",true)):
                    node("com.instagram.android:id/bottom_sheet","",true);
                sheet.getClass().getField("type").set(sheet,"android.widget.FrameLayout");
                sheet.getClass().getField("right").setInt(sheet,1080);
                if(variant==0)sheet.getClass().getField("top").setInt(sheet,2200);
                Object tree=node("","",true,player("reel-"+i,false),sheet);tree.getClass().getField("right").setInt(tree,1080);
                Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,tree);
                s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(i,4096,player("",false)));pump(s,now+2200);
            }
            check(sink.detected==20,"SIBLING_SHEET_FALSE_OVERLAY variant="+variant+" expected 20, observed "+sink.detected);
        }
    }
    static void sourceBehindSlowTree()throws Exception {
        Sink sink=new Sink();ProbeService s=service(InstagramSignals.PACKAGE,sink);
        java.util.ArrayList<Runnable> heldTrees=new java.util.ArrayList<>();
        set(s,"reader",new ReadLane((java.util.concurrent.Executor)heldTrees::add));
        try {set(s,"sourceReader",new ReadLane((java.util.concurrent.Executor)Runnable::run));}catch(NoSuchFieldException ignored){}
        set(s,"queued",false);set(s,"generation",ProbeStore.generation);set(s,"instagramConfirmedAt",9900L);
        for(int i=0;i<40;i++) {
            long now=10000+i*100;time(now);
            s.onAccessibilityEvent((android.view.accessibility.AccessibilityEvent)event(5+i,4096,player("",false)));pump(s,now+50);
        }
        check(!heldTrees.isEmpty(),"structural reader is deliberately held busy");
        check((Integer)get(s,"pagerIndex")==44 && sink.detected==40,"TREE_QUEUE_STARVES_PAGER: 40 rapid confirmed indexed events must count while a tree read is blocked; detected="+sink.detected);
    }
    static Object facebookTree(boolean vertical)throws Exception {
        Object like=node("com.facebook.katana:id/(name removed)","Like",true);
        Object share=node("com.facebook.katana:id/(name removed)","Share",true);
        for(Object n:new Object[]{like,share}) {
            set(n,"pkg","com.facebook.katana");set(n,"type","android.widget.Button");
            set(n,"left",900);set(n,"right",1040);set(n,"top",900);set(n,"bottom",1040);
        }
        if(vertical){set(share,"top",1300);set(share,"bottom",1440);}
        else {set(like,"left",600);set(like,"right",740);}
        Object tree=node("com.facebook.katana:id/(name removed)","",true,like,share);
        set(tree,"pkg","com.facebook.katana");set(tree,"type","androidx.recyclerview.widget.RecyclerView");set(tree,"right",1080);
        return tree;
    }
    static Object facebookFrame(ProbeService service,boolean vertical,long now)throws Exception {
        time(now);
        Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance("com.facebook.katana",1080,2400);
        set(snap,"deadline",now+1000);set(snap,"facebookSource",get(service,"facebookSource"));
        for(String name:new String[]{"facebookIndex","facebookWindow","facebookAt"})set(snap,name,get(service,name));
        call(snap,"walk",new Class<?>[]{Class.forName("android.view.accessibility.AccessibilityNodeInfo"),int.class},facebookTree(vertical),0);
        set(snap,"feed",get(snap,"facebookPlayer"));
        Object out=frame("com.facebook.katana",-1,"",true,true);
        set(out,"snapshot",snap);return out;
    }
    static void facebookSource(ProbeService service,int index,long now)throws Exception {
        time(now);call(service,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class},
                "com.facebook.katana","com.facebook.katana:id/(name removed)","androidx.recyclerview.widget.RecyclerView",index,index+1,4096,0,true,now);
    }
    static void facebookPipeline()throws Exception {
        Sink sink=new Sink();ProbeService s=service("com.facebook.katana",sink);
        facebookSource(s,3,10000);
        Object horizontal=facebookFrame(s,false,10100);
        check(!(Boolean)get(get(horizontal,"snapshot"),"facebookPlayer"),"Facebook ordinary-feed horizontal actions rejected");
        Object first=facebookFrame(s,true,10200);
        check((Boolean)get(get(first,"snapshot"),"facebookPlayer"),"Facebook redacted player verified from subtree rail");
        call(s,"applyScan",new Class<?>[]{result},first);
        call(s,"applyScan",new Class<?>[]{result},facebookFrame(s,true,11000));
        check(sink.detected==1 && ProbeStore.counter.accepted==0,"Facebook initial exposure baseline");
        facebookSource(s,4,12000);
        call(s,"applyScan",new Class<?>[]{result},facebookFrame(s,true,12100));
        call(s,"applyScan",new Class<?>[]{result},facebookFrame(s,true,13000));
        call(s,"applyScan",new Class<?>[]{result},facebookFrame(s,true,14000));
        check(sink.detected==2 && ProbeStore.counter.accepted==1,"Facebook stable advance counted once without captions");
        call(s,"applyScan",new Class<?>[]{result},facebookFrame(s,true,20000));
        check(sink.detected==2 && ((CountingEngine)get(s,"engine")).live(20000)!=null,"long Facebook watch preserves confirmed exposure");
        facebookSource(s,5,21000);
        Object wrongWindow=facebookFrame(s,true,21100);set(wrongWindow,"window",12);
        call(s,"applyScan",new Class<?>[]{result},wrongWindow);
        Object panel=facebookFrame(s,true,22000);set(get(panel,"snapshot"),"overlay",true);
        call(s,"applyScan",new Class<?>[]{result},panel);
        call(s,"applyScan",new Class<?>[]{result},facebookFrame(s,true,28000));
        check(sink.detected==2,"Facebook wrong window, overlay and expired new position rejected");
        call(s,"invalidateReads",new Class<?>[]{});
        check((Integer)get(s,"facebookIndex")==-1,"Facebook window/read reset clears position");
    }
    static Object snapchatTree(String label,boolean spotlight,boolean favorite,boolean panel)throws Exception {
        Object title=node("com.snapchat.android:id/0_resource_name_obfuscated",spotlight?"Spotlight":"Stories",true);
        set(title,"bottom",140);
        Object author=node("com.snapchat.android:id/0_resource_name_obfuscated",label,true);
        set(author,"right",650);set(author,"top",1600);set(author,"bottom",1700);
        Object count=node("com.snapchat.android:id/0_resource_name_obfuscated","123K",true);
        set(count,"right",650);set(count,"top",1720);set(count,"bottom",1770);
        Object action=node("com.snapchat.android:id/0_resource_name_obfuscated",favorite?"Favorite":"Share",true);
        set(action,"type","android.widget.Button");set(action,"left",900);set(action,"right",1040);set(action,"top",1000);set(action,"bottom",1140);
        Object player=node("com.snapchat.android:id/0_resource_name_obfuscated","",true,author,count,action);
        set(player,"type","androidx.recyclerview.widget.RecyclerView");set(player,"right",1080);
        Object root=node("","",true,title,player);
        set(root,"right",1080);set(root,"pkg","com.snapchat.android");
        if(panel){Object input=node("","",true);set(input,"editable",true);set(root,"children",new android.view.accessibility.AccessibilityNodeInfo[]{(android.view.accessibility.AccessibilityNodeInfo)title,(android.view.accessibility.AccessibilityNodeInfo)player,(android.view.accessibility.AccessibilityNodeInfo)input});}
        return root;
    }
    static Object snapchatFrame(ProbeService service,String label,boolean spotlight,boolean favorite,boolean panel,long now)throws Exception {
        time(now);Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance("com.snapchat.android",1080,2400);
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,snapchatTree(label,spotlight,favorite,panel));
        return call(service,"readSnapshot",new Class<?>[]{snapshot,int.class,int.class,boolean.class},snap,-1,11,false);
    }
    static void snapchatSource(ProbeService service,long now)throws Exception {
        time(now);call(service,"applySource",new Class<?>[]{String.class,String.class,String.class,int.class,int.class,int.class,int.class,boolean.class,long.class},
                "com.snapchat.android","","android.widget.FrameLayout",-1,-1,4096,0,true,now);
    }
    static void snapchatPipeline()throws Exception {
        Sink sink=new Sink();ProbeService s=service("com.snapchat.android",sink);ProbeStore.setDiagnostics(true,false);
        snapchatSource(s,10000);
        Object stories=snapchatFrame(s,"Private story caption",false,true,false,10100);
        check(!(Boolean)get(get(stories,"snapshot"),"snapchatConfirmed"),"Snapchat Stories are not Spotlight");
        check((Integer)get(get(stories,"snapshot"),"fields")==0,"unconfirmed Snapchat captions are never collected");
        android.view.accessibility.AccessibilityNodeInfo storyRoot=(android.view.accessibility.AccessibilityNodeInfo)Class.forName("android.accessibilityservice.AccessibilityService").getField("root").get(null);
        check((Integer)get(storyRoot.getChild(1).getChild(0),"reads")==0,"unconfirmed Snapchat body text is not read during layout detection");
        Object first=snapchatFrame(s,"Creator Alpha",true,true,false,10200);
        check((Boolean)get(get(first,"snapshot"),"snapchatConfirmed"),"obfuscated Spotlight proof accepted without a share label");
        check((Integer)get(get(first,"snapshot"),"fields")==1,"only scoped text, not numeric counters, becomes identity");
        call(s,"applyScan",new Class<?>[]{result},first);
        call(s,"applyScan",new Class<?>[]{result},snapchatFrame(s,"Creator Alpha",true,true,false,11000));
        check(sink.detected==1,"Snapchat initial baseline establishes one exposure");
        snapchatSource(s,12000);
        call(s,"applyScan",new Class<?>[]{result},snapchatFrame(s,"Creator Beta",true,true,false,12100));
        call(s,"applyScan",new Class<?>[]{result},snapchatFrame(s,"Creator Beta",true,true,false,13000));
        check(sink.detected==2&&ProbeStore.counter.accepted==1,"unindexed FrameLayout scroll and stable scoped content count once");
        call(s,"applyScan",new Class<?>[]{result},snapchatFrame(s,"Creator Beta edited",true,true,false,14000));
        call(s,"applyScan",new Class<?>[]{result},snapchatFrame(s,"Creator Beta edited",true,true,false,15000));
        check(sink.detected==2,"a consumed movement cannot count subsequent label edits");
        snapchatSource(s,16000);
        Object blocked=snapchatFrame(s,"Creator Gamma",true,true,true,16100);
        check((Integer)get(get(blocked,"snapshot"),"fields")==0,"editable overlay excludes Snapchat content collection");
        call(s,"applyScan",new Class<?>[]{result},blocked);
        Object noFavorite=snapchatFrame(s,"Creator Gamma",true,false,false,17000);
        check(!(Boolean)get(get(noFavorite,"snapshot"),"snapchatConfirmed"),"Spotlight tab alone cannot confirm a player");
        Object stale=snapchatFrame(s,"Creator Gamma",true,true,false,22000);
        call(s,"applyScan",new Class<?>[]{result},stale);
        call(s,"applyScan",new Class<?>[]{result},snapchatFrame(s,"Creator Gamma",true,true,false,23000));
        check(sink.detected==2,"expired Snapchat movement cannot count");
        call(s,"invalidateReads",new Class<?>[]{});
        check((Long)get(s,"snapchatMovementAt")==0,"window/read reset clears Snapchat movement");
        Object info=get(get(first,"snapshot"),"snapchatLayout");
        check(info.toString().contains("depth=")&&!info.toString().contains("Creator")&&!info.toString().contains("123K"),"release metadata contains structure but no captions/authors/count labels");
        String report=ProbeStore.report();check(report.contains("Snapchat layout metadata")&&!report.contains("Creator"),"release export includes content-free metadata");
        ProbeStore.setDiagnostics(false,false);check(ProbeStore.snapchatLayout.isEmpty(),"disabling diagnostics clears Snapchat metadata");
    }
    static Object observedSnapchatTree(String caption,boolean shrinkRail,boolean input)throws Exception {
        java.util.List<Object> stack=new java.util.ArrayList<>(),nodes=new java.util.ArrayList<>();
        java.util.List<Integer> depths=new java.util.ArrayList<>();
        java.util.Map<Object,java.util.List<Object>> children=new java.util.IdentityHashMap<>();
        java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("depth=(\\d+) type=(\\S+) bounds=(-?\\d+) (-?\\d+) (-?\\d+) (-?\\d+) selected=(true|false) clickable=(true|false) scrollable=(true|false).* ");
        Object root=null;int railRows=0;
        for(String line:java.nio.file.Files.readAllLines(java.nio.file.Paths.get(System.getProperty("scrollcount.fixture")))){
            java.util.regex.Matcher m=pattern.matcher(line+" ");if(!m.matches())throw new AssertionError("Bad fixture line");
            int depth=Integer.parseInt(m.group(1)),left=Integer.parseInt(m.group(3)),top=Integer.parseInt(m.group(4)),right=Integer.parseInt(m.group(5)),bottom=Integer.parseInt(m.group(6));
            boolean clickable=Boolean.parseBoolean(m.group(8));
            String text=caption.isEmpty()?"":top==1945&&left==22?"Test creator":top==2112&&left==22?caption:top==2035&&left==66?"Test music":"";
            Object n=node("com.snapchat.android:id/0_resource_name_obfuscated",text,true);
            set(n,"pkg","com.snapchat.android");set(n,"type",m.group(2));set(n,"left",left);set(n,"top",top);set(n,"right",right);set(n,"bottom",bottom);set(n,"clickable",clickable);
            if(shrinkRail&&m.group(2).equals("android.view.ViewGroup")&&clickable&&left>=900){railRows++;if(railRows>2)set(n,"clickable",false);}
            nodes.add(n);children.put(n,new java.util.ArrayList<>());
            while(!depths.isEmpty()&&depths.get(depths.size()-1)>=depth){depths.remove(depths.size()-1);stack.remove(stack.size()-1);}
            if(stack.isEmpty()){if(root==null)root=n;}else children.get(stack.get(stack.size()-1)).add(n);
            depths.add(depth);stack.add(n);
        }
        if(input){Object edit=node("","",true);set(edit,"editable",true);children.get(root).add(edit);}
        for(Object n:nodes){java.util.List<Object> kids=children.get(n);android.view.accessibility.AccessibilityNodeInfo[] array=new android.view.accessibility.AccessibilityNodeInfo[kids.size()];for(int i=0;i<array.length;i++)array[i]=(android.view.accessibility.AccessibilityNodeInfo)kids.get(i);set(n,"children",array);}
        return root;
    }
    static Object observedSnapchatFrame(ProbeService service,String caption,boolean shrinkRail,boolean input,long now)throws Exception{
        time(now);Constructor<?> ctor=snapshot.getDeclaredConstructor(String.class,int.class,int.class);ctor.setAccessible(true);
        Object snap=ctor.newInstance("com.snapchat.android",1080,2400);
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,observedSnapchatTree(caption,shrinkRail,input));
        return call(service,"readSnapshot",new Class<?>[]{snapshot,int.class,int.class,boolean.class},snap,-1,11,false);
    }
    static void observedSnapchatPipeline()throws Exception{
        Sink sink=new Sink();ProbeService s=service("com.snapchat.android",sink);
        Object exact=observedSnapchatFrame(s,"",false,false,10000),snap=get(exact,"snapshot");
        check(!(Boolean)get(snap,"snapchatPager")&&!(Boolean)get(snap,"snapchatSpotlight"),"trace supplies no pager or Spotlight label");
        check(get(snap,"snapchatGeometryBounds")!=null&&(Boolean)get(snap,"snapchatConfirmed"),"actual 14.29 non-scrollable layout is structurally recognised");
        check((Integer)get(snap,"fields")==0,"geometry alone never invents content identity");
        snapchatSource(s,11000);
        call(s,"applyScan",new Class<?>[]{result},observedSnapchatFrame(s,"Test caption A",false,false,11100));
        call(s,"applyScan",new Class<?>[]{result},observedSnapchatFrame(s,"Test caption A",false,false,12000));
        check(sink.detected==1,"observed geometry with synthetic accessible footer establishes baseline");
        snapchatSource(s,13000);
        call(s,"applyScan",new Class<?>[]{result},observedSnapchatFrame(s,"Test caption B",false,false,13100));
        call(s,"applyScan",new Class<?>[]{result},observedSnapchatFrame(s,"Test caption B",false,false,14000));
        check(sink.detected==2&&ProbeStore.counter.accepted==1,"real geometry plus synthetic footer changes counts once");
        Object sparse=observedSnapchatFrame(s,"Test caption C",true,false,15000);
        check(!(Boolean)get(get(sparse,"snapshot"),"snapchatConfirmed"),"two action groups are insufficient for layout confirmation");
        Object panel=observedSnapchatFrame(s,"Test caption C",false,true,16000);
        check(!(Boolean)get(get(panel,"snapshot"),"snapchatConfirmed")&&(Integer)get(get(panel,"snapshot"),"fields")==0,"editable panels block both confirmation and content reads");
    }
    static android.view.accessibility.AccessibilityNodeInfo browserNode(String id,String label,Object...kids)throws Exception {
        Object n=node(id,label,true,kids);set(n,"pkg","com.android.chrome");
        return (android.view.accessibility.AccessibilityNodeInfo)n;
    }
    static void browserReaderGuards()throws Exception {
        android.view.accessibility.AccessibilityNodeInfo bar=browserNode("com.android.chrome:id/url_bar","m.youtube.com");
        set(bar,"description","https://m.youtube.com/shorts/dQw4w9WgXcQ");
        android.view.accessibility.AccessibilityNodeInfo root=browserNode("","",bar);
        BrowserReader.Result reading=BrowserReader.read(root,"com.android.chrome",new byte[32]);
        check(reading.state==BrowserSignals.SHORTS && reading.mode.equals("url"),"full URL in toolbar description recovers trimmed display");
        set(bar,"focused",true);
        check(BrowserReader.read(root,"com.android.chrome",new byte[32]).state==BrowserSignals.SHORTS,"focused read-only toolbar is not an editing field");
        set(bar,"editable",true);set(bar,"reads",0);
        reading=BrowserReader.read(root,"com.android.chrome",new byte[32]);
        check(reading.state==BrowserSignals.EDITING && (Integer)get(bar,"reads")==0,"typed address is never inspected");
        set(bar,"editable",false);set(bar,"focused",false);set(bar,"description","");
        android.view.accessibility.AccessibilityNodeInfo title=browserNode("video-title","Synthetic title A");
        android.view.accessibility.AccessibilityNodeInfo player=browserNode("reel-video-renderer","",title);
        android.view.accessibility.AccessibilityNodeInfo web=browserNode("","",player);set(web,"type","android.webkit.WebView");
        root=browserNode("","",bar,web);
        reading=BrowserReader.read(root,"com.android.chrome",new byte[32]);
        check(reading.state==BrowserSignals.SHORTS && reading.mode.equals("player-title") && !reading.identity.contains("Synthetic"),"YouTube domain plus actual reel subtree yields hashed identity");
        set(player,"id","shorts-tab");set(title,"reads",0);
        check(BrowserReader.read(root,"com.android.chrome",new byte[32]).identity==null && (Integer)get(title,"reads")==0,"a Shorts navigation tab cannot confirm a player or authorize label reads");
        set(player,"id","reel-video-renderer");set(bar,"label","example.com");set(title,"reads",0);
        check(BrowserReader.read(root,"com.android.chrome",new byte[32]).state==BrowserSignals.OTHER_SITE && (Integer)get(title,"reads")==0,"unrelated page content is never read");
        set(bar,"label","youtube.com/watch?v=dQw4w9WgXcQ");
        check(BrowserReader.read(root,"com.android.chrome",new byte[32]).identity==null,"regular YouTube watch pages remain excluded");
        set(bar,"label","youtube.com");set(title,"editable",true);set(title,"reads",0);
        check(BrowserReader.read(root,"com.android.chrome",new byte[32]).identity==null && (Integer)get(title,"reads")==0,"editable player fields are excluded");
    }
    static void browserCountingPipeline()throws Exception {
        Sink sink=new Sink();ProbeService s=service("com.android.chrome",sink);
        set(s,"browserPkg","com.android.chrome");
        Class<?>[] types={long.class,int.class,String.class,String.class};
        time(10000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"web:A","url");
        check(sink.detected==1 && (Boolean)get(s,"indicatorReady"),"browser baseline counts and enables badge");
        time(11000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"web:A","url");
        check(sink.detected==1,"unchanged URL cannot count twice");
        time(12000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"web:B","url");
        check(sink.detected==2 && ProbeStore.counter.accepted==1,"exact Shorts URL transition counts once");
        time(13000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"title:B","player-title");
        check(sink.detected==2,"switching URL to title source aliases the live item");
        time(14000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"title:C","player-title");
        time(15000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"title:C","player-title");
        check(sink.detected==2,"title edits without a fresh swipe cannot count");
        set(s,"browserMovementAt",16000L);
        time(16000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"title:D","player-title");
        time(16700);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"title:D","player-title");
        check(sink.detected==3,"stable title after verified browser scroll counts once");
        time(18000);call(s,"applyBrowser",types,0L,BrowserSignals.EDITING,null,"none");
        check(!(Boolean)get(s,"indicatorReady") && ((CountingEngine)get(s,"engine")).live(18000).paused,"typing pauses timing and hides badge");
        call(s,"invalidateReads",new Class<?>[]{});
        time(19000);call(s,"applyBrowser",types,0L,BrowserSignals.SHORTS,"web:stale","url");
        check(sink.detected==3 && !(Boolean)get(s,"browserQueued"),"stale browser results and pending polls are invalidated");
    }
    static void browserReadPipeline()throws Exception {
        Sink sink=new Sink();ProbeService s=service("",sink);
        set(s,"reader",new ReadLane((java.util.concurrent.Executor)Runnable::run));
        ProbeStore.setDiagnostics(true,false);
        android.view.accessibility.AccessibilityNodeInfo bar=browserNode("com.android.chrome:id/url_bar","youtube.com/shorts/dQw4w9WgXcQ");
        android.view.accessibility.AccessibilityNodeInfo root=browserNode("","",bar);
        Class.forName("android.accessibilityservice.AccessibilityService").getField("root").set(null,root);
        android.view.accessibility.AccessibilityEvent event=new android.view.accessibility.AccessibilityEvent();
        set(event,"pkg","com.android.chrome");set(event,"eventType",32);
        s.onAccessibilityEvent(event);pump(s,10000);
        check(ProbeStore.events==1 && ProbeStore.scans>=1 && ProbeStore.target.equals("com.android.chrome") && sink.detected==1,"real browser event/read/result paths populate diagnostics and count");
        set(bar,"label","youtube.com/shorts/ab_-123XYZ9");pump(s,10600);
        check(sink.detected==2,"active polling observes URL changes without a second accessibility event");
        set(event,"pkg","in.scrollcount.probe");s.onAccessibilityEvent(event);pump(s,12000);
        check(!(Boolean)get(s,"browserQueued") && get(s,"browserPkg")==null,"app switch stops browser polling");
    }
    public static void main(String[] args)throws Exception {
        snapshot=Class.forName("in.scrollcount.probe.ProbeService$Snapshot");result=Class.forName("in.scrollcount.probe.ProbeService$ScanResult");
        int failures=0;
        for(String name:new String[]{"browserReaderGuards","browserCountingPipeline","browserReadPipeline","observedSnapchatPipeline","snapchatPipeline","facebookPipeline","missingIndices","partialRead","hiddenWrapper","youtubeWrapper","optionalAliasBudget","staleSource","instagramProfile","reusedIndex","hiddenPlayerContainers","missingSourceMetadata","ancestorSource","eventPipeline","lateIndexedPipeline","noCountForActions","pendingSuperseded","legacyLayoutPipeline","legacyPreviewExcluded","overlayAffordancePipeline","actualOverlayPanels","siblingSheetPipeline","sourceBehindSlowTree"})try {
            Method test=ServicePipelineTest.class.getDeclaredMethod(name);test.invoke(null);
        }catch(InvocationTargetException e){failures++;System.err.println(name+": "+e.getCause());e.getCause().printStackTrace();}
        check(failures==0,"service pipeline regressions: "+failures);
        System.out.println("PASS: 27 service cases (including browser URL/title/privacy/lifecycle regressions) with supplied Snapchat 14.29 structural replay with unindexed Snapchat Spotlight and content-free metadata including redacted-ID Facebook snapshot/source/counting guards, modern/legacy/overlay/sibling-sheet layouts and 40 rapid indexed events during a blocked tree read (340 synthetic exposures), repeated/missing indices, missing/descendant sources, long views, hidden wrappers, actions, windows, privacy and partial-read guards");
    }
}
