package in.scrollcount.probe;
import android.content.SharedPreferences;
import java.lang.reflect.*;
/** Invoke the real service admission helper without starting Android services/IPC. */
public final class ReadFenceTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        ProbeService service=(ProbeService)unsafe.allocateInstance(ProbeService.class);
        boolean[] running={true};int[] consent={3};
        SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(ReadFenceTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)-> {
            if(m.getName().equals("getBoolean"))return running[0];
            if(m.getName().equals("getInt"))return consent[0];
            throw new AssertionError("Unexpected preference read "+m.getName());
        });
        Field preference=ProbeService.class.getDeclaredField("preferences");preference.setAccessible(true);preference.set(service,prefs);
        Field epoch=ProbeService.class.getDeclaredField("readEpoch");epoch.setAccessible(true);epoch.setLong(service,7);
        Method valid=ProbeService.class.getDeclaredMethod("currentRead",String.class,long.class);valid.setAccessible(true);
        ProbeStore.target=InstagramSignals.PACKAGE;
        check((Boolean)valid.invoke(service,InstagramSignals.PACKAGE,7L),"current read admitted");
        epoch.setLong(service,8);check(!(Boolean)valid.invoke(service,InstagramSignals.PACKAGE,7L),"late read rejected after lock/exit/reset invalidation");
        ProbeStore.target=YouTubeSignals.PACKAGE;check(!(Boolean)valid.invoke(service,InstagramSignals.PACKAGE,8L),"old package rejected");
        running[0]=false;check(!(Boolean)valid.invoke(service,YouTubeSignals.PACKAGE,8L),"paused counting rejects results");running[0]=true;
        consent[0]=2;check(!(Boolean)valid.invoke(service,YouTubeSignals.PACKAGE,8L),"revoked disclosure rejects results");consent[0]=3;
        Field reset=ProbeService.class.getDeclaredField("resettingHistory");reset.setAccessible(true);reset.setBoolean(service,true);
        check(!(Boolean)valid.invoke(service,YouTubeSignals.PACKAGE,8L),"history reset rejects old detections");reset.setBoolean(service,false);
        Field destroyed=ProbeService.class.getDeclaredField("destroyed");destroyed.setAccessible(true);destroyed.setBoolean(service,true);
        check(!(Boolean)valid.invoke(service,YouTubeSignals.PACKAGE,8L),"destroyed service cannot be revived by late callbacks");
        System.out.println("PASS: actual service result admission rejects stale epochs, old packages, pause, consent withdrawal, reset and destroyed service");
    }
}
