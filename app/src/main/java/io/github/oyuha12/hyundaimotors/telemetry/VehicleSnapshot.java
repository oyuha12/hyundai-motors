package io.github.oyuha12.hyundaimotors.telemetry;

import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** One reading of the vehicle. Nullable fields mean the value is not available yet. */
public final class VehicleSnapshot {
    public enum Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

    public enum Warning { OVERHEAT, POWERTRAIN, SAFETY_SYSTEM }

    public final long timestampMillis;
    @Nullable public final Float speedKph;
    @Nullable public final Boolean brakePressed;
    public final Gear gear;
    public final Set<Warning> warnings;
    @Nullable public final Double latitude;
    @Nullable public final Double longitude;

    public VehicleSnapshot(
            long timestampMillis,
            @Nullable Float speedKph,
            @Nullable Boolean brakePressed,
            Gear gear,
            Set<Warning> warnings,
            @Nullable Double latitude,
            @Nullable Double longitude) {
        this.timestampMillis = timestampMillis;
        this.speedKph = speedKph;
        this.brakePressed = brakePressed;
        this.gear = gear;
        this.warnings = warnings.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(warnings));
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
