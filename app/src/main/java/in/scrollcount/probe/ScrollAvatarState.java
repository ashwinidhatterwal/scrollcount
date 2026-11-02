package in.scrollcount.probe;

/**
 * Pure-Java expression and body-language state machine for the ScrollCount animated avatar.
 * State transitions occur at exact scroll milestones:
 *   0       -> TIER_ZEN      ("Unplugged")
 *   1–99    -> TIER_FRESH    ("Mindful")
 *   100–249 -> TIER_CRUISING ("Cruising")
 *   250–499 -> TIER_TRANCE   ("Deep in the Feed")
 *   500–699 -> TIER_FATIGUED ("Scroll Fatigue")
 *   700+    -> TIER_ZOMBIE   ("Doomscroller")
 */
public final class ScrollAvatarState {
    public static final int TIER_ZEN = 0;
    public static final int TIER_FRESH = 1;
    public static final int TIER_CRUISING = 2;
    public static final int TIER_TRANCE = 3;
    public static final int TIER_FATIGUED = 4;
    public static final int TIER_ZOMBIE = 5;

    /** Frame interval for 24 FPS (~41ms, exceeding the 15 FPS / 66ms minimum). */
    public static final long FRAME_INTERVAL_MS = 41L;

    public final long scrolls;
    public final int dailyGoal;
    public final int tier;
    public final float stress;
    public final String moodTitle;
    public final String moodSubtitle;

    // Smooth body language & posture parameters
    public final float spineSlouchDeg;
    public final float headTiltDeg;
    public final float headSwayX;
    public final float shoulderDrop;
    public final float breathOffset;
    public final float phoneRaise;
    public final float thumbSwipePhase;
    public final float armJitter;

    // Smooth facial expression parameters
    public final float eyeOpenness;
    public final float upperLidDroop;
    public final float pupilOffsetY;
    public final float browAngleDeg;
    public final float mouthCurve;
    public final float jawOpen;
    public final float eyeBagAlpha;
    public final float screenGlowAlpha;
    public final float spiralPhaseRad;
    public final float sweatDropProgress;

    private ScrollAvatarState(
            long scrolls, int dailyGoal, int tier, float stress,
            String moodTitle, String moodSubtitle,
            float spineSlouchDeg, float headTiltDeg, float headSwayX,
            float shoulderDrop, float breathOffset, float phoneRaise,
            float thumbSwipePhase, float armJitter, float eyeOpenness,
            float upperLidDroop, float pupilOffsetY, float browAngleDeg,
            float mouthCurve, float jawOpen, float eyeBagAlpha,
            float screenGlowAlpha, float spiralPhaseRad, float sweatDropProgress) {
        this.scrolls = scrolls;
        this.dailyGoal = dailyGoal;
        this.tier = tier;
        this.stress = stress;
        this.moodTitle = moodTitle;
        this.moodSubtitle = moodSubtitle;
        this.spineSlouchDeg = spineSlouchDeg;
        this.headTiltDeg = headTiltDeg;
        this.headSwayX = headSwayX;
        this.shoulderDrop = shoulderDrop;
        this.breathOffset = breathOffset;
        this.phoneRaise = phoneRaise;
        this.thumbSwipePhase = thumbSwipePhase;
        this.armJitter = armJitter;
        this.eyeOpenness = eyeOpenness;
        this.upperLidDroop = upperLidDroop;
        this.pupilOffsetY = pupilOffsetY;
        this.browAngleDeg = browAngleDeg;
        this.mouthCurve = mouthCurve;
        this.jawOpen = jawOpen;
        this.eyeBagAlpha = eyeBagAlpha;
        this.screenGlowAlpha = screenGlowAlpha;
        this.spiralPhaseRad = spiralPhaseRad;
        this.sweatDropProgress = sweatDropProgress;
    }

    /**
     * Exact scroll-count state thresholds:
     * 0 -> Unplugged, 1–99 -> Mindful, 100–249 -> Cruising,
     * 250–499 -> Deep in the Feed, 500–699 -> Scroll Fatigue, 700+ -> Doomscroller.
     */
    public static int computeTier(long scrolls, int dailyGoal) {
        long s = Math.max(0L, scrolls);
        if (s == 0L) return TIER_ZEN;
        if (s < 100L) return TIER_FRESH;
        if (s < 250L) return TIER_CRUISING;
        if (s < 500L) return TIER_TRANCE;
        if (s < 700L) return TIER_FATIGUED;
        return TIER_ZOMBIE;
    }

    public static float computeStress(float scrolls, int dailyGoal) {
        float s = Math.max(0f, scrolls);
        if (s <= 0.01f) return 0f;
        return Math.min(1f, Math.max(0.04f, s / 700f));
    }

    public static String titleForTier(int tier) {
        switch (tier) {
            case TIER_ZEN: return "Unplugged";
            case TIER_FRESH: return "Mindful";
            case TIER_CRUISING: return "Cruising";
            case TIER_TRANCE: return "Deep in the Feed";
            case TIER_FATIGUED: return "Scroll Fatigue";
            default: return "Doomscroller";
        }
    }

    public static String subtitleForTier(int tier) {
        switch (tier) {
            case TIER_ZEN: return "Fresh & rested · 0 scrolls";
            case TIER_FRESH: return "Light browsing · Under 100 scrolls";
            case TIER_CRUISING: return "Steady pace · 100+ scrolls";
            case TIER_TRANCE: return "Locked into the screen · 250+ scrolls";
            case TIER_FATIGUED: return "Tired eyes · 500+ scrolls";
            default: return "Overloaded · 700+ scrolls";
        }
    }

    public static ScrollAvatarState evaluate(long scrolls, int dailyGoal, long timeMs) {
        return evaluateSmooth((float) Math.max(0L, scrolls), dailyGoal, timeMs);
    }

    public static ScrollAvatarState evaluateSmooth(float smoothScrolls, int dailyGoal, long timeMs) {
        long cleanScrolls = Math.max(0L, Math.round(smoothScrolls));
        int tier = computeTier(cleanScrolls, dailyGoal);
        float stress = computeStress(smoothScrolls, dailyGoal);
        double t = (timeMs % 3600000L) / 1000.0;

        String title = titleForTier(tier);
        String subtitle = subtitleForTier(tier);

        double breathHz = 0.38 + stress * 0.85;
        float breathOffset = (float) Math.sin(t * Math.PI * 2.0 * breathHz);

        float spineSlouchDeg = stress * 26f + (tier == TIER_ZOMBIE ? 3f : 0f);
        float headTiltDeg = (tier == TIER_ZEN)
                ? (float) (Math.sin(t * 1.4) * 2.0)
                : (3f + stress * 20f + (float) Math.sin(t * 2.0) * (1.2f + stress * 1.8f));
        float headSwayX = tier >= TIER_FATIGUED
                ? (float) Math.sin(t * (tier == TIER_ZOMBIE ? 2.8 : 1.6)) * (tier == TIER_ZOMBIE ? 0.85f : 0.4f)
                : (float) Math.sin(t * 0.9) * 0.1f;

        float shoulderDrop = Math.min(1f, stress * 1.0f);
        float phoneRaise = tier == TIER_ZEN ? 0.0f : Math.min(1f, 0.35f + stress * 0.65f);

        double swipeHz = tier == TIER_ZEN ? 0.0 : (0.6 + stress * 2.4);
        float thumbSwipePhase = tier == TIER_ZEN ? 0f : (float) ((t * swipeHz) % 1.0);

        float armJitter = tier >= TIER_FATIGUED
                ? (float) Math.sin(t * 24.0) * (tier == TIER_ZOMBIE ? 0.75f : 0.4f)
                : 0f;

        double blinkCycle = (t % 3.8);
        boolean blinking = (tier <= TIER_CRUISING) && blinkCycle > 3.62;
        float eyeOpenness;
        float upperLidDroop;
        if (blinking) {
            eyeOpenness = 0.08f;
            upperLidDroop = 0.05f;
        } else if (tier == TIER_ZEN) {
            eyeOpenness = 1.0f;
            upperLidDroop = 0.0f;
        } else if (tier == TIER_TRANCE) {
            eyeOpenness = 1.16f;
            upperLidDroop = 0.06f;
        } else if (tier == TIER_FATIGUED) {
            eyeOpenness = 0.72f;
            upperLidDroop = 0.48f + (float) Math.sin(t * 1.8) * 0.05f;
        } else if (tier == TIER_ZOMBIE) {
            eyeOpenness = 1.08f;
            upperLidDroop = 0.36f;
        } else {
            eyeOpenness = 1.0f - stress * 0.08f;
            upperLidDroop = stress * 0.20f;
        }

        float pupilOffsetY = tier == TIER_ZEN ? 0f : (0.28f - (float) Math.sin(thumbSwipePhase * Math.PI) * 0.52f);

        float browAngleDeg;
        if (tier == TIER_ZEN) browAngleDeg = 5f;
        else if (tier == TIER_FRESH) browAngleDeg = 3.5f;
        else if (tier == TIER_CRUISING) browAngleDeg = 0f;
        else if (tier == TIER_TRANCE) browAngleDeg = -7f;
        else if (tier == TIER_FATIGUED) browAngleDeg = -11f;
        else browAngleDeg = -14f;

        float mouthCurve;
        float jawOpen;
        if (tier == TIER_ZEN) {
            mouthCurve = 0.78f;
            jawOpen = 0f;
        } else if (tier == TIER_FRESH) {
            mouthCurve = 0.58f;
            jawOpen = 0f;
        } else if (tier == TIER_CRUISING) {
            mouthCurve = 0.16f;
            jawOpen = 0f;
        } else if (tier == TIER_TRANCE) {
            mouthCurve = -0.12f;
            jawOpen = 0.12f;
        } else if (tier == TIER_FATIGUED) {
            mouthCurve = -0.52f;
            jawOpen = 0.26f + (float) Math.max(0, Math.sin(t * 1.3)) * 0.20f;
        } else {
            mouthCurve = -0.75f;
            jawOpen = 0.52f + (float) Math.sin(t * 2.6) * 0.14f;
        }

        float eyeBagAlpha = stress < 0.35f ? 0f : Math.min(1f, (stress - 0.35f) / 0.65f);
        float screenGlowAlpha = tier == TIER_ZEN ? 0f : Math.min(1f, 0.22f + stress * 0.78f);
        float spiralPhaseRad = (float) ((t * 5.8) % (Math.PI * 2.0));
        float sweatDropProgress = tier >= TIER_FATIGUED ? (float) ((t * 0.75) % 1.0) : 0f;

        return new ScrollAvatarState(
                cleanScrolls, dailyGoal, tier, stress, title, subtitle,
                spineSlouchDeg, headTiltDeg, headSwayX, shoulderDrop,
                breathOffset, phoneRaise, thumbSwipePhase, armJitter,
                eyeOpenness, upperLidDroop, pupilOffsetY, browAngleDeg,
                mouthCurve, jawOpen, eyeBagAlpha, screenGlowAlpha,
                spiralPhaseRad, sweatDropProgress);
    }
}
