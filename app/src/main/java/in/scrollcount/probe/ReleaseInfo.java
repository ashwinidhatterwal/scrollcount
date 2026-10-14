package in.scrollcount.probe;
/** Generated from release-config.json by tools/configure_release.py. */
final class ReleaseInfo {
    static final String OPERATOR = "Ashwini Dhatterwal";
    static final String EMAIL = "ashwinidhatterwal@gmail.com";
    static final String EFFECTIVE_DATE = "2026-10-14";
    static String summary() {
        return "\n\nOperator: "+OPERATOR+"\nEffective date: "+EFFECTIVE_DATE
            +"\nPrivacy contact: "+(EMAIL.isEmpty()?"Not configured for publication":EMAIL)
            +"\nPublic policy: https://scrollcount-friends.actionbridge.workers.dev/privacy"
            +"\nAccount deletion: https://scrollcount-friends.actionbridge.workers.dev/delete";
    }
}
