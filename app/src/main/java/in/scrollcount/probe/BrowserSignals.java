package in.scrollcount.probe;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Recognises YouTube Shorts, Instagram Reels and Facebook Reels opened in a mobile browser.
 *
 * Only the address-bar text is examined, in memory, and only to answer two questions: is this a
 * supported short-video page, and its platform/video id. The URL is never stored or logged; callers keep just
 * a salted HMAC of the id, exactly like the in-app identities.
 *
 * Pure Java (no Android types) so it can be unit-tested off-device.
 */
final class BrowserSignals {
    /** Address bar could not be read (page loading, window changing, bar not found). */
    static final int UNAVAILABLE = 0;
    /** Address bar is being edited; never read while the user may be typing. */
    static final int EDITING = 1;
    /** Some other site. */
    static final int OTHER_SITE = 2;
    /** A supported site page that is not an individual Short/Reel (legacy state name). */
    static final int YOUTUBE_OTHER = 3;
    /**
     * Only a supported domain is visible. Some browsers trim the address bar, in which
     * case a Short cannot be told apart from the home page.
     */
    static final int YOUTUBE_HOST_ONLY = 4;
    /** Individual YouTube Short, Instagram Reel or Facebook Reel; see Reading.platform. */
    static final int SHORTS = 5;

    /** package, address-bar view id. Chrome's id is widely used; the others follow the same pattern or are best-effort. */
    static final String[][] BROWSERS = {
        {"com.android.chrome", "com.android.chrome:id/url_bar"},
        {"com.chrome.dev", "com.chrome.dev:id/url_bar"},
        {"com.chrome.canary", "com.chrome.canary:id/url_bar"},
        {"com.chrome.beta", "com.chrome.beta:id/url_bar"},
        {"com.brave.browser", "com.brave.browser:id/url_bar"},
        {"com.microsoft.emmx", "com.microsoft.emmx:id/url_bar"},
        {"com.vivaldi.browser", "com.vivaldi.browser:id/url_bar"},
        {"com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text"},
        {"com.opera.browser", "com.opera.browser:id/url_field"},
        {"com.opera.mini.native", "com.opera.mini.native:id/url_field"},
        {"com.vivo.browser", "com.vivo.browser:id/url_bar"},
        {"com.vivo.browser", "com.vivo.browser:id/url"},
        {"org.mozilla.firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"},
    };

    private BrowserSignals() {
    }

    /** The address-bar view id for a supported browser package, or null if it is not one. */
    static String urlBarId(String pkg) {
        if (pkg == null) return null;
        for (String[] browser : BROWSERS) if (browser[0].equals(pkg)) return browser[1];
        return null;
    }

    static String[] urlBarIds(String pkg) {
        java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        for(String[] browser:BROWSERS)if(browser[0].equals(pkg))ids.add(browser[1]);
        // Read-only and edit-mode toolbar variants used by Chromium/Gecko releases.
        if(!ids.isEmpty())for(String name:new String[]{"url_bar","url_bar_text","location_bar_edit_text","mozac_browser_toolbar_url_view","mozac_browser_toolbar_edit_url_view"}) {
            String id=pkg+":id/"+name;if(!ids.contains(id))ids.add(id);
        }
        return ids.toArray(new String[0]);
    }

    static boolean browser(String pkg) {
        return urlBarId(pkg) != null;
    }

    /** Result of {@link #parse}: a state, plus the Short id when the state is SHORTS. */
    static final class Reading {
        final int state;
        final String shortId;
        final String platform;

        Reading(int state, String shortId) {
            this(state,shortId,"YouTube");
        }
        Reading(int state,String shortId,String platform){
            this.state=state;this.shortId=shortId;this.platform=platform;
        }
    }

    static boolean platform(String value){return "YouTube".equals(value)||"Instagram".equals(value)||"Facebook".equals(value);}
    static Reading parse(CharSequence raw) {
        if(raw==null||raw.toString().trim().isEmpty())return new Reading(UNAVAILABLE,null,"");
        String text=raw.toString().trim();
        for(int i=0;i<text.length();i++)if(Character.isWhitespace(text.charAt(i)))return new Reading(OTHER_SITE,null,"");
        try{
            java.net.URI uri=new java.net.URI(text.contains("://")?text:"https://"+text);
            if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))||uri.getRawUserInfo()!=null)
                return new Reading(OTHER_SITE,null,"");
            String host=uri.getHost();if(host==null)return new Reading(OTHER_SITE,null,"");host=host.toLowerCase(Locale.ROOT);
            String platform=host.equals("youtube.com")||host.equals("www.youtube.com")||host.equals("m.youtube.com")?"YouTube":
                    host.equals("instagram.com")||host.equals("www.instagram.com")?"Instagram":
                    host.equals("facebook.com")||host.equals("www.facebook.com")||host.equals("m.facebook.com")||host.equals("web.facebook.com")?"Facebook":"";
            if(platform.isEmpty())return new Reading(OTHER_SITE,null,"");
            String path=uri.getRawPath();if(path==null||path.isEmpty()||path.equals("/"))return new Reading(YOUTUBE_HOST_ONLY,null,platform);
            // Paths must identify exactly one short video; query and fragment never change identity.
            String[] parts=path.split("/",-1);
            if((parts.length==3||parts.length==4&&parts[3].isEmpty())&&parts[0].isEmpty()){
                String kind=parts[1],id=parts[2];
                boolean video=platform.equals("YouTube")?kind.equals("shorts")&&validId(id):
                        platform.equals("Instagram")?(kind.equals("reel")||kind.equals("reels"))&&id.matches("[A-Za-z0-9_-]{5,64}")
                            &&!id.matches("audio|saved|liked|create|following|explore|videos|for_you"):
                        (kind.equals("reel")||kind.equals("reels"))&&id.matches("[0-9]{5,32}");
                if(video)return new Reading(SHORTS,id,platform);
            }
            return new Reading(YOUTUBE_OTHER,null,platform);
        }catch(java.net.URISyntaxException error){return new Reading(OTHER_SITE,null,"");}
    }

    private static boolean validId(String id) {
        if (id.length() != 11) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) return false;
        }
        return true;
    }

    /** Salted, one-way identity for a Short; the id itself never leaves this call. */
    static String identity(byte[] salt, String shortId) {return identity(salt,"YouTube",shortId);}
    static String identity(byte[] salt,String platform,String shortId){
        if(!platform(platform))throw new IllegalArgumentException("Unsupported platform");
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(salt, "HmacSHA256"));
            byte[] digest = mac.doFinal(("web-short\u0000" + platform + "\u0000" + shortId).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("web:");
            for (int i = 0; i < 12; i++) out.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return out.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
