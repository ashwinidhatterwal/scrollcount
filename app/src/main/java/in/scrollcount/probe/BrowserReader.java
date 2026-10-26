package in.scrollcount.probe;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

/** Bounded browser read. URLs and page titles never leave this reader unhashed. */
final class BrowserReader {
    static final class Result {
        int state=BrowserSignals.UNAVAILABLE,nodes;
        String identity,mode="none";
        boolean bar,editing,player,title,cut;
        String metadata(){return "browserState="+state+", bar="+bar+", editing="+editing+", mode="+mode
                +", shortsPlayer="+player+", titleAvailable="+title+", nodes="+nodes+", cut="+cut;}
    }
    static Result read(AccessibilityNodeInfo root,String pkg,byte[] salt) {
        Result out=new Result();
        if(root==null || !pkg.equals(String.valueOf(root.getPackageName())))return out;
        BrowserSignals.Reading address=null;
        for(String id:BrowserSignals.urlBarIds(pkg)) {
            java.util.List<AccessibilityNodeInfo> bars=root.findAccessibilityNodeInfosByViewId(id);
            try {
                for(AccessibilityNodeInfo bar:bars) {
                    if(!bar.isVisibleToUser())continue;
                    out.bar=true;
                    if(bar.isPassword() || (bar.isFocused() && bar.isEditable())) {out.editing=true;continue;}
                    BrowserSignals.Reading text=BrowserSignals.parse(bar.getText());
                    // The read-only toolbar may expose a full URL as its description.
                    BrowserSignals.Reading description=BrowserSignals.parse(bar.getContentDescription());
                    BrowserSignals.Reading next=text;
                    if((text.state==BrowserSignals.UNAVAILABLE || text.state==BrowserSignals.YOUTUBE_HOST_ONLY)
                            && description.state==BrowserSignals.SHORTS)next=description;
                    if(address==null || next.state==BrowserSignals.SHORTS
                            || (address.state==BrowserSignals.UNAVAILABLE && next.state!=BrowserSignals.UNAVAILABLE))address=next;
                }
            } finally {for(AccessibilityNodeInfo bar:bars)bar.recycle();}
        }
        if(out.editing){out.state=BrowserSignals.EDITING;return out;}
        if(address!=null)out.state=address.state;
        if(out.state==BrowserSignals.SHORTS){out.identity=BrowserSignals.identity(salt,address.shortId);out.mode="url";return out;}
        // No page labels are read on arbitrary sites, while typing, or on regular watch pages.
        if(out.state!=BrowserSignals.YOUTUBE_HOST_ONLY)return out;
        Page page=new Page(out,salt);
        page.walk(root,0,false,false);
        out.player=page.player;out.title=page.title!=null;
        if(!out.cut && !page.editable && page.player && page.title!=null) {
            out.state=BrowserSignals.SHORTS;out.identity=page.title;out.mode="player-title";
        }
        return out;
    }
    private static final class Page {
        final Result result;final byte[] salt;
        boolean player,editable;
        String title;
        Page(Result result,byte[] salt){this.result=result;this.salt=salt;}
        void walk(AccessibilityNodeInfo node,int depth,boolean web,boolean playerScope) {
            if(depth>32 || ++result.nodes>220){result.cut=true;return;}
            if(!node.isVisibleToUser())return;
            if(node.isEditable() || node.isPassword()){editable=true;return;}
            web=web || "android.webkit.WebView".contentEquals(String.valueOf(node.getClassName()));
            if(web) {
                String id=String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT);
                // A Shorts tab/link alone is insufficient: require the actual reel player container.
                if(id.contains("reel-video-renderer") || id.contains("shorts-player") || id.contains("reel-player")){player=true;playerScope=true;}
                if(playerScope && (id.contains("reel-title") || id.contains("shorts-title") || id.endsWith("video-title"))) {
                    CharSequence value=node.getText();if(value==null || value.length()==0)value=node.getContentDescription();
                    if(value!=null && value.length()>0 && value.length()<=2000 && title==null)
                        title=BrowserSignals.identity(salt,"page-title\u0000"+value);
                }
            }
            for(int i=0;i<node.getChildCount()&&!result.cut;i++) {
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)try{walk(child,depth+1,web,playerScope);}finally{child.recycle();}
            }
        }
    }
}
