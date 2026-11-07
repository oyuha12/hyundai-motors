package io.github.oyuha12.hyundaimotors.diagnosis;

import androidx.annotation.StringRes;

/** One instruction: a short symbol for a glance, and text that is both shown and read aloud. */
public final class GuidanceStep {
    public final String symbol;
    @StringRes public final int text;

    GuidanceStep(String symbol, @StringRes int text) {
        this.symbol = symbol;
        this.text = text;
    }
}
