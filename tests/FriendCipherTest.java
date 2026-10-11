package in.scrollcount.probe;
import java.security.*;
import java.security.spec.*;
public class FriendCipherTest {
 interface Attempt{void run()throws Exception;}
 static void reject(Attempt a)throws Exception{try{a.run();throw new AssertionError("Accepted tampering");}catch(GeneralSecurityException|IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  KeyPair a=FriendCipher.create(),b=FriendCipher.create(),c=FriendCipher.create();String ai="a".repeat(32),bi="b".repeat(32),ci="c".repeat(32),ap=FriendCipher.encode(a.getPublic().getEncoded()),bp=FriendCipher.encode(b.getPublic().getEncoded());String data="{\"reels\":20,\"shorts\":40}";
  FriendCipher.Envelope e=FriendCipher.encrypt(a,bp,ai,bi,data),e2=FriendCipher.encrypt(a,bp,ai,bi,data);
  if(!data.equals(FriendCipher.decrypt(b,ap,ai,bi,e.iv,e.ciphertext))||e.iv.equals(e2.iv))throw new AssertionError("Roundtrip/random nonce");
  byte[] corrupted=FriendCipher.decode(e.ciphertext);corrupted[0]^=1;
  reject(()->FriendCipher.decrypt(b,ap,ai,bi,e.iv,FriendCipher.encode(corrupted)));
  reject(()->FriendCipher.decrypt(b,ap,ai,ci,e.iv,e.ciphertext));
  reject(()->FriendCipher.decrypt(b,ap,bi,ai,e.iv,e.ciphertext));
  reject(()->FriendCipher.decrypt(c,ap,ai,bi,e.iv,e.ciphertext));
  reject(()->FriendCipher.decrypt(b,ap,ai,bi,"AA==",e.ciphertext));
  KeyPair restored=FriendCipher.restore(FriendCipher.encode(b.getPrivate().getEncoded()),bp);
  if(!data.equals(FriendCipher.decrypt(restored,ap,ai,bi,e.iv,e.ciphertext)))throw new AssertionError("Restored key");
  FriendCipher.Envelope reverse=FriendCipher.encrypt(b,ap,bi,ai,data);
  if(!data.equals(FriendCipher.decrypt(a,bp,bi,ai,reverse.iv,reverse.ciphertext)))throw new AssertionError("Reverse direction");
  KeyPairGenerator gen=KeyPairGenerator.getInstance("EC");gen.initialize(new ECGenParameterSpec("secp384r1"));String wrong=FriendCipher.encode(gen.generateKeyPair().getPublic().getEncoded());reject(()->FriendCipher.encrypt(a,wrong,ai,bi,data));
  System.out.println("PASS: encrypted roundtrip, independent nonce, authentication/tampering, recipient binding, wrong key/curve and private key restoration");
 }
}
