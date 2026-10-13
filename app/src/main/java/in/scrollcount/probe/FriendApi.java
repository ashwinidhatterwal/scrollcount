package in.scrollcount.probe;
import android.os.Handler;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;
/** Optional HTTPS pairing and encrypted snapshots; never receives plaintext totals. */
final class FriendApi {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private boolean closed;
    interface Result{void done(JSONObject response,String error);}
    static boolean validServer(String value){
        try{URI u=new URI(value);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getHost().endsWith(".workers.dev")&&u.getPort()==-1&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&(u.getPath().isEmpty()||u.getPath().equals("/"));}catch(Exception e){return false;}
    }
    void request(FriendStore store,String route,String method,JSONObject data,Result result){
        String server=store.server(),token=store.prefs.getString("token","");
        if(closed)return;
        io.execute(()->{
            if(!server.equals(store.server())||!token.equals(store.prefs.getString("token",""))){main.post(()->{if(!closed)CallbackGuard.run(()->result.done(null,"Sharing profile changed. Try again."),e->ServiceHealth.failure("sharing result",e));});return;}
            JSONObject response=null;String error="";HttpURLConnection c=null;
            try{
                if(!validServer(server))throw new IOException("Set up the sharing server first");
                c=(HttpURLConnection)new URL(server+route).openConnection();
                c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);
                c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");
                if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
                if(data!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(data.toString().getBytes("UTF-8"));}}
                int status=c.getResponseCode();InputStream stream=status>=200&&status<300?c.getInputStream():c.getErrorStream();
                if(stream==null)throw new IOException("Sharing service is unavailable");
                StringBuilder text=new StringBuilder();try(Reader in=new InputStreamReader(stream,"UTF-8")){char[] b=new char[2048];int n;while((n=in.read(b))!=-1){if(text.length()+n>65536)throw new IOException("Invalid server response");text.append(b,0,n);}}
                response=new JSONObject(text.toString());if(status<200||status>=300)error=response.optString("error","Sharing service is unavailable");
            }catch(Exception e){error=e instanceof IOException?"Could not connect. Check your internet and try again.":"Invalid sharing response";}
            finally{if(c!=null)c.disconnect();}
            JSONObject finalResponse=response;String finalError=error;main.post(()->{if(!closed)CallbackGuard.run(()->result.done(finalResponse,finalError),e->ServiceHealth.failure("sharing result",e));});
        });
    }
    void close(){closed=true;io.shutdown();}
}
