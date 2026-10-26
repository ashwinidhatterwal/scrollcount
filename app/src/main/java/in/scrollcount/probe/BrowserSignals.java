package in.scrollcount.probe;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Recognises YouTube Shorts opened in a mobile browser.
 *
 * Only the address-bar text is examined, in memory, and only to answer two questions: is this a
 * youtube.com/shorts/<id> page, and which id. The URL is never stored or logged; callers keep just
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
    /** A YouTube page that is not a Short (watch page, home, search...). */
    static final int YOUTUBE_OTHER = 3;
    /**
     * Only "youtube.com" is visible. Some browsers trim the address bar to the domain, in which
     * case a Short cannot be told apart from the home page.
     */
    static final int YOUTUBE_HOST_ONLY = 4;
    /** youtube.com/shorts/<11-character id>. */
    static final int SHORTS = 5;

    /** package, address-bar view id. Chrome's id is widely used; the others follow the same pattern or are best-effort. */
    private static final String[][] BROWSERS = {
        {"com.android.chrome", "com.android.chrome:id/url_bar"},
        {"com.chrome.beta", "com.chrome.beta:id/url_bar"},
        {"com.brave.browser", "com.brave.browser:id/url_bar"},
        {"com.microsoft.emmx", "com.microsoft.emmx:id/url_bar"},
        {"com.vivaldi.browser", "com.vivaldi.browser:id/url_bar"},
        {"com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text"},
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

    static boolean browser(String pkg) {
        return urlBarId(pkg) != null;
    }

    /** Result of {@link #parse}: a state, plus the Short id when the state is SHORTS. */
    static final class Reading {
        final int state;
        final String shortId;

        Reading(int state, String shortId) {
            this.state = state;
            this.shortId = shortId;
        }
    }

    static Reading parse(CharSequence raw) {
        if (raw == null) return new Reading(UNAVAILABLE, null);
        String text = raw.toString().trim();
        if (text.isEmpty()) return new Reading(UNAVAILABLE, null);
        // Drop the scheme if present.
        int scheme = text.indexOf("://");
        if (scheme >= 0 && scheme <= 10) text = text.substring(scheme + 3);
        // Anything with whitespace is a search query or label, not a URL.
        for (int i = 0; i < text.length(); i++) if (Character.isWhitespace(text.charAt(i))) return new Reading(OTHER_SITE, null);
        int slash = text.indexOf('/');
        String host = slash < 0 ? text : text.substring(0, slash);
        String rest = slash < 0 ? "" : text.substring(slash);
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        int port = host.indexOf(':');
        if (port >= 0) host = host.substring(0, port);
        host = host.toLowerCase(Locale.ROOT);
        if (!(host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")))
            return new Reading(OTHER_SITE, null);
        int cut = rest.length();
        int query = rest.indexOf('?'), fragment = rest.indexOf('#');
        if (query >= 0) cut = Math.min(cut, query);
        if (fragment >= 0) cut = Math.min(cut, fragment);
        String path = rest.substring(0, cut);
        if (path.isEmpty() || path.equals("/")) return new Reading(YOUTUBE_HOST_ONLY, null);
        if (path.startsWith("/shorts/")) {
            String id = path.substring("/shorts/".length());
            int end = id.indexOf('/');
            if (end >= 0) id = id.substring(0, end);
            if (validId(id)) return new Reading(SHORTS, id);
        }
        return new Reading(YOUTUBE_OTHER, null);
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
    static String identity(byte[] salt, String shortId) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(salt, "HmacSHA256"));
            byte[] digest = mac.doFinal(("web-short\u0000" + shortId).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("web:");
            for (int i = 0; i < 12; i++) out.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return out.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
