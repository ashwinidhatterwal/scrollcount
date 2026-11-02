package in.scrollcount.probe;

public final class ScrollAvatarStateTest {
    public static void main(String[] args) {
        // 1. Frame rate requirement: must be at least 15 FPS (<= 66ms per frame)
        if (ScrollAvatarState.FRAME_INTERVAL_MS > 66L || ScrollAvatarState.FRAME_INTERVAL_MS <= 0L) {
            throw new AssertionError("Frame interval must be >= 15 FPS: " + ScrollAvatarState.FRAME_INTERVAL_MS);
        }

        // 2. Exact tier boundaries: 0, 1..99, 100..249, 250..499, 500..699, 700+
        ScrollAvatarState zen = ScrollAvatarState.evaluate(0, 150, 1000L);
        ScrollAvatarState fresh99 = ScrollAvatarState.evaluate(99, 150, 1000L);
        ScrollAvatarState cruising100 = ScrollAvatarState.evaluate(100, 150, 1000L);
        ScrollAvatarState cruising249 = ScrollAvatarState.evaluate(249, 150, 1000L);
        ScrollAvatarState trance250 = ScrollAvatarState.evaluate(250, 150, 1000L);
        ScrollAvatarState trance499 = ScrollAvatarState.evaluate(499, 150, 1000L);
        ScrollAvatarState fatigued500 = ScrollAvatarState.evaluate(500, 150, 1000L);
        ScrollAvatarState fatigued699 = ScrollAvatarState.evaluate(699, 150, 1000L);
        ScrollAvatarState zombie700 = ScrollAvatarState.evaluate(700, 150, 1000L);

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

        // 3. Every state must have a distinct, non-empty title
        java.util.Set<String> titles = new java.util.HashSet<>();
        for (ScrollAvatarState s : new ScrollAvatarState[]{zen, fresh99, cruising100, trance250, fatigued500, zombie700}) {
            if (s.moodTitle == null || s.moodTitle.trim().isEmpty()) {
                throw new AssertionError("Empty moodTitle for tier " + s.tier);
            }
            if (!titles.add(s.moodTitle)) {
                throw new AssertionError("Duplicate moodTitle: " + s.moodTitle);
            }
        }

        // 4. Body language and facial expression progression across 0 -> 700+
        if (!(zen.spineSlouchDeg < fresh99.spineSlouchDeg
                && fresh99.spineSlouchDeg < cruising100.spineSlouchDeg
                && cruising100.spineSlouchDeg < trance250.spineSlouchDeg
                && trance250.spineSlouchDeg < fatigued500.spineSlouchDeg
                && fatigued500.spineSlouchDeg < zombie700.spineSlouchDeg)) {
            throw new AssertionError("Posture slouch must progressively increase across thresholds");
        }
        if (!(zen.mouthCurve > 0.5f && fresh99.mouthCurve > 0.4f && fatigued500.mouthCurve < -0.4f && zombie700.mouthCurve < -0.7f)) {
            throw new AssertionError("Mouth expression must shift from smile to frown as scroll fatigue grows");
        }
    }
}
