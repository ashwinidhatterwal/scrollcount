package in.scrollcount.probe;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
/** Per-recipient ECDH/HKDF keys; AES-GCM authenticates payload and both profile IDs. */
final class FriendCipher {
    static KeyPair create() throws GeneralSecurityException {KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));return g.generateKeyPair();}
    static String encode(byte[] value){return Base64.getEncoder().encodeToString(value);}
    static byte[] decode(String value){return Base64.getDecoder().decode(value);}
    static KeyPair restore(String privateKey,String publicKey) throws GeneralSecurityException {
        KeyFactory f=KeyFactory.getInstance("EC");return new KeyPair(f.generatePublic(new X509EncodedKeySpec(decode(publicKey))),f.generatePrivate(new PKCS8EncodedKeySpec(decode(privateKey))));
    }
    private static byte[] context(String from,String to){
        if(!from.matches("[a-f0-9]{32}")||!to.matches("[a-f0-9]{32}")||from.equals(to))throw new IllegalArgumentException("Invalid sharing identity");
        return ("scrollcount-snapshot-v1|"+from+"|"+to).getBytes(StandardCharsets.UTF_8);
    }
    private static SecretKey key(KeyPair own,String other,String from,String to) throws GeneralSecurityException {
        PublicKey remote=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(decode(other)));
        if(!(remote instanceof ECPublicKey)||!((ECPublicKey)remote).getParams().getCurve().equals(((ECPublicKey)own.getPublic()).getParams().getCurve()))throw new InvalidKeyException("Wrong sharing curve");
        KeyAgreement agree=KeyAgreement.getInstance("ECDH");agree.init(own.getPrivate());agree.doPhase(remote,true);
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("scrollcount-snapshot-v1".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] prk=mac.doFinal(agree.generateSecret());
        mac.init(new SecretKeySpec(prk,"HmacSHA256"));mac.update(context(from,to));mac.update((byte)1);
        return new SecretKeySpec(mac.doFinal(),"AES");
    }
    static final class Envelope {final String iv,ciphertext;Envelope(String i,String c){iv=i;ciphertext=c;}}
    static Envelope encrypt(KeyPair own,String other,String from,String to,String payload) throws GeneralSecurityException {
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(own,other,from,to),new GCMParameterSpec(128,iv));cipher.updateAAD(context(from,to));return new Envelope(encode(iv),encode(cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8))));
    }
    static String decrypt(KeyPair own,String other,String from,String to,String iv,String ciphertext) throws GeneralSecurityException {
        byte[] nonce=decode(iv),bytes=decode(ciphertext);if(nonce.length!=12||bytes.length<16||bytes.length>1536)throw new InvalidAlgorithmParameterException("Invalid encrypted snapshot");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(own,other,from,to),new GCMParameterSpec(128,nonce));cipher.updateAAD(context(from,to));return new String(cipher.doFinal(bytes),StandardCharsets.UTF_8);
    }
}
