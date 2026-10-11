package in.scrollcount.probe;
import java.util.Base64;
import java.net.URLEncoder;
public class FriendInvitationTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void bad(String s){try{FriendInvitation.parse(s);throw new AssertionError(s);}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  String c="a".repeat(32),server=FriendInvitation.DEFAULT_SERVER,link=FriendInvitation.link(server,c);
  check(FriendInvitation.parse(link).code.equals(c));check(FriendInvitation.parse("Join me on ScrollCount:\n"+link).server.equals(server));
  check(FriendInvitation.parse("scrollcount://invite/"+c+"?server="+URLEncoder.encode(server,"UTF-8")).code.equals(c));
  String old="SC1."+Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"server\":\""+server+"\",\"code\":\""+c+"\"}").getBytes("UTF-8"));check(FriendInvitation.parse(old).code.equals(c));
  for(String s:new String[]{"https://evil.example/invite/"+c,"https://fake.workers.dev@evil.example/invite/"+c,link+"?autoaccept=true",link+"#fragment",server+"/invite/short","scrollcount://invite/"+c+"?server=http%3A%2F%2Ftest.workers.dev","https://test.workers.dev:443/invite/"+c,"https://test.workers.dev/invite/../"+c})bad(s);
  System.out.println("PASS: HTTPS/app invitations, share messages, legacy compatibility, unsafe host and malformed link rejection");
 }
}
