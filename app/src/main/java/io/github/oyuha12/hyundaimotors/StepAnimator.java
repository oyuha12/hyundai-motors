package io.github.oyuha12.hyundaimotors;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep.Motion;

/**
 * Loops a short motion on the step picture so the action is easy to grasp.
 *
 * <p>Pleos design guidance says animation must not draw the driver's eyes while driving, so the
 * motion runs only while parked, and never when the system has animations turned off. In those
 * cases the picture stays still in its resting pose.
 */
final class StepAnimator {
    private final View target;
    private final float density;
    @Nullable private Animator running;
    @Nullable private Motion motion;
    private boolean allowed;

    StepAnimator(View target) {
        this.target = target;
        this.density = target.getResources().getDisplayMetrics().density;
    }

    /** Shows a new step. The motion starts at once if allowed. */
    void show(Motion motion) {
        this.motion = motion;
        restart();
    }

    /** False while driving or when the screen is not visible. */
    void setAllowed(boolean allowed) {
        if (this.allowed == allowed) return;
        this.allowed = allowed;
        restart();
    }

    void stop() {
        if (running != null) {
            running.cancel();
            running = null;
        }
        resetPose();
    }

    private void restart() {
        stop();
        if (!allowed || motion == null || !ValueAnimator.areAnimatorsEnabled()) return;
        running = build(motion);
        running.start();
    }

    private void resetPose() {
        target.setTranslationX(0f);
        target.setTranslationY(0f);
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setRotation(0f);
        target.setRotationY(0f);
        target.setAlpha(1f);
    }

    private Animator build(Motion motion) {
        float dp = density;
        switch (motion) {
            case PRESS:
                return repeat(ObjectAnimator.ofPropertyValuesHolder(target,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, 14 * dp, 14 * dp, 0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.88f, 0.88f, 1f)), 1100, false);
            case PULL:
                return repeat(ObjectAnimator.ofFloat(
                        target, View.TRANSLATION_Y, 12 * dp, -14 * dp, -14 * dp, 12 * dp), 1400, false);
            case BLINK:
                return repeat(ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0.15f), 500, true);
            case PULSE:
                return repeat(ObjectAnimator.ofPropertyValuesHolder(target,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f)), 700, true);
            case SWAY:
                return repeat(ObjectAnimator.ofPropertyValuesHolder(target,
                        PropertyValuesHolder.ofFloat(View.ROTATION, -14f, 14f),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -10 * dp, 10 * dp)), 900, true);
            case MOVE_AWAY:
                return repeat(ObjectAnimator.ofPropertyValuesHolder(target,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -18 * dp, 26 * dp),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 1f, 0f)), 1300, false);
            case RISE:
                return repeat(ObjectAnimator.ofPropertyValuesHolder(target,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 22 * dp, -18 * dp),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 1f, 1f)), 1300, false);
            case SHAKE:
                return repeat(ObjectAnimator.ofFloat(target, View.TRANSLATION_X,
                        0f, -10 * dp, 10 * dp, -10 * dp, 10 * dp, 0f, 0f, 0f), 1200, false);
            case TAP:
                return repeat(ObjectAnimator.ofPropertyValuesHolder(target,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.82f, 1f, 0.82f, 1f, 1f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.82f, 1f, 0.82f, 1f, 1f, 1f)),
                        1200, false);
            case SWING:
            default:
                target.setCameraDistance(8000 * dp);
                return repeat(ObjectAnimator.ofFloat(target, View.ROTATION_Y, 0f, -55f), 900, true);
        }
    }

    private static Animator repeat(ObjectAnimator animator, long durationMillis, boolean reverse) {
        animator.setDuration(durationMillis);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(reverse ? ValueAnimator.REVERSE : ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.play(animator);
        return set;
    }
}
