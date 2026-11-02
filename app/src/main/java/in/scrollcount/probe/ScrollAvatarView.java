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
import android.widget.TextView;

/**
 * Expressive 24 FPS (~41ms frame cadence) full upper-body animated character view.
 * Restores the expressive three-quarter posture (visible spine slouch, articulated arm & elbow,
 * phone light cone, Zen closed-eye smile + sparkles, dramatic eye/brow/mouth transitions,
 * sweat drop, and spinning spiral eyes) while keeping a bound title TextView directly above
 * the avatar automatically synchronized as titles rotate every 5 seconds.
 */
final class ScrollAvatarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private long targetScrolls;
    private float displayedScrolls;
    private int dailyGoal;
    private boolean initialized;
    private boolean attached;
    private TextView boundTitleView;
    private String lastBoundTitle = "";
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
            syncBoundTitle();
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

    void bindTitleView(TextView titleView) {
        this.boundTitleView = titleView;
        this.lastBoundTitle = "";
        syncBoundTitle();
    }

    private void syncBoundTitle() {
        if (boundTitleView != null && !state.moodTitle.equals(lastBoundTitle)) {
            lastBoundTitle = state.moodTitle;
            boundTitleView.setText(state.moodTitle);
        }
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
        syncBoundTitle();
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

        float unit = Math.min(w, h) / 185f;
        // Center the full upper-body character cleanly inside the square view bounds
        float cx = w * 0.46f;
        float cy = h * 0.54f;

        // 1. Background reactive aura ring
        int auraColor = tierColor(state.tier);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((auraColor & 0x00ffffff) | 0x22000000);
        float auraRadius = 74f * unit * (1f + state.breathOffset * 0.035f);
        c.drawCircle(w * 0.5f, h * 0.5f, auraRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * unit);
        paint.setColor((auraColor & 0x00ffffff) | 0x66000000);
        c.drawCircle(w * 0.5f, h * 0.5f, auraRadius, paint);

        // 2. Expressive posture coordinates (hip/base -> torso/shoulders -> neck -> head)
        float slouchRad = (float) Math.toRadians(state.spineSlouchDeg);
        float hipX = cx - 10f * unit;
        float hipY = cy + 52f * unit;

        float torsoLen = 50f * unit;
        float shoulderX = hipX + (float) Math.sin(slouchRad) * torsoLen;
        float shoulderY = hipY - (float) Math.cos(slouchRad) * torsoLen
                + state.shoulderDrop * 6.5f * unit
                - state.breathOffset * 2.4f * unit;

        // Torso / hoodie
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(shirtColor(state.tier));
        path.reset();
        path.moveTo(hipX - 34f * unit, hipY + 10f * unit);
        path.quadTo(hipX - 32f * unit, shoulderY + 6f * unit, shoulderX - 24f * unit, shoulderY);
        path.lineTo(shoulderX + 24f * unit, shoulderY + 2f * unit);
        path.quadTo(hipX + 35f * unit, shoulderY + 14f * unit, hipX + 32f * unit, hipY + 10f * unit);
        path.close();
        c.drawPath(path, paint);

        // Collar detail
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f * unit);
        paint.setColor(0x33ffffff);
        rect.set(shoulderX - 13f * unit, shoulderY - 4f * unit, shoulderX + 13f * unit, shoulderY + 13f * unit);
        c.drawArc(rect, 15f, 150f, false, paint);

        // Neck
        float headAngleRad = (float) Math.toRadians(state.headTiltDeg);
        float headX = shoulderX + (float) Math.sin(slouchRad * 0.7f + headAngleRad * 0.5f) * 26f * unit + state.headSwayX * 5.5f * unit;
        float headY = shoulderY - 28f * unit + state.shoulderDrop * 5.5f * unit;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(13f * unit);
        paint.setColor(0xffe0ac8b);
        c.drawLine(shoulderX, shoulderY + 2f * unit, headX, headY + 9f * unit, paint);

        // 3. Expressive Head & Face (rotated by headTiltDeg)
        c.save();
        c.translate(headX, headY);
        c.rotate(state.headTiltDeg * 0.55f);

        float headR = 27.5f * unit;
        // Ear
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffd39c7b);
        c.drawCircle(-headR * 0.88f, 2f * unit, 6.2f * unit, paint);

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

        // Face features (facing slightly right toward the held phone)
        float faceShiftX = 4.8f * unit;
        float eyeY = -2f * unit;
        float leftEyeX = faceShiftX - 10f * unit;
        float rightEyeX = faceShiftX + 10f * unit;
        float eyeR = 6.0f * unit;

        // Under-eye dark circles (fatigue/zombie)
        if (state.eyeBagAlpha > 0.02f) {
            int bagAlpha = Math.min(190, (int) (state.eyeBagAlpha * 170));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * unit);
            paint.setColor((bagAlpha << 24) | 0x004a3248);
            rect.set(leftEyeX - eyeR, eyeY + 1f * unit, leftEyeX + eyeR, eyeY + eyeR * 1.45f);
            c.drawArc(rect, 20f, 140f, false, paint);
            rect.set(rightEyeX - eyeR, eyeY + 1f * unit, rightEyeX + eyeR, eyeY + eyeR * 1.45f);
            c.drawArc(rect, 20f, 140f, false, paint);
        }

        // Rosy cheeks in Zen / Fresh tiers
        if (state.tier <= ScrollAvatarState.TIER_FRESH) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x38e57373);
            rect.set(leftEyeX - 9.5f * unit, eyeY + 5.5f * unit, leftEyeX - 1f * unit, eyeY + 11.5f * unit);
            c.drawOval(rect, paint);
            rect.set(rightEyeX + 1f * unit, eyeY + 5.5f * unit, rightEyeX + 9.5f * unit, eyeY + 11.5f * unit);
            c.drawOval(rect, paint);
        }

        // Eyes
        drawEye(c, leftEyeX, eyeY, eyeR, unit);
        drawEye(c, rightEyeX, eyeY, eyeR, unit);

        // Eyebrows
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xff1e2824);
        float browTilt = state.browAngleDeg * 0.18f * unit;
        c.drawLine(leftEyeX - eyeR, eyeY - eyeR * 1.35f - browTilt, leftEyeX + eyeR * 0.9f, eyeY - eyeR * 1.35f + browTilt, paint);
        c.drawLine(rightEyeX - eyeR * 0.9f, eyeY - eyeR * 1.35f + browTilt, rightEyeX + eyeR, eyeY - eyeR * 1.35f - browTilt, paint);

        // Mouth
        float mouthY = 13.5f * unit;
        float mouthW = 9.0f * unit;
        if (state.jawOpen > 0.15f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff5a2a2c);
            float openH = (2.4f + state.jawOpen * 6.2f) * unit;
            rect.set(faceShiftX - mouthW * 0.75f, mouthY - openH * 0.4f, faceShiftX + mouthW * 0.75f, mouthY + openH);
            c.drawOval(rect, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * unit);
            paint.setColor(0xff5a2a2c);
            path.reset();
            path.moveTo(faceShiftX - mouthW, mouthY);
            path.quadTo(faceShiftX, mouthY + state.mouthCurve * 7.2f * unit, faceShiftX + mouthW, mouthY);
            c.drawPath(path, paint);
        }

        // Sweat drop in Fatigued / Zombie tiers
        if (state.tier >= ScrollAvatarState.TIER_FATIGUED) {
            float dropY = -headR * 0.55f + state.sweatDropProgress * 17f * unit;
            float dropX = -headR * 0.72f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xcc4fc3f7);
            c.drawCircle(dropX, dropY, 3.0f * unit, paint);
            path.reset();
            path.moveTo(dropX - 3.0f * unit, dropY);
            path.lineTo(dropX, dropY - 6.5f * unit);
            path.lineTo(dropX + 3.0f * unit, dropY);
            path.close();
            c.drawPath(path, paint);
        }

        c.restore();

        // 4. Held Smartphone, Articulated Arm & Animated Thumb Swipe
        float phoneX = shoulderX + (35f + state.phoneRaise * 13f) * unit + state.armJitter * 1.7f * unit;
        float phoneY = shoulderY + (26f - state.phoneRaise * 30f) * unit + state.armJitter * 1.3f * unit;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(10.5f * unit);
        paint.setColor(shirtColor(state.tier));
        float elbowX = shoulderX + 16f * unit;
        float elbowY = shoulderY + 24f * unit;
        c.drawLine(shoulderX + 9f * unit, shoulderY + 5f * unit, elbowX, elbowY, paint);
        c.drawLine(elbowX, elbowY, phoneX - 5f * unit, phoneY + 7f * unit, paint);

        if (state.tier > ScrollAvatarState.TIER_ZEN) {
            c.save();
            c.translate(phoneX, phoneY);
            c.rotate(-14f + state.stress * 10f);

            // Screen light cone toward face
            int coneAlpha = Math.min(115, (int) (state.screenGlowAlpha * 95));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((coneAlpha << 24) | 0x0064ffda);
            path.reset();
            path.moveTo(-4f * unit, -15f * unit);
            path.lineTo(-40f * unit, -40f * unit);
            path.lineTo(-34f * unit, 6f * unit);
            path.lineTo(-4f * unit, 15f * unit);
            path.close();
            c.drawPath(path, paint);

            // Phone body
            rect.set(-8.5f * unit, -18f * unit, 8.5f * unit, 18f * unit);
            paint.setColor(0xff141e19);
            c.drawRoundRect(rect, 3.8f * unit, 3.8f * unit, paint);

            // Phone illuminated screen
            rect.set(-6.5f * unit, -15f * unit, 6.5f * unit, 15f * unit);
            paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xff3b1d38 : 0xff163d35);
            c.drawRoundRect(rect, 2.4f * unit, 2.4f * unit, paint);

            // Animated mini feed item moving upward with thumbSwipePhase
            float feedY = 7.5f * unit - state.thumbSwipePhase * 16f * unit;
            paint.setColor(auraColor);
            rect.set(-4.8f * unit, feedY - 4.8f * unit, 4.8f * unit, feedY + 4.8f * unit);
            c.drawRoundRect(rect, 2f * unit, 2f * unit, paint);

            // Animated thumb swiping vertically across screen
            float thumbY = 9.5f * unit - (float) Math.sin(state.thumbSwipePhase * Math.PI) * 15f * unit;
            paint.setColor(0xffeab696);
            c.drawCircle(-6.5f * unit, thumbY, 4.0f * unit, paint);

            c.restore();
        } else {
            // Zen floating mindfulness sparkles
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99a6ddbd);
            float floatOff = state.breathOffset * 3.8f * unit;
            c.drawCircle(w * 0.5f - 48f * unit, cy - 34f * unit + floatOff, 3.3f * unit, paint);
            c.drawCircle(w * 0.5f + 48f * unit, cy - 26f * unit - floatOff, 4.0f * unit, paint);
        }
    }

    private void drawEye(Canvas c, float x, float y, float r, float unit) {
        if (state.eyeOpenness <= 0.14f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.3f * unit);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xff1e2824);
            rect.set(x - r, y - r * 0.5f, x + r, y + r * 0.6f);
            c.drawArc(rect, 15f, 150f, false, paint);
            return;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xfffff0ee : 0xffffffff);
        float ry = r * Math.min(1.25f, state.eyeOpenness);
        rect.set(x - r, y - ry, x + r, y + ry);
        c.drawOval(rect, paint);

        if (state.tier == ScrollAvatarState.TIER_ZOMBIE) {
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
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff182620);
            float py = y + state.pupilOffsetY * r * 0.42f;
            float px = x + 1.3f * unit;
            c.drawCircle(px, py, r * 0.48f, paint);

            paint.setColor(0xccffffff);
            c.drawCircle(px - r * 0.16f, py - r * 0.18f, r * 0.16f, paint);
        }

        if (state.upperLidDroop > 0.05f) {
            c.save();
            path.reset();
            rect.set(x - r, y - ry, x + r, y + ry);
            path.addOval(rect, Path.Direction.CW);
            c.clipPath(path);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(state.tier >= ScrollAvatarState.TIER_FATIGUED ? 0xffd2b09a : 0xffe2aa88);
            float lidBottom = (y - ry) + (ry * 2f * state.upperLidDroop);
            rect.set(x - r * 1.1f, y - ry * 1.1f, x + r * 1.1f, lidBottom);
            c.drawRect(rect, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f * unit);
            paint.setColor(0xff1e2824);
            c.drawLine(x - r, lidBottom, x + r, lidBottom, paint);
            c.restore();
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
