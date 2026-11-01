package in.scrollcount.probe;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Refined 24 FPS animated character avatar enclosed in a balanced circular medallion.
 * Expresses scroll load strictly through polished character art, facial expression,
 * and posture — with zero technical labels or debug text exposed to the user.
 */
final class ScrollAvatarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    private long targetScrolls;
    private float displayedScrolls;
    private int dailyGoal;
    private boolean initialized;
    private boolean attached;
    private ScrollAvatarState state = ScrollAvatarState.evaluate(0, 0, 0);

    private final Runnable frameTicker = new Runnable() {
        @Override public void run() {
            if (!attached || getVisibility() != VISIBLE || !isShown()) return;
            float diff = targetScrolls - displayedScrolls;
            if (Math.abs(diff) > 0.05f) {
                displayedScrolls += diff * 0.22f;
            } else {
                displayedScrolls = targetScrolls;
            }
            state = ScrollAvatarState.evaluateSmooth(displayedScrolls, dailyGoal, SystemClock.elapsedRealtime());
            invalidate();
            if (ValueAnimator.areAnimatorsEnabled()) {
                postDelayed(this, ScrollAvatarState.FRAME_INTERVAL_MS);
            }
        }
    };

    ScrollAvatarView(Ui ui) {
        this(ui, false);
    }

    ScrollAvatarView(Ui ui, boolean compact) {
        super(ui.activity);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateContentDescription();
    }

    void update(long scrolls, int dailyGoal) {
        long clean = Math.max(0L, scrolls);
        int cleanGoal = Math.max(0, dailyGoal);
        boolean changed = this.targetScrolls != clean || this.dailyGoal != cleanGoal;
        this.targetScrolls = clean;
        this.dailyGoal = cleanGoal;
        if (!initialized) {
            this.displayedScrolls = clean;
            this.initialized = true;
        }
        state = ScrollAvatarState.evaluateSmooth(this.displayedScrolls, this.dailyGoal, SystemClock.elapsedRealtime());
        if (changed) updateContentDescription();
        invalidate();
    }

    String moodLabel() {
        return state.moodTitle;
    }

    private void updateContentDescription() {
        setContentDescription(state.moodTitle + ". " + state.moodSubtitle + ".");
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

        float size = Math.min(w, h);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float radius = size * 0.46f;
        float u = radius / 100f; // Normalized 100-unit coordinate system inside medallion

        int accentColor = tierColor(state.tier);

        // 1. Medallion backdrop with soft vertical gradient
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                cx, cy - radius, cx, cy + radius,
                backdropTopColor(state.tier), backdropBottomColor(state.tier),
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);

        // Clip character neatly inside the circular medallion so shoulders/torso align with the frame
        c.save();
        clipPath.reset();
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
        c.clipPath(clipPath);

        // Soft ambient glow behind head
        paint.setColor((accentColor & 0x00ffffff) | 0x24000000);
        float haloR = (58f + state.breathOffset * 3f) * u;
        c.drawCircle(cx, cy - 6f * u, haloR, paint);

        // 2. Posture kinematics: spine lean & shoulder position anchored cleanly at bottom of medallion
        float slouchPx = state.stress * 14f * u;
        float dropPx = state.shoulderDrop * 10f * u;
        float breathPx = state.breathOffset * 2.2f * u;

        float torsoCenterX = cx - 4f * u + slouchPx * 0.35f;
        float shoulderY = cy + 36f * u + dropPx - breathPx;
        float bottomY = cy + radius + 14f * u;

        // Torso / rounded sweater shoulders
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(shirtColor(state.tier));
        path.reset();
        path.moveTo(torsoCenterX - 66f * u, bottomY);
        path.cubicTo(
                torsoCenterX - 64f * u, shoulderY + 12f * u,
                torsoCenterX - 38f * u, shoulderY - 4f * u,
                torsoCenterX, shoulderY - 4f * u);
        path.cubicTo(
                torsoCenterX + 38f * u, shoulderY - 4f * u,
                torsoCenterX + 64f * u, shoulderY + 12f * u,
                torsoCenterX + 66f * u, bottomY);
        path.close();
        c.drawPath(path, paint);

        // Subtle sweater depth shading on left side
        paint.setColor(0x1f000000);
        path.reset();
        path.moveTo(torsoCenterX - 66f * u, bottomY);
        path.cubicTo(
                torsoCenterX - 60f * u, shoulderY + 16f * u,
                torsoCenterX - 30f * u, shoulderY,
                torsoCenterX - 6f * u, shoulderY + 2f * u);
        path.lineTo(torsoCenterX - 12f * u, bottomY);
        path.close();
        c.drawPath(path, paint);

        // Crew-neck collar trim
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4.5f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x33ffffff);
        rect.set(torsoCenterX - 22f * u, shoulderY - 9f * u, torsoCenterX + 22f * u, shoulderY + 11f * u);
        c.drawArc(rect, 12f, 156f, false, paint);

        // 3. Neck with soft chin shadow
        float headX = cx + slouchPx * 0.65f + state.headSwayX * 5.5f * u;
        float headY = cy - 14f * u + dropPx * 0.85f - breathPx * 0.6f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffd79e7d);
        rect.set(torsoCenterX - 11f * u, headY + 24f * u, torsoCenterX + 11f * u, shoulderY + 8f * u);
        c.drawRoundRect(rect, 10f * u, 10f * u, paint);

        // Chin cast shadow on neck
        paint.setColor(0x26402218);
        rect.set(torsoCenterX - 11f * u, headY + 24f * u, torsoCenterX + 11f * u, headY + 38f * u);
        c.drawOval(rect, paint);

        // 4. Head & Facial Expression
        c.save();
        c.translate(headX, headY);
        c.rotate(state.headTiltDeg * 0.42f);

        float headW = 40f * u;
        float headH = 44f * u;
        int skinTone = state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xffe3bfa8 : 0xfff1c4a5;
        int earTone = state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xffd3ad95 : 0xffe2b191;

        // Symmetric Ears + Inner Ear Fold
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(earTone);
        c.drawCircle(-headW * 0.95f, 3f * u, 8.5f * u, paint);
        c.drawCircle(headW * 0.95f, 3f * u, 8.5f * u, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f * u);
        paint.setColor(0x447a4430);
        c.drawArc(-headW * 0.95f - 4f * u, -1f * u, -headW * 0.95f + 4f * u, 7f * u, 110f, 160f, false, paint);
        c.drawArc(headW * 0.95f - 4f * u, -1f * u, headW * 0.95f + 4f * u, 7f * u, -70f, 160f, false, paint);

        // Refined Head Contour (smooth rounded forehead + tapered jawline)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(skinTone);
        path.reset();
        path.moveTo(0f, -headH);
        path.cubicTo(headW * 0.92f, -headH, headW, -headH * 0.35f, headW * 0.94f, headH * 0.18f);
        path.cubicTo(headW * 0.86f, headH * 0.68f, headW * 0.42f, headH, 0f, headH);
        path.cubicTo(-headW * 0.42f, headH, -headW * 0.86f, headH * 0.68f, -headW * 0.94f, headH * 0.18f);
        path.cubicTo(-headW, -headH * 0.35f, -headW * 0.92f, -headH, 0f, -headH);
        path.close();
        c.drawPath(path, paint);

        // Screen glow reflection on lower face when actively scrolling
        if (state.screenGlowAlpha > 0.03f) {
            int glowAlpha = Math.min(140, (int) (state.screenGlowAlpha * 115));
            paint.setShader(new LinearGradient(
                    headW * 0.5f, headH, -headW * 0.2f, -headH * 0.1f,
                    (glowAlpha << 24) | 0x0064ffda, 0x0064ffda, Shader.TileMode.CLAMP));
            c.drawPath(path, paint);
            paint.setShader(null);
        }

        // Warm rosy cheeks (in rested / fresh tiers)
        if (state.tier <= ScrollAvatarState.TIER_CRUISING) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x2ee57373);
            rect.set(-28f * u, 8f * u, -12f * u, 18f * u);
            c.drawOval(rect, paint);
            rect.set(12f * u, 8f * u, 28f * u, 18f * u);
            c.drawOval(rect, paint);
        }

        // Gaze offset (subtle shift toward the phone when raised)
        float gazeX = state.tier == ScrollAvatarState.TIER_ZEN ? 0f : 2.5f * u;
        float eyeY = -1f * u;
        float leftEyeX = -15.5f * u + gazeX * 0.5f;
        float rightEyeX = 15.5f * u + gazeX * 0.5f;
        float eyeR = 8.2f * u;

        // Soft under-eye fatigue bags
        if (state.eyeBagAlpha > 0.02f) {
            int bagAlpha = Math.min(150, (int) (state.eyeBagAlpha * 135));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f * u);
            paint.setColor((bagAlpha << 24) | 0x0052364f);
            rect.set(leftEyeX - eyeR * 0.9f, eyeY + 2f * u, leftEyeX + eyeR * 0.9f, eyeY + eyeR * 1.35f);
            c.drawArc(rect, 22f, 136f, false, paint);
            rect.set(rightEyeX - eyeR * 0.9f, eyeY + 2f * u, rightEyeX + eyeR * 0.9f, eyeY + eyeR * 1.35f);
            c.drawArc(rect, 22f, 136f, false, paint);
        }

        // Eyes
        drawPolishedEye(c, leftEyeX, eyeY, eyeR, u, skinTone);
        drawPolishedEye(c, rightEyeX, eyeY, eyeR, u, skinTone);

        // Delicate Nose Bridge & Tip
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x558c533e);
        path.reset();
        path.moveTo(gazeX * 0.6f, eyeY + 2f * u);
        path.quadTo(gazeX + 2.2f * u, eyeY + 11f * u, gazeX - 1.2f * u, eyeY + 12.5f * u);
        c.drawPath(path, paint);

        // Smooth Curved Eyebrows
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.2f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xff1c2622);
        float browSlant = state.browAngleDeg * 0.22f * u;
        float browLift = (state.tier == ScrollAvatarState.TIER_TRANCE ? -2.2f : 0f) * u;

        path.reset();
        path.moveTo(leftEyeX - eyeR * 1.05f, eyeY - eyeR * 1.25f - browSlant + browLift);
        path.quadTo(leftEyeX, eyeY - eyeR * 1.65f + browLift, leftEyeX + eyeR * 1.0f, eyeY - eyeR * 1.20f + browSlant + browLift);
        c.drawPath(path, paint);

        path.reset();
        path.moveTo(rightEyeX - eyeR * 1.0f, eyeY - eyeR * 1.20f + browSlant + browLift);
        path.quadTo(rightEyeX, eyeY - eyeR * 1.65f + browLift, rightEyeX + eyeR * 1.05f, eyeY - eyeR * 1.25f - browSlant + browLift);
        c.drawPath(path, paint);

        // Expressive Mouth
        float mouthY = 21f * u;
        float mouthW = 11.5f * u;
        if (state.jawOpen > 0.16f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff632b30);
            float openH = (3.0f + state.jawOpen * 7.5f) * u;
            rect.set(gazeX - mouthW * 0.72f, mouthY - openH * 0.35f, gazeX + mouthW * 0.72f, mouthY + openH * 0.85f);
            c.drawOval(rect, paint);
            // Subtle tongue tint at bottom of open mouth
            paint.setColor(0xffbf5f65);
            rect.set(gazeX - mouthW * 0.42f, mouthY + openH * 0.15f, gazeX + mouthW * 0.42f, mouthY + openH * 0.78f);
            c.drawOval(rect, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.0f * u);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xff632b30);
            path.reset();
            path.moveTo(gazeX - mouthW, mouthY);
            path.quadTo(gazeX, mouthY + state.mouthCurve * 9.5f * u, gazeX + mouthW, mouthY);
            c.drawPath(path, paint);
        }

        // Layered Hair Crown & Swept Front Bangs
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff19231f);
        path.reset();
        path.moveTo(-headW * 1.02f, -headH * 0.12f);
        path.cubicTo(-headW * 1.08f, -headH * 0.95f, -headW * 0.45f, -headH * 1.22f, 0f, -headH * 1.20f);
        path.cubicTo(headW * 0.52f, -headH * 1.22f, headW * 1.08f, -headH * 0.92f, headW * 1.02f, -headH * 0.10f);
        // Swept front hairline with natural wave
        path.cubicTo(headW * 0.65f, -headH * 0.58f, headW * 0.15f, -headH * 0.44f, -headW * 0.28f, -headH * 0.62f);
        path.cubicTo(-headW * 0.62f, -headH * 0.74f, -headW * 0.88f, -headH * 0.45f, -headW * 1.02f, -headH * 0.12f);
        path.close();
        c.drawPath(path, paint);

        // Soft specular hair highlight
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f * u);
        paint.setColor(0x29a6ddbd);
        path.reset();
        path.moveTo(-headW * 0.48f, -headH * 0.88f);
        path.quadTo(0f, -headH * 1.04f, headW * 0.46f, -headH * 0.82f);
        c.drawPath(path, paint);

        // Temple sweat drop in fatigued / scrolled-out tiers
        if (state.tier >= ScrollAvatarState.TIER_FATIGUED) {
            float dropX = -headW * 0.74f;
            float dropY = -headH * 0.25f + state.sweatDropProgress * 20f * u;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xd94fc3f7);
            path.reset();
            path.moveTo(dropX, dropY - 8f * u);
            path.quadTo(dropX + 4.5f * u, dropY, dropX, dropY + 4.5f * u);
            path.quadTo(dropX - 4.5f * u, dropY, dropX, dropY - 8f * u);
            path.close();
            c.drawPath(path, paint);
        }

        c.restore();

        // 5. Sleek Smartphone & Animated Thumb Swipe in foreground when scrolling
        if (state.tier > ScrollAvatarState.TIER_ZEN) {
            float phoneX = cx + (30f + state.phoneRaise * 8f) * u + state.armJitter * 1.5f * u;
            float phoneY = cy + (52f - state.phoneRaise * 24f) * u + state.armJitter * 1.2f * u;

            c.save();
            c.translate(phoneX, phoneY);
            c.rotate(-10f + state.stress * 6f);

            // Phone outer body with subtle rim
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff111915);
            rect.set(-13f * u, -24f * u, 13f * u, 24f * u);
            c.drawRoundRect(rect, 5.5f * u, 5.5f * u, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f * u);
            paint.setColor(0x44a6ddbd);
            c.drawRoundRect(rect, 5.5f * u, 5.5f * u, paint);

            // Illuminated screen
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xff351c33 : 0xff163d35);
            rect.set(-10.5f * u, -20.5f * u, 10.5f * u, 20.5f * u);
            c.drawRoundRect(rect, 3.8f * u, 3.8f * u, paint);

            // Scrolling feed card inside phone screen
            float feedOffset = 10f * u - state.thumbSwipePhase * 20f * u;
            paint.setColor(accentColor);
            rect.set(-7.5f * u, feedOffset - 6.5f * u, 7.5f * u, feedOffset + 6.5f * u);
            c.drawRoundRect(rect, 2.5f * u, 2.5f * u, paint);

            // Wrapped fingers on right edge of phone
            paint.setColor(earTone);
            c.drawCircle(13f * u, -6f * u, 3.4f * u, paint);
            c.drawCircle(13f * u, 2f * u, 3.4f * u, paint);
            c.drawCircle(13f * u, 10f * u, 3.2f * u, paint);

            // Articulated thumb gliding vertically across the screen
            float thumbArc = (float) Math.sin(state.thumbSwipePhase * Math.PI);
            float thumbY = 12f * u - thumbArc * 18f * u;
            float thumbX = -9.5f * u + thumbArc * 2.5f * u;
            paint.setColor(skinTone);
            rect.set(thumbX - 4.5f * u, thumbY - 6f * u, thumbX + 4.5f * u, thumbY + 6f * u);
            c.drawOval(rect, paint);

            c.restore();
        }

        c.restore(); // Restore medallion clip

        // 6. Outer Medallion Ring Border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f * u);
        paint.setColor((accentColor & 0x00ffffff) | 0x88000000);
        c.drawCircle(cx, cy, radius - 1.3f * u, paint);
    }

    private void drawPolishedEye(Canvas c, float x, float y, float r, float u, int skinTone) {
        if (state.eyeOpenness <= 0.14f) {
            // Peaceful closed / blinking eye arc
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.8f * u);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xff1c2622);
            rect.set(x - r, y - r * 0.45f, x + r, y + r * 0.55f);
            c.drawArc(rect, 18f, 144f, false, paint);
            return;
        }

        float ry = r * Math.min(1.18f, state.eyeOpenness);
        c.save();
        path.reset();
        rect.set(x - r, y - ry, x + r, y + ry);
        path.addOval(rect, Path.Direction.CW);
        c.clipPath(path);

        // Sclera
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xfffff3f0 : 0xffffffff);
        c.drawOval(rect, paint);

        if (state.tier == ScrollAvatarState.TIER_ZOMBIE) {
            // Dazed spiral iris
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.0f * u);
            paint.setColor(0xffd84315);
            for (int i = 0; i < 2; i++) {
                float startDeg = (float) Math.toDegrees(state.spiralPhaseRad) + i * 180f;
                rect.set(x - r * 0.68f, y - r * 0.68f, x + r * 0.68f, y + r * 0.68f);
                c.drawArc(rect, startDeg, 220f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, 2.2f * u, paint);
        } else {
            float px = x + (state.tier == ScrollAvatarState.TIER_ZEN ? 0f : 1.2f * u);
            float py = y + state.pupilOffsetY * r * 0.40f;

            // Iris
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff2c5243);
            c.drawCircle(px, py, r * 0.60f, paint);

            // Pupil
            paint.setColor(0xff111c17);
            c.drawCircle(px, py, r * 0.34f, paint);

            // Dual specular catchlights
            paint.setColor(0xffffffff);
            c.drawCircle(px - r * 0.20f, py - r * 0.20f, r * 0.17f, paint);
            paint.setColor(0xbbffffff);
            c.drawCircle(px + r * 0.18f, py + r * 0.18f, r * 0.08f, paint);
        }

        // Smooth curved upper eyelid droop
        if (state.upperLidDroop > 0.04f) {
            float lidY = (y - ry) + (ry * 2f * state.upperLidDroop);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(skinTone);
            path.reset();
            path.moveTo(x - r * 1.2f, y - ry * 1.2f);
            path.lineTo(x + r * 1.2f, y - ry * 1.2f);
            path.lineTo(x + r * 1.2f, lidY);
            path.quadTo(x, lidY + 2.2f * u, x - r * 1.2f, lidY);
            path.close();
            c.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * u);
            paint.setColor(0xff1c2622);
            path.reset();
            path.moveTo(x - r, lidY);
            path.quadTo(x, lidY + 2.2f * u, x + r, lidY);
            c.drawPath(path, paint);
        }

        c.restore();

        // Subtle upper lash line
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xff1c2622);
        rect.set(x - r, y - ry, x + r, y + ry);
        c.drawArc(rect, 195f, 150f, false, paint);
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

    private static int backdropTopColor(int tier) {
        switch (tier) {
            case ScrollAvatarState.TIER_ZEN:
            case ScrollAvatarState.TIER_FRESH: return 0xff1b3f30;
            case ScrollAvatarState.TIER_CRUISING: return 0xff183848;
            case ScrollAvatarState.TIER_TRANCE: return 0xff2b2947;
            case ScrollAvatarState.TIER_FATIGUED: return 0xff3d2522;
            default: return 0xff3b1c2b;
        }
    }

    private static int backdropBottomColor(int tier) {
        switch (tier) {
            case ScrollAvatarState.TIER_ZEN:
            case ScrollAvatarState.TIER_FRESH: return 0xff0d231a;
            case ScrollAvatarState.TIER_CRUISING: return 0xff0e222c;
            case ScrollAvatarState.TIER_TRANCE: return 0xff171629;
            case ScrollAvatarState.TIER_FATIGUED: return 0xff211412;
            default: return 0xff210e17;
        }
    }

    private static int shirtColor(int tier) {
        switch (tier) {
            case ScrollAvatarState.TIER_ZEN:
            case ScrollAvatarState.TIER_FRESH: return 0xff2e8560;
            case ScrollAvatarState.TIER_CRUISING: return 0xff2b6982;
            case ScrollAvatarState.TIER_TRANCE: return 0xff63528f;
            case ScrollAvatarState.TIER_FATIGUED: return 0xff8a523f;
            default: return 0xff7a334f;
        }
    }
}
