package in.scrollcount.probe;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * Custom 24 FPS (~41ms frame cadence, exceeding the 15 FPS minimum) animated avatar view.
 * Renders dynamic posture, spine slouch, head tilt, phone scrolling motion, screen reflection,
 * and facial expressions driven by the person's live scroll counter.
 */
final class ScrollAvatarView extends View {
    private final Ui ui;
    private final boolean compact;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private long scrolls;
    private int dailyGoal;
    private boolean attached;
    private ScrollAvatarState state = ScrollAvatarState.evaluate(0, 0, 0);

    private final Runnable frameTicker = new Runnable() {
        @Override public void run() {
            if (!attached || getVisibility() != VISIBLE || !isShown()) return;
            state = ScrollAvatarState.evaluate(scrolls, dailyGoal, SystemClock.elapsedRealtime());
            invalidate();
            if (ValueAnimator.areAnimatorsEnabled()) {
                postDelayed(this, ScrollAvatarState.FRAME_INTERVAL_MS);
            }
        }
    };

    ScrollAvatarView(Ui ui, boolean compact) {
        super(ui.activity);
        this.ui = ui;
        this.compact = compact;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateContentDescription();
    }

    void update(long scrolls, int dailyGoal) {
        boolean changed = this.scrolls != scrolls || this.dailyGoal != dailyGoal;
        this.scrolls = Math.max(0L, scrolls);
        this.dailyGoal = Math.max(0, dailyGoal);
        state = ScrollAvatarState.evaluate(this.scrolls, this.dailyGoal, SystemClock.elapsedRealtime());
        if (changed) updateContentDescription();
        invalidate();
    }

    private void updateContentDescription() {
        setContentDescription("Animated scroll avatar: " + state.moodTitle + ". "
                + state.moodSubtitle + ". " + Ui.number(scrolls) + " scrolls today.");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        removeCallbacks(frameTicker);
        post(frameTicker);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(frameTicker);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        removeCallbacks(frameTicker);
        if (attached && visibility == VISIBLE) post(frameTicker);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float unit = Math.min(w, h) / 220f;
        float cx = compact ? w * 0.50f : w * 0.34f;
        float cy = h * 0.54f;

        // 1. Background aura ring reactive to tier
        int auraColor = tierColor(state.tier);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((auraColor & 0x00ffffff) | 0x22000000);
        float auraRadius = (compact ? 74f : 68f) * unit * (1f + state.breathOffset * 0.035f);
        c.drawCircle(cx, cy - 8f * unit, auraRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * unit);
        paint.setColor((auraColor & 0x00ffffff) | 0x66000000);
        c.drawCircle(cx, cy - 8f * unit, auraRadius, paint);

        // 2. Compute posture coordinates (hip/base -> torso/shoulders -> neck -> head)
        float slouchRad = (float) Math.toRadians(state.spineSlouchDeg);
        float hipX = cx - 8f * unit;
        float hipY = cy + 56f * unit;

        float torsoLen = 54f * unit;
        float shoulderX = hipX + (float) Math.sin(slouchRad) * torsoLen;
        float shoulderY = hipY - (float) Math.cos(slouchRad) * torsoLen
                + state.shoulderDrop * 7f * unit
                - state.breathOffset * 2.5f * unit;

        // Draw torso / hoodie
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(shirtColor(state.tier));
        path.reset();
        path.moveTo(hipX - 36f * unit, hipY + 12f * unit);
        path.quadTo(hipX - 34f * unit, shoulderY + 6f * unit, shoulderX - 26f * unit, shoulderY);
        path.lineTo(shoulderX + 26f * unit, shoulderY + 2f * unit);
        path.quadTo(hipX + 38f * unit, shoulderY + 14f * unit, hipX + 34f * unit, hipY + 12f * unit);
        path.close();
        c.drawPath(path, paint);

        // Collar detail
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * unit);
        paint.setColor(0x33ffffff);
        c.drawArc(shoulderX - 14f * unit, shoulderY - 4f * unit, shoulderX + 14f * unit, shoulderY + 14f * unit, 15, 150, false, paint);

        // Neck
        float headAngleRad = (float) Math.toRadians(state.headTiltDeg);
        float headX = shoulderX + (float) Math.sin(slouchRad * 0.7f + headAngleRad * 0.5f) * 28f * unit + state.headSwayX * 6f * unit;
        float headY = shoulderY - 30f * unit + state.shoulderDrop * 6f * unit;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(14f * unit);
        paint.setColor(0xffe0ac8b);
        c.drawLine(shoulderX, shoulderY + 2f * unit, headX, headY + 10f * unit, paint);

        // 3. Head & Face (rotated by headTiltDeg)
        c.save();
        c.translate(headX, headY);
        c.rotate(state.headTiltDeg * 0.55f);

        float headR = 29f * unit;
        // Back hair / ear
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffd39c7b);
        c.drawCircle(-headR * 0.88f, 2f * unit, 6.5f * unit, paint);

        // Head base
        paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xffdcc0ad : 0xffeab696);
        rect.set(-headR, -headR * 1.06f, headR, headR * 1.02f);
        c.drawOval(rect, paint);

        // Screen blue glow cast onto lower face when scrolling
        if (state.screenGlowAlpha > 0.02f) {
            int glowAlpha = Math.min(180, (int) (state.screenGlowAlpha * 145));
            paint.setShader(new LinearGradient(
                    headR * 0.9f, headR * 0.9f, -headR * 0.3f, -headR * 0.4f,
                    (glowAlpha << 24) | 0x005ce1e6, 0x005ce1e6, Shader.TileMode.CLAMP));
            c.drawOval(rect, paint);
            paint.setShader(null);
        }

        // Hair cap
        paint.setColor(0xff1e2824);
        path.reset();
        path.moveTo(-headR * 1.02f, -headR * 0.25f);
        path.quadTo(-headR * 0.95f, -headR * 1.25f, 0f, -headR * 1.18f);
        path.quadTo(headR * 1.05f, -headR * 1.15f, headR * 0.96f, -headR * 0.32f);
        path.quadTo(headR * 0.35f, -headR * 0.68f, -headR * 1.02f, -headR * 0.25f);
        path.close();
        c.drawPath(path, paint);

        // Face feature offsets (facing slightly right toward the held phone)
        float faceShiftX = 5f * unit;
        float eyeY = -2f * unit;
        float leftEyeX = faceShiftX - 10.5f * unit;
        float rightEyeX = faceShiftX + 10.5f * unit;
        float eyeR = 6.2f * unit;

        // Under-eye dark circles (fatigue/zombie)
        if (state.eyeBagAlpha > 0.02f) {
            int bagAlpha = Math.min(190, (int) (state.eyeBagAlpha * 170));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f * unit);
            paint.setColor((bagAlpha << 24) | 0x004a3248);
            rect.set(leftEyeX - eyeR, eyeY + 1f * unit, leftEyeX + eyeR, eyeY + eyeR * 1.45f);
            c.drawArc(rect, 20, 140, false, paint);
            rect.set(rightEyeX - eyeR, eyeY + 1f * unit, rightEyeX + eyeR, eyeY + eyeR * 1.45f);
            c.drawArc(rect, 20, 140, false, paint);
        }

        // Cheeks in Zen / Fresh tiers
        if (state.tier <= ScrollAvatarState.TIER_FRESH) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x38e57373);
            c.drawOval(leftEyeX - 10f * unit, eyeY + 6f * unit, leftEyeX - 1f * unit, eyeY + 12f * unit, paint);
            c.drawOval(rightEyeX + 1f * unit, eyeY + 6f * unit, rightEyeX + 10f * unit, eyeY + 12f * unit, paint);
        }

        // Eyes
        drawEye(c, leftEyeX, eyeY, eyeR, unit);
        drawEye(c, rightEyeX, eyeY, eyeR, unit);

        // Eyebrows
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f * unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xff1e2824);
        float browTilt = state.browAngleDeg * 0.18f * unit;
        c.drawLine(leftEyeX - eyeR, eyeY - eyeR * 1.35f - browTilt, leftEyeX + eyeR * 0.9f, eyeY - eyeR * 1.35f + browTilt, paint);
        c.drawLine(rightEyeX - eyeR * 0.9f, eyeY - eyeR * 1.35f + browTilt, rightEyeX + eyeR, eyeY - eyeR * 1.35f - browTilt, paint);

        // Mouth
        float mouthY = 14f * unit;
        float mouthW = 9.5f * unit;
        if (state.jawOpen > 0.15f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff5a2a2c);
            float openH = (2.5f + state.jawOpen * 6.5f) * unit;
            rect.set(faceShiftX - mouthW * 0.75f, mouthY - openH * 0.4f, faceShiftX + mouthW * 0.75f, mouthY + openH);
            c.drawOval(rect, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f * unit);
            paint.setColor(0xff5a2a2c);
            path.reset();
            path.moveTo(faceShiftX - mouthW, mouthY);
            path.quadTo(faceShiftX, mouthY + state.mouthCurve * 7.5f * unit, faceShiftX + mouthW, mouthY);
            c.drawPath(path, paint);
        }

        // Sweat drop in Fatigued / Zombie tiers
        if (state.tier >= ScrollAvatarState.TIER_FATIGUED) {
            float dropY = -headR * 0.55f + state.sweatDropProgress * 18f * unit;
            float dropX = -headR * 0.72f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xcc4fc3f7);
            c.drawCircle(dropX, dropY, 3.2f * unit, paint);
            path.reset();
            path.moveTo(dropX - 3.2f * unit, dropY);
            path.lineTo(dropX, dropY - 7f * unit);
            path.lineTo(dropX + 3.2f * unit, dropY);
            path.close();
            c.drawPath(path, paint);
        }

        c.restore();

        // 4. Held Smartphone, Arm & Animated Thumb Swipe
        float phoneX = shoulderX + (38f + state.phoneRaise * 14f) * unit + state.armJitter * 1.8f * unit;
        float phoneY = shoulderY + (28f - state.phoneRaise * 32f) * unit + state.armJitter * 1.4f * unit;

        // Back arm & forearm holding the phone
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(11f * unit);
        paint.setColor(shirtColor(state.tier));
        float elbowX = shoulderX + 18f * unit;
        float elbowY = shoulderY + 26f * unit;
        c.drawLine(shoulderX + 10f * unit, shoulderY + 6f * unit, elbowX, elbowY, paint);
        c.drawLine(elbowX, elbowY, phoneX - 6f * unit, phoneY + 8f * unit, paint);

        if (state.tier > ScrollAvatarState.TIER_ZEN) {
            c.save();
            c.translate(phoneX, phoneY);
            c.rotate(-14f + state.stress * 10f);

            // Screen light cone toward face
            int coneAlpha = Math.min(115, (int) (state.screenGlowAlpha * 95));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((coneAlpha << 24) | 0x0064ffda);
            path.reset();
            path.moveTo(-4f * unit, -16f * unit);
            path.lineTo(-42f * unit, -42f * unit);
            path.lineTo(-36f * unit, 6f * unit);
            path.lineTo(-4f * unit, 16f * unit);
            path.close();
            c.drawPath(path, paint);

            // Phone body
            rect.set(-9f * unit, -19f * unit, 9f * unit, 19f * unit);
            paint.setColor(0xff141e19);
            c.drawRoundRect(rect, 4f * unit, 4f * unit, paint);

            // Phone illuminated screen with scrolling feed cards
            rect.set(-7f * unit, -16f * unit, 7f * unit, 16f * unit);
            paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xff3b1d38 : 0xff163d35);
            c.drawRoundRect(rect, 2.5f * unit, 2.5f * unit, paint);

            // Animated mini feed item moving upward with thumbSwipePhase
            float feedY = 8f * unit - state.thumbSwipePhase * 18f * unit;
            paint.setColor(auraColor);
            c.drawRoundRect(-5f * unit, feedY - 5f * unit, 5f * unit, feedY + 5f * unit, 2f * unit, 2f * unit, paint);

            // Animated thumb swiping vertically across screen
            float thumbY = 10f * unit - (float) Math.sin(state.thumbSwipePhase * Math.PI) * 16f * unit;
            paint.setColor(0xffeab696);
            c.drawCircle(-7f * unit, thumbY, 4.2f * unit, paint);

            c.restore();
        } else {
            // Zen floating mindfulness sparkles
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99a6ddbd);
            float floatOff = state.breathOffset * 4f * unit;
            c.drawCircle(cx - 52f * unit, cy - 36f * unit + floatOff, 3.5f * unit, paint);
            c.drawCircle(cx + 52f * unit, cy - 28f * unit - floatOff, 4.2f * unit, paint);
        }

        // 5. Status pill & FPS / Mood telemetry on full-width card
        if (!compact && w > ui.dp(220)) {
            float textLeft = w * 0.62f;
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);

            // Tier pill
            paint.setColor((auraColor & 0x00ffffff) | 0x2e000000);
            float pillTop = h * 0.22f;
            String badge = "●  24 FPS LIVE MOOD";
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
            float pillW = paint.measureText(badge) + ui.dp(16);
            rect.set(textLeft, pillTop, Math.min(w - ui.dp(8), textLeft + pillW), pillTop + ui.dp(22));
            c.drawRoundRect(rect, ui.dp(11), ui.dp(11), paint);

            paint.setColor(auraColor);
            c.drawText(badge, textLeft + ui.dp(8), pillTop + ui.dp(15), paint);

            // Mood title
            paint.setColor(0xfff5fff6);
            paint.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
            c.drawText(state.moodTitle, textLeft, pillTop + ui.dp(44), paint);

            // Mood subtitle (body language summary)
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setColor(0xffc0dfc8);
            paint.setTextSize(11.5f * getResources().getDisplayMetrics().scaledDensity);
            c.drawText(state.moodSubtitle, textLeft, pillTop + ui.dp(64), paint);

            // Posture angle telemetry
            String posture = "Neck tilt " + Math.round(state.headTiltDeg) + "° · Spine " + Math.round(state.spineSlouchDeg) + "°";
            paint.setColor(0xff99bfae);
            paint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
            c.drawText(posture, textLeft, pillTop + ui.dp(84), paint);
        }
    }

    private void drawEye(Canvas c, float x, float y, float r, float unit) {
        if (state.eyeOpenness <= 0.14f) {
            // Closed / peaceful or blinking eye
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f * unit);
            paint.setColor(0xff1e2824);
            rect.set(x - r, y - r * 0.5f, x + r, y + r * 0.6f);
            c.drawArc(rect, 15, 150, false, paint);
            return;
        }

        // Sclera
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xfffff0ee : 0xffffffff);
        float ry = r * Math.min(1.25f, state.eyeOpenness);
        rect.set(x - r, y - ry, x + r, y + ry);
        c.drawOval(rect, paint);

        if (state.tier == ScrollAvatarState.TIER_ZOMBIE) {
            // Hypnotic spinning spiral in Zombie tier
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f * unit);
            paint.setColor(0xffd84315);
            for (int i = 0; i < 2; i++) {
                float startDeg = (float) Math.toDegrees(state.spiralPhaseRad) + i * 180f;
                rect.set(x - r * 0.72f, y - r * 0.72f, x + r * 0.72f, y + r * 0.72f);
                c.drawArc(rect, startDeg, 230f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, 1.8f * unit, paint);
        } else {
            // Pupil tracking vertical scroll feed
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff182620);
            float py = y + state.pupilOffsetY * r * 0.42f;
            float px = x + 1.4f * unit;
            c.drawCircle(px, py, r * 0.48f, paint);

            // Screen highlight reflection inside pupil
            paint.setColor(0xccffffff);
            c.drawCircle(px - r * 0.16f, py - r * 0.18f, r * 0.16f, paint);
        }

        // Heavy upper eyelid droop for scroll fatigue
        if (state.upperLidDroop > 0.05f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xffd2b09a : 0xffe2aa88);
            float lidBottom = (y - ry) + (ry * 2f * state.upperLidDroop);
            rect.set(x - r * 1.05f, y - ry * 1.1f, x + r * 1.05f, lidBottom);
            c.drawRect(rect, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f * unit);
            paint.setColor(0xff1e2824);
            c.drawLine(x - r, lidBottom, x + r, lidBottom, paint);
        }
    }

    private static int tierColor(int tier) {
        switch (tier) {
            case ScrollAvatarState.TIER_ZEN: return 0xff6ee7b7;
            case ScrollAvatarState.TIER_FRESH: return 0xff4ade80;
            case ScrollAvatarState.TIER_CRUISING: return 0xff38bdf8;
            case ScrollAvatarState.TIER_TRANCE: return 0xfffbbf24;
            case ScrollAvatarState.TIER_FATIGUED: return 0xfff97316;
            default: return 0xfff43f5e;
        }
    }

    private static int shirtColor(int tier) {
        switch (tier) {
            case ScrollAvatarState.TIER_ZEN:
            case ScrollAvatarState.TIER_FRESH: return 0xff2b7a58;
            case ScrollAvatarState.TIER_CRUISING: return 0xff275d73;
            case ScrollAvatarState.TIER_TRANCE: return 0xff5a4b81;
            case ScrollAvatarState.TIER_FATIGUED: return 0xff7a4938;
            default: return 0xff6b2d46;
        }
    }
}
