package in.scrollcount.probe;

public final class ScrollAvatarStateTest {
    public static void main(String[] args) {
        // 1. Frame rate requirement: must be at least 15 FPS (<= 66ms per frame)
        if (ScrollAvatarState.FRAME_INTERVAL_MS > 66L || ScrollAvatarState.FRAME_INTERVAL_MS <= 0L) {
            throw new AssertionError("Frame interval must be >= 15 FPS: " + ScrollAvatarState.FRAME_INTERVAL_MS);
        }

        // 2. Tier progression from 0 scrolls (Zen) through 350+ scrolls (Zombie)
        ScrollAvatarState zen = ScrollAvatarState.evaluate(0, 0, 1000L);
        ScrollAvatarState fresh = ScrollAvatarState.evaluate(12, 0, 1000L);
        ScrollAvatarState cruising = ScrollAvatarState.evaluate(50, 0, 1000L);
        ScrollAvatarState trance = ScrollAvatarState.evaluate(110, 0, 1000L);
        ScrollAvatarState fatigued = ScrollAvatarState.evaluate(220, 0, 1000L);
        ScrollAvatarState zombie = ScrollAvatarState.evaluate(400, 0, 1000L);

        if (zen.tier != ScrollAvatarState.TIER_ZEN) throw new AssertionError("Expected ZEN tier");
        if (fresh.tier != ScrollAvatarState.TIER_FRESH) throw new AssertionError("Expected FRESH tier");
        if (cruising.tier != ScrollAvatarState.TIER_CRUISING) throw new AssertionError("Expected CRUISING tier");
        if (trance.tier != ScrollAvatarState.TIER_TRANCE) throw new AssertionError("Expected TRANCE tier");
        if (fatigued.tier != ScrollAvatarState.TIER_FATIGUED) throw new AssertionError("Expected FATIGUED tier");
        if (zombie.tier != ScrollAvatarState.TIER_ZOMBIE) throw new AssertionError("Expected ZOMBIE tier");

        // 3. Body language progression: spine slouch and shoulder drop increase with scrolls
        if (!(zen.spineSlouchDeg < fresh.spineSlouchDeg
                && fresh.spineSlouchDeg < cruising.spineSlouchDeg
                && cruising.spineSlouchDeg < trance.spineSlouchDeg
                && trance.spineSlouchDeg < fatigued.spineSlouchDeg
                && fatigued.spineSlouchDeg < zombie.spineSlouchDeg)) {
            throw new AssertionError("Spine slouch must progressively increase with scroll count");
        }

        // 4. Facial expression progression: mouth curve transitions from smile (+) to frown (-)
        if (!(zen.mouthCurve > 0.5f && fresh.mouthCurve > 0.4f && fatigued.mouthCurve < -0.4f && zombie.mouthCurve < -0.7f)) {
            throw new AssertionError("Mouth expression must shift from smile to frown as scroll fatigue grows");
        }

        // 5. Multi-frame animation continuity at 24 FPS (41ms steps)
        float prevSwipe = -1f;
        int changes = 0;
        for (int frame = 0; frame < 24; frame++) {
            ScrollAvatarState step = ScrollAvatarState.evaluate(95, 100, frame * ScrollAvatarState.FRAME_INTERVAL_MS);
            if (step.thumbSwipePhase != prevSwipe) changes++;
            prevSwipe = step.thumbSwipePhase;
        }
        if (changes < 20) {
            throw new AssertionError("Avatar must animate smoothly across 24 FPS frame steps");
        }
    }
}
