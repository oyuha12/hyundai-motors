package io.github.oyuha12.hyundaimotors.diagnosis;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * One instruction: a picture for a glance, a short looping motion that shows the action, and
 * text that is both shown and read aloud.
 */
public final class GuidanceStep {
    /** How the picture moves. Each mirrors the physical action of the step. */
    public enum Motion {
        /** Pushes down and back, like pressing a pedal. */
        PRESS,
        /** Slides up and holds, like pulling a switch. */
        PULL,
        /** Fades on and off, like hazard lights. */
        BLINK,
        /** Grows and shrinks, to draw attention. */
        PULSE,
        /** Sways left and right, like steering. */
        SWAY,
        /** Moves away and fades, like leaving a place. */
        MOVE_AWAY,
        /** Rises up, like climbing to higher ground. */
        RISE,
        /** Shakes, meaning "do not". */
        SHAKE,
        /** Taps forward twice, like striking glass. */
        TAP,
        /** Swings open, like a door. */
        SWING
    }

    /** Shown when there is no icon, for example a gear letter. */
    @Nullable public final String symbol;
    @DrawableRes public final int icon;
    public final Motion motion;
    @StringRes public final int text;

    GuidanceStep(@DrawableRes int icon, Motion motion, @StringRes int text) {
        this(null, icon, motion, text);
    }

    GuidanceStep(String symbol, Motion motion, @StringRes int text) {
        this(symbol, 0, motion, text);
    }

    private GuidanceStep(
            @Nullable String symbol, @DrawableRes int icon, Motion motion, @StringRes int text) {
        this.symbol = symbol;
        this.icon = icon;
        this.motion = motion;
        this.text = text;
    }

    public boolean hasIcon() {
        return icon != 0;
    }
}
