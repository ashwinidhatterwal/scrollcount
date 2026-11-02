package in.scrollcount.probe;

/**
 * Pure-Java kinematic and expression state machine for the ScrollCount animated avatar.
 * State transitions occur at exact scroll milestones:
 *   0       -> TIER_ZEN      (State 0)
 *   1–99    -> TIER_FRESH    (State 1)
 *   100–249 -> TIER_CRUISING (State 2)
 *   250–499 -> TIER_TRANCE   (State 3)
 *   500–699 -> TIER_FATIGUED (State 4)
 *   700+    -> TIER_ZOMBIE   (State 5)
 *
 * Each state rotates through multiple expressive titles every 5 seconds (5,000 ms).
 */
public final class ScrollAvatarState {
    public static final int TIER_ZEN = 0;
    public static final int TIER_FRESH = 1;
    public static final int TIER_CRUISING = 2;
    public static final int TIER_TRANCE = 3;
    public static final int TIER_FATIGUED = 4;
    public static final int TIER_ZOMBIE = 5;

    /** Frame interval for 24 FPS (~41ms, well above the 15 FPS / 66ms minimum). */
    public static final long FRAME_INTERVAL_MS = 41L;
    /** Title rotation interval: switches to the next title every 5 seconds. */
    public static final long TITLE_ROTATION_MS = 5000L;

    private static final String[][] STATE_TITLES = {
        // State 0: 0 scrolls
        {
            "Zen Master",
            "Unplugged & Free",
            "Touch Grass Champion",
            "Digital Detox",
            "Mind Clear"
        },
        // State 1: 1–99 scrolls
        {
            "Just Peeking",
            "Casual Warm-Up",
            "Mindful Scroller",
            "Lightly Browsing",
            "Still in Control"
        },
        // State 2: 100–249 scrolls
        {
            "Thumb in Motion",
            "Cruising the Feed",
            "One More Reel…",
            "Algorithm's Friend",
            "Steady Swiping"
        },
        // State 3: 250–499 scrolls
        {
            "Deep in the Feed",
            "Screen Trance",
            "Lost in Shorts",
            "Hypnotized Thumb",
            "Time Warp Active"
        },
        // State 4: 500–699 scrolls
        {
            "Scroll Fatigue",
            "Thumb Needs Ice",
            "Heavy Eyelids",
            "Dopamine Drained",
            "Blink Twice!"
        },
        // State 5: 700+ scrolls
        {
            "Full Doomscroller",
            "Scroll Zombie",
            "Merged With Screen",
            "Send Reinforcements",
            "Final Boss of Reels"
        }
    };

    public final long scrolls;
    public final int dailyGoal;
    public final int tier;
    public final int titleIndex;
    public final float stress;
    public final String moodTitle;
    public final String moodSubtitle;

    // Expressive body language & posture kinematics
    public final float spineSlouchDeg;   // 0 deg (upright) .. 42 deg (deep hunch)
    public final float headTiltDeg;      // -4 deg .. +32 deg forward neck droop
    public final float headSwayX;        // -1.0 .. +1.0 horizontal zombie sway
    public final float shoulderDrop;     // 0.0 (squared) .. 1.0 (slumped forward)
    public final float breathOffset;     // -1.0 .. +1.0 chest breathing cycle
    public final float phoneRaise;       // 0.0 (resting) .. 1.0 (held close to face)
    public final float thumbSwipePhase;  // 0.0 .. 1.0 cyclic thumb flick position
    public final float armJitter;        // -1.0 .. +1.0 fatigue tremor

    // Expressive facial parameters
    public final float eyeOpenness;      // 0.0 (closed) .. 1.25 (wide trance)
    public final float upperLidDroop;    // 0.0 (alert) .. 0.60 (heavy tired lids)
    public final float pupilOffsetY;     // -1.0 .. +1.0 vertical eye tracking of feed
    public final float browAngleDeg;     // -18 deg (furrowed) .. +8 deg (relaxed)
    public final float mouthCurve;       // +1.0 (happy smile) .. -0.9 (weary frown)
    public final float jawOpen;          // 0.0 (closed) .. 0.85 (slack jaw / sigh)
    public final float eyeBagAlpha;      // 0.0 .. 1.0 dark under-eye circles
    public final float screenGlowAlpha;  // 0.0 .. 1.0 blue screen reflection on face
    public final float spiralPhaseRad;   // radians for zombie spiral eyes
    public final float sweatDropProgress;// 0.0 .. 1.0 falling sweat drop in fatigued tiers

    private ScrollAvatarState(
            long scrolls, int dailyGoal, int tier, int titleIndex, float stress,
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
        this.titleIndex = titleIndex;
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
        return Math.min(1f, Math.max(0.05f, s / 700f));
    }

    public static String[] titlesForTier(int tier) {
        int safeTier = Math.max(0, Math.min(STATE_TITLES.length - 1, tier));
        return STATE_TITLES[safeTier].clone();
    }

    public static String titleForTier(int tier, long timeMs) {
        int safeTier = Math.max(0, Math.min(STATE_TITLES.length - 1, tier));
        String[] pool = STATE_TITLES[safeTier];
        int idx = (int) ((Math.max(0L, timeMs) / TITLE_ROTATION_MS) % pool.length);
        return pool[idx];
    }

    public static String subtitleForTier(int tier) {
        switch (tier) {
            case TIER_ZEN: return "Fresh & rested · 0 scrolls";
            case TIER_FRESH: return "Light browsing · 1–99 scrolls";
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
        long positiveTime = Math.max(0L, timeMs);
        double t = (positiveTime % 3600000L) / 1000.0;

        String[] pool = STATE_TITLES[tier];
        int titleIndex = (int) ((positiveTime / TITLE_ROTATION_MS) % pool.length);
        String title = pool[titleIndex];
        String subtitle = subtitleForTier(tier);

        // Breathing slows in Zen (0.35 Hz) and becomes shallow/rapid under high scroll load (1.4 Hz)
        double breathHz = 0.35 + stress * 1.05;
        float breathOffset = (float) Math.sin(t * Math.PI * 2.0 * breathHz);

        // Expressive full upper-body spine slouch & head droop
        float spineSlouchDeg = stress * 38f + (tier == TIER_ZOMBIE ? 4f : 0f);
        float headTiltDeg = (tier == TIER_ZEN)
                ? (float) (Math.sin(t * 1.5) * 2.5)
                : (4f + stress * 28f + (float) Math.sin(t * 2.2) * (1.5f + stress * 2.5f));
        float headSwayX = tier >= TIER_FATIGUED
                ? (float) Math.sin(t * (tier == TIER_ZOMBIE ? 3.2 : 1.8)) * (tier == TIER_ZOMBIE ? 1.0f : 0.45f)
                : (float) Math.sin(t * 0.9) * 0.12f;

        float shoulderDrop = Math.min(1f, stress * 1.05f);
        float phoneRaise = tier == TIER_ZEN ? 0.08f : Math.min(1f, 0.38f + stress * 0.62f);

        // Thumb swipe speed scales with scroll intensity
        double swipeHz = tier == TIER_ZEN ? 0.0 : (0.55 + stress * 2.85);
        float thumbSwipePhase = tier == TIER_ZEN ? 0f : (float) ((t * swipeHz) % 1.0);

        // Arm fatigue tremor in fatigued / doomscroller states
        float armJitter = tier >= TIER_FATIGUED
                ? (float) Math.sin(t * 28.0) * (tier == TIER_ZOMBIE ? 1.0f : 0.55f)
                : 0f;

        // Expressive eyes: peaceful closed smile in Zen, natural blinking in Fresh/Cruising,
        // wide glazed stare in Trance, heavy drooping lids in Fatigued, spinning spirals in Zombie
        double blinkCycle = (t % 3.6);
        boolean blinking = (tier == TIER_FRESH || tier == TIER_CRUISING) && blinkCycle > 3.42;
        float eyeOpenness;
        float upperLidDroop;
        if (tier == TIER_ZEN) {
            eyeOpenness = 0.08f;
            upperLidDroop = 0.0f;
        } else if (blinking) {
            eyeOpenness = 0.06f;
            upperLidDroop = 0.1f;
        } else if (tier == TIER_TRANCE) {
            eyeOpenness = 1.22f;
            upperLidDroop = 0.05f;
        } else if (tier == TIER_FATIGUED) {
            eyeOpenness = 0.62f;
            upperLidDroop = 0.52f + (float) Math.sin(t * 2.0) * 0.06f;
        } else if (tier == TIER_ZOMBIE) {
            eyeOpenness = 1.15f;
            upperLidDroop = 0.35f;
        } else {
            eyeOpenness = 1.0f - stress * 0.12f;
            upperLidDroop = stress * 0.22f;
        }

        // Pupils track vertically with each thumb swipe
        float pupilOffsetY = tier == TIER_ZEN ? 0f : (0.35f - thumbSwipePhase * 0.75f);

        float browAngleDeg;
        if (tier == TIER_ZEN) browAngleDeg = 6f;
        else if (tier == TIER_FRESH) browAngleDeg = 4f;
        else if (tier == TIER_CRUISING) browAngleDeg = 0f;
        else if (tier == TIER_TRANCE) browAngleDeg = -9f;
        else if (tier == TIER_FATIGUED) browAngleDeg = -14f;
        else browAngleDeg = -18f;

        float mouthCurve;
        float jawOpen;
        if (tier == TIER_ZEN) {
            mouthCurve = 0.85f;
            jawOpen = 0f;
        } else if (tier == TIER_FRESH) {
            mouthCurve = 0.65f;
            jawOpen = 0f;
        } else if (tier == TIER_CRUISING) {
            mouthCurve = 0.18f;
            jawOpen = 0f;
        } else if (tier == TIER_TRANCE) {
            mouthCurve = -0.15f;
            jawOpen = 0.18f;
        } else if (tier == TIER_FATIGUED) {
            mouthCurve = -0.62f;
            jawOpen = 0.32f + (float) Math.max(0, Math.sin(t * 1.4)) * 0.25f;
        } else {
            mouthCurve = -0.85f;
            jawOpen = 0.65f + (float) Math.sin(t * 3.0) * 0.18f;
        }

        float eyeBagAlpha = stress < 0.35f ? 0f : Math.min(1f, (stress - 0.35f) / 0.65f);
        float screenGlowAlpha = tier == TIER_ZEN ? 0f : Math.min(1f, 0.25f + stress * 0.75f);
        float spiralPhaseRad = (float) ((t * 6.5) % (Math.PI * 2.0));
        float sweatDropProgress = tier >= TIER_FATIGUED ? (float) ((t * 0.85) % 1.0) : 0f;

        return new ScrollAvatarState(
                cleanScrolls, dailyGoal, tier, titleIndex, stress, title, subtitle,
                spineSlouchDeg, headTiltDeg, headSwayX, shoulderDrop,
                breathOffset, phoneRaise, thumbSwipePhase, armJitter,
                eyeOpenness, upperLidDroop, pupilOffsetY, browAngleDeg,
                mouthCurve, jawOpen, eyeBagAlpha, screenGlowAlpha,
                spiralPhaseRad, sweatDropProgress);
    }
}
