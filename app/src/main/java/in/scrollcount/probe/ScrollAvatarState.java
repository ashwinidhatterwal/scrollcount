package in.scrollcount.probe;

/**
 * Pure-Java kinematic and expression state machine for the ScrollCount animated avatar.
 * Maps a person's daily scroll count (and optional daily goal) plus animation time (ms)
 * into facial expression and body-language parameters at >= 15 FPS.
 */
public final class ScrollAvatarState {
    public static final int TIER_ZEN = 0;
    public static final int TIER_FRESH = 1;
    public static final int TIER_CRUISING = 2;
    public static final int TIER_TRANCE = 3;
    public static final int TIER_FATIGUED = 4;
    public static final int TIER_ZOMBIE = 5;

    /** Minimum frame interval for 24 FPS (~41ms, well above the 15 FPS / 66ms requirement). */
    public static final long FRAME_INTERVAL_MS = 41L;

    public final long scrolls;
    public final int dailyGoal;
    public final int tier;
    public final float stress; // 0.0 (zen) .. 1.0 (zombie)
    public final String moodTitle;
    public final String moodSubtitle;

    // Body language & posture kinematics
    public final float spineSlouchDeg;   // 0 deg (upright) .. 40 deg (hunched over phone)
    public final float headTiltDeg;      // -4 deg .. +32 deg forward neck droop
    public final float headSwayX;        // -1.0 .. +1.0 horizontal zombie sway
    public final float shoulderDrop;     // 0.0 (squared) .. 1.0 (slumped forward)
    public final float breathOffset;     // -1.0 .. +1.0 chest breathing cycle
    public final float phoneRaise;       // 0.0 (lowered/resting) .. 1.0 (held close to face)
    public final float thumbSwipePhase;  // 0.0 .. 1.0 cyclic thumb flick position
    public final float armJitter;        // -1.0 .. +1.0 fatigue tremor

    // Facial expression parameters
    public final float eyeOpenness;      // 0.0 (closed) .. 1.25 (wide trance)
    public final float upperLidDroop;    // 0.0 (alert) .. 0.65 (heavy tired lids)
    public final float pupilOffsetY;     // -1.0 .. +1.0 vertical eye tracking of feed
    public final float browAngleDeg;     // -18 deg (furrowed) .. +10 deg (relaxed/raised)
    public final float mouthCurve;       // +1.0 (happy smile) .. -1.0 (weary frown / slack jaw)
    public final float jawOpen;          // 0.0 (closed) .. 1.0 (zombie slack jaw / yawn)
    public final float eyeBagAlpha;      // 0.0 .. 1.0 dark under-eye circles
    public final float screenGlowAlpha;  // 0.0 .. 1.0 blue screen reflection on face
    public final float spiralPhaseRad;   // radians for zombie spiral eyes
    public final float sweatDropProgress;// 0.0 .. 1.0 falling sweat drop in fatigued tiers

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

    public static int computeTier(long scrolls, int dailyGoal) {
        long s = Math.max(0L, scrolls);
        if (s == 0L) return TIER_ZEN;
        if (dailyGoal > 0) {
            float ratio = (float) s / (float) dailyGoal;
            if (ratio <= 0.25f) return TIER_FRESH;
            if (ratio <= 0.65f) return TIER_CRUISING;
            if (ratio <= 1.00f) return TIER_TRANCE;
            if (ratio <= 1.50f) return TIER_FATIGUED;
            return TIER_ZOMBIE;
        }
        if (s <= 25L) return TIER_FRESH;
        if (s <= 75L) return TIER_CRUISING;
        if (s <= 150L) return TIER_TRANCE;
        if (s <= 300L) return TIER_FATIGUED;
        return TIER_ZOMBIE;
    }

    public static float computeStress(long scrolls, int dailyGoal) {
        long s = Math.max(0L, scrolls);
        if (s == 0L) return 0f;
        float scale = dailyGoal > 0 ? Math.max(20f, dailyGoal * 1.6f) : 320f;
        return Math.min(1f, Math.max(0.05f, s / scale));
    }

    public static ScrollAvatarState evaluate(long scrolls, int dailyGoal, long timeMs) {
        long cleanScrolls = Math.max(0L, scrolls);
        int tier = computeTier(cleanScrolls, dailyGoal);
        float stress = computeStress(cleanScrolls, dailyGoal);
        double t = (timeMs % 3600000L) / 1000.0;

        String title;
        String subtitle;
        switch (tier) {
            case TIER_ZEN:
                title = "Zen & Unplugged";
                subtitle = "Upright posture · Relaxed breathing";
                break;
            case TIER_FRESH:
                title = "Mindful & Fresh";
                subtitle = "Balanced posture · Light browsing";
                break;
            case TIER_CRUISING:
                title = "Casually Cruising";
                subtitle = "Leaning in · Steady thumb rhythm";
                break;
            case TIER_TRANCE:
                title = "Feed Trance";
                subtitle = "Forward hunch · Unblinking screen lock";
                break;
            case TIER_FATIGUED:
                title = "Scroll Fatigue";
                subtitle = "Slumped shoulders · Tired eyes";
                break;
            default:
                title = "Doomscroll Mode";
                subtitle = "Deep slouch · Time for a breather";
                break;
        }

        // Breathing slows in Zen (0.35 Hz) and becomes shallow/rapid under high scroll load (1.4 Hz)
        double breathHz = 0.35 + stress * 1.05;
        float breathOffset = (float) Math.sin(t * Math.PI * 2.0 * breathHz);

        // Spine slouch and head forward droop progress smoothly with scroll load
        float spineSlouchDeg = stress * 38f + (tier == TIER_ZOMBIE ? 4f : 0f);
        float headTiltDeg = (tier == TIER_ZEN)
                ? (float) (Math.sin(t * 1.5) * 2.5)
                : (4f + stress * 28f + (float) Math.sin(t * 2.2) * (1.5f + stress * 2.5f));
        float headSwayX = tier >= TIER_FATIGUED
                ? (float) Math.sin(t * (tier == TIER_ZOMBIE ? 3.2 : 1.8)) * (tier == TIER_ZOMBIE ? 1.0f : 0.45f)
                : (float) Math.sin(t * 0.9) * 0.12f;

        float shoulderDrop = Math.min(1f, stress * 1.05f);
        float phoneRaise = tier == TIER_ZEN ? 0.08f : Math.min(1f, 0.38f + stress * 0.62f);

        // Thumb swipe speed scales with scroll intensity (0 in Zen, up to 3.4 swipes/sec in Zombie)
        double swipeHz = tier == TIER_ZEN ? 0.0 : (0.55 + stress * 2.85);
        float thumbSwipePhase = tier == TIER_ZEN ? 0f : (float) ((t * swipeHz) % 1.0);

        // Subtle arm fatigue tremor when fatigued/zombie
        float armJitter = tier >= TIER_FATIGUED
                ? (float) Math.sin(t * 28.0) * (tier == TIER_ZOMBIE ? 1.0f : 0.55f)
                : 0f;

        // Blinking cycle: natural blink in Fresh/Cruising, wide unblinking stare in Trance, heavy droop in Fatigued
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

        // Pupils track vertically with each thumb swipe as the user watches Reels/Shorts
        float pupilOffsetY = tier == TIER_ZEN ? 0f : (0.35f - thumbSwipePhase * 0.75f);

        // Eyebrow angle: positive = relaxed arch, negative = furrowed / strained
        float browAngleDeg;
        if (tier == TIER_ZEN) browAngleDeg = 6f;
        else if (tier == TIER_FRESH) browAngleDeg = 4f;
        else if (tier == TIER_CRUISING) browAngleDeg = 0f;
        else if (tier == TIER_TRANCE) browAngleDeg = -9f;
        else if (tier == TIER_FATIGUED) browAngleDeg = -14f;
        else browAngleDeg = -18f;

        // Mouth curve: +1 smile -> 0 neutral -> -0.8 frown
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
                cleanScrolls, dailyGoal, tier, stress, title, subtitle,
                spineSlouchDeg, headTiltDeg, headSwayX, shoulderDrop,
                breathOffset, phoneRaise, thumbSwipePhase, armJitter,
                eyeOpenness, upperLidDroop, pupilOffsetY, browAngleDeg,
                mouthCurve, jawOpen, eyeBagAlpha, screenGlowAlpha,
                spiralPhaseRad, sweatDropProgress);
    }
}
