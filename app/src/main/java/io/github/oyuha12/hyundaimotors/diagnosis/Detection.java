package io.github.oyuha12.hyundaimotors.diagnosis;

import androidx.annotation.StringRes;

/** A detected condition and the evidence for it, shown to the driver. */
public final class Detection {
    public final EmergencyType type;
    @StringRes public final int reason;

    Detection(EmergencyType type, @StringRes int reason) {
        this.type = type;
        this.reason = reason;
    }
}
