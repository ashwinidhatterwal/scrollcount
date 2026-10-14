package in.scrollcount.probe;
import java.lang.reflect.*;
import android.content.SharedPreferences;
/** Production orchestration: never replace applySource/applyScan with a test algorithm. */
public final class ServicePipelineTest {
    static sun.misc.Unsafe unsafe;
    static Class<?> snapshot,result;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void set(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
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
        ProbeService s=(ProbeService)unsafe.allocateInstance(ProbeService.class);
        set(s,"preferences",Proxy.newProxyInstance(ServicePipelineTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->m.getName().equals("getInt")?3:true));
        set(s,"engine",new CountingEngine(sink));
        set(s,"handler",new android.os.Handler());
        set(s,"reader",new ReadLane((java.util.concurrent.Executor)r->{}));
        set(s,"readTimers",new java.util.HashSet<Runnable>());
        set(s,"instagramIdentity",new InstagramIdentity());
        set(s,"instagramFast",new FastFingerprintTracker());
        set(s,"youtubeFast",new FastFingerprintTracker());
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
        set(out,"snapshot",snap);set(out,"index",index);set(out,"hash",hash);
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
    public static void main(String[] args)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        snapshot=Class.forName("in.scrollcount.probe.ProbeService$Snapshot");result=Class.forName("in.scrollcount.probe.ProbeService$ScanResult");
        int failures=0;
        for(String name:new String[]{"missingIndices","partialRead","hiddenWrapper","youtubeWrapper","optionalAliasBudget","staleSource"})try {
            Method test=ServicePipelineTest.class.getDeclaredMethod(name);test.invoke(null);
        }catch(InvocationTargetException e){failures++;System.err.println(name+": "+e.getCause());}
        check(failures==0,"service pipeline regressions: "+failures);
        System.out.println("PASS: actual service recovers missing indices without duplicates, pauses partial reads, resumes same reel, closes confirmed exits, traverses hidden wrappers and excludes editable content");
    }
}
