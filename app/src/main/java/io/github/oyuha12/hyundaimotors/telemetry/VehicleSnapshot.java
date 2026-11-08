package io.github.oyuha12.hyundaimotors.telemetry;

import androidx.annotation.Nullable;

/** One reading of the vehicle. Nullable fields mean the value is not available. */
public final class VehicleSnapshot {
    public enum Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

    public final long timestampMillis;
    @Nullable public final Float speedKph;
    @Nullable public final Boolean brakePressed;
    public final Gear gear;
    @Nullable public final Boolean airbagDeployed;
    @Nullable public final Float cabinTemperatureC;
    /** Engine temperature warning light. */
    public final boolean overheatWarning;
    @Nullable public final Double latitude;
    @Nullable public final Double longitude;
    /** Direction of travel, 0-360 degrees clockwise from north. */
    @Nullable public final Float headingDegrees;

    public VehicleSnapshot(
            long timestampMillis,
            @Nullable Float speedKph,
            @Nullable Boolean brakePressed,
            Gear gear,
            @Nullable Boolean airbagDeployed,
            @Nullable Float cabinTemperatureC,
            boolean overheatWarning,
            @Nullable Double latitude,
            @Nullable Double longitude) {
        this(timestampMillis, speedKph, brakePressed, gear, airbagDeployed, cabinTemperatureC,
                overheatWarning, latitude, longitude, null);
    }

    public VehicleSnapshot(
            long timestampMillis,
            @Nullable Float speedKph,
            @Nullable Boolean brakePressed,
            Gear gear,
            @Nullable Boolean airbagDeployed,
            @Nullable Float cabinTemperatureC,
            boolean overheatWarning,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable Float headingDegrees) {
        this.timestampMillis = timestampMillis;
        this.headingDegrees = headingDegrees;
        this.speedKph = speedKph;
        this.brakePressed = brakePressed;
        this.gear = gear;
        this.airbagDeployed = airbagDeployed;
        this.cabinTemperatureC = cabinTemperatureC;
        this.overheatWarning = overheatWarning;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
