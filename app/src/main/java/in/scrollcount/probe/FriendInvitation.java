package in.scrollcount.probe;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.*;
/** Untrusted links are validated before any network request; old invitations remain usable. */
final class FriendInvitation {
    static final String DEFAULT_SERVER="https://scrollcount-friends.actionbridge.workers.dev";
    final String server,code;
    private FriendInvitation(String server,String code){
        if(!validServer(server)||!code.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid invitation");
        this.server=server.endsWith("/")?server.substring(0,server.length()-1):server;this.code=code;
    }
    private static boolean validServer(String server){
        try{URI u=new URI(server);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getHost().endsWith(".workers.dev")&&u.getPort()==-1&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&(u.getPath().isEmpty()||u.getPath().equals("/"));}catch(Exception e){return false;}
    }
    static String link(String server,String code){FriendInvitation p=new FriendInvitation(server,code);return p.server+"/invite/"+p.code;}
    static FriendInvitation parse(String text){
        if(text==null||text.length()>4096)throw new IllegalArgumentException();
        String raw=text.trim();
        // Accept copied share messages as well as a bare link.
        Matcher link=Pattern.compile("https://[^\\s]+/invite/[a-f0-9]{32}").matcher(raw);
        if(!raw.startsWith("https://")&&link.find())raw=link.group();
        try{
            if(raw.startsWith("https://")){
                URI u=new URI(raw);if(u.getQuery()!=null||u.getFragment()!=null||u.getUserInfo()!=null||u.getPort()!=-1||!u.getPath().matches("/invite/[a-f0-9]{32}"))throw new IllegalArgumentException();
                return new FriendInvitation("https://"+u.getHost(),u.getPath().substring(8));
            }
            if(raw.startsWith("scrollcount://")){
                URI u=new URI(raw);if(!"invite".equals(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1||u.getFragment()!=null||!u.getPath().matches("/[a-f0-9]{32}"))throw new IllegalArgumentException();
                String query=u.getRawQuery();if(query==null||!query.startsWith("server=")||query.contains("&"))throw new IllegalArgumentException();
                return new FriendInvitation(URLDecoder.decode(query.substring(7),"UTF-8"),u.getPath().substring(1));
            }
            int start=raw.indexOf("SC1.");if(start<0)throw new IllegalArgumentException();
            String json=new String(Base64.getUrlDecoder().decode(raw.substring(start+4).trim()),StandardCharsets.UTF_8);
            return new FriendInvitation(field(json,"server"),field(json,"code"));
        }catch(Exception e){throw new IllegalArgumentException("Invalid invitation");}
    }
    private static String field(String json,String name){Matcher m=Pattern.compile("\""+name+"\"\\s*:\\s*\"([^\"]*)\"").matcher(json);if(!m.find())throw new IllegalArgumentException();return m.group(1).replace("\\/","/");}
}
