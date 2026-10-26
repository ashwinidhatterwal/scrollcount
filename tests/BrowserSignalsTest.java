package in.scrollcount.probe;
import java.nio.charset.StandardCharsets; import java.util.Locale;
public class BrowserSignalsTest {
    static int fails=0;
    static void check(String in,int state,String id){
        BrowserSignals.Reading r=BrowserSignals.parse(in);
        boolean ok=r.state==state && java.util.Objects.equals(r.shortId,id);
        if(!ok){fails++;System.out.println("FAIL "+in+" -> "+r.state+" "+r.shortId);}
    }
    public static void main(String[] a){
        int S=BrowserSignals.SHORTS;
        check("https://m.youtube.com/shorts/dQw4w9WgXcQ",S,"dQw4w9WgXcQ");
        check("m.youtube.com/shorts/dQw4w9WgXcQ",S,"dQw4w9WgXcQ");
        check("youtube.com/shorts/dQw4w9WgXcQ?feature=share",S,"dQw4w9WgXcQ");
        check("https://www.youtube.com/shorts/ab_-123XYZ9/",S,"ab_-123XYZ9");
        check("HTTPS://WWW.YOUTUBE.COM/shorts/dQw4w9WgXcQ#t=3",S,"dQw4w9WgXcQ");
        check("https://m.youtube.com/watch?v=dQw4w9WgXcQ",BrowserSignals.YOUTUBE_OTHER,null);
        check("m.youtube.com/shorts",BrowserSignals.YOUTUBE_OTHER,null);
        check("m.youtube.com/shorts/short",BrowserSignals.YOUTUBE_OTHER,null);
        check("m.youtube.com/@creator/shorts",BrowserSignals.YOUTUBE_OTHER,null);
        check("m.youtube.com",BrowserSignals.YOUTUBE_HOST_ONLY,null);
        check("https://youtube.com/",BrowserSignals.YOUTUBE_HOST_ONLY,null);
        check("music.youtube.com/shorts/dQw4w9WgXcQ",BrowserSignals.OTHER_SITE,null);
        check("evil.com/youtube.com/shorts/dQw4w9WgXcQ",BrowserSignals.OTHER_SITE,null);
        check("youtube.com.evil.com/shorts/dQw4w9WgXcQ",BrowserSignals.OTHER_SITE,null);
        check("user@youtube.com/shorts/dQw4w9WgXcQ",S,"dQw4w9WgXcQ");
        check("youtube shorts dQw4w9WgXcQ",BrowserSignals.OTHER_SITE,null);
        check("   ",BrowserSignals.UNAVAILABLE,null);
        check(null,BrowserSignals.UNAVAILABLE,null);
        byte[] salt=new byte[32]; byte[] salt2=new byte[32]; salt2[0]=1;
        String i1=BrowserSignals.identity(salt,"dQw4w9WgXcQ"),i2=BrowserSignals.identity(salt,"dQw4w9WgXcR"),i3=BrowserSignals.identity(salt2,"dQw4w9WgXcQ");
        if(i1.equals(BrowserSignals.identity(salt,"dQw4w9WgXcQ"))&&!i1.equals(i2)&&!i1.equals(i3)&&i1.startsWith("web:")&&!i1.contains("dQw4"))System.out.println("identity ok "+i1);else{fails++;System.out.println("FAIL identity");}
        if(BrowserSignals.urlBarId("com.android.chrome").equals("com.android.chrome:id/url_bar")&&BrowserSignals.urlBarId("com.google.android.youtube")==null&&!BrowserSignals.browser(null))System.out.println("package map ok");else{fails++;System.out.println("FAIL pkgmap");}
        if(fails>0)throw new AssertionError(fails+" failures");
        System.out.println("PASS: production browser URL parser");
    }
}
