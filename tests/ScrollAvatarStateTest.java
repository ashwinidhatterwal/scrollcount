package in.scrollcount.probe;

public final class ScrollAvatarStateTest {
    public static void main(String[] args) {
        // 1. Frame rate requirement: must be at least 15 FPS (<= 66ms per frame)
        if (ScrollAvatarState.FRAME_INTERVAL_MS > 66L || ScrollAvatarState.FRAME_INTERVAL_MS <= 0L) {
            throw new AssertionError("Frame interval must be >= 15 FPS: " + ScrollAvatarState.FRAME_INTERVAL_MS);
        }

        // 2. Title rotation interval must be 5,000 ms (5 seconds)
        if (ScrollAvatarState.TITLE_ROTATION_MS != 5000L) {
            throw new AssertionError("Title rotation interval must be 5000ms");
        }

        // 3. Exact tier boundaries: 0, 1..99, 100..249, 250..499, 500..699, 700+
        ScrollAvatarState zen = ScrollAvatarState.evaluate(0, 150, 0L);
        ScrollAvatarState fresh99 = ScrollAvatarState.evaluate(99, 150, 0L);
        ScrollAvatarState cruising100 = ScrollAvatarState.evaluate(100, 150, 0L);
        ScrollAvatarState cruising249 = ScrollAvatarState.evaluate(249, 150, 0L);
        ScrollAvatarState trance250 = ScrollAvatarState.evaluate(250, 150, 0L);
        ScrollAvatarState trance499 = ScrollAvatarState.evaluate(499, 150, 0L);
        ScrollAvatarState fatigued500 = ScrollAvatarState.evaluate(500, 150, 0L);
        ScrollAvatarState fatigued699 = ScrollAvatarState.evaluate(699, 150, 0L);
        ScrollAvatarState zombie700 = ScrollAvatarState.evaluate(700, 150, 0L);

        if (zen.tier != ScrollAvatarState.TIER_ZEN) throw new AssertionError("Expected ZEN at 0");
        if (fresh99.tier != ScrollAvatarState.TIER_FRESH) throw new AssertionError("Expected FRESH at 99");
        if (cruising100.tier != ScrollAvatarState.TIER_CRUISING || cruising249.tier != ScrollAvatarState.TIER_CRUISING) {
            throw new AssertionError("Expected CRUISING on 100..249");
        }
        if (trance250.tier != ScrollAvatarState.TIER_TRANCE || trance499.tier != ScrollAvatarState.TIER_TRANCE) {
            throw new AssertionError("Expected TRANCE on 250..499");
        }
        if (fatigued500.tier != ScrollAvatarState.TIER_FATIGUED || fatigued699.tier != ScrollAvatarState.TIER_FATIGUED) {
            throw new AssertionError("Expected FATIGUED on 500..699");
        }
        if (zombie700.tier != ScrollAvatarState.TIER_ZOMBIE) {
            throw new AssertionError("Expected ZOMBIE on 700+");
        }

        // 4. Every state must have multiple distinct titles rotating every 5 seconds
        java.util.Set<String> allTitles = new java.util.HashSet<>();
        long[] sampleScrolls = {0L, 50L, 100L, 250L, 500L, 700L};
        for (int tier = 0; tier < sampleScrolls.length; tier++) {
            String[] pool = ScrollAvatarState.titlesForTier(tier);
            if (pool.length < 3) {
                throw new AssertionError("Each state must have multiple titles, got " + pool.length);
            }
            String t0 = ScrollAvatarState.evaluate(sampleScrolls[tier], 0, 0L).moodTitle;
            String t5 = ScrollAvatarState.evaluate(sampleScrolls[tier], 0, 5000L).moodTitle;
            String t10 = ScrollAvatarState.evaluate(sampleScrolls[tier], 0, 10000L).moodTitle;
            if (t0.equals(t5) || t5.equals(t10)) {
                throw new AssertionError("Titles must switch every 5 seconds for tier " + tier);
            }
            for (String title : pool) {
                if (!allTitles.add(title)) {
                    throw new AssertionError("Duplicate title across states: " + title);
                }
            }
        }
    }
}
