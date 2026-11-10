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
    /** Brake fluid level or pressure reported low. */
    public final boolean brakeFluidLow;
    /** Tire pressure per wheel (front left, front right, rear left, rear right), or null. */
    @Nullable private final float[] tirePressures;
    /** Steering wheel angle in degrees, negative to the left. */
    @Nullable public final Float steeringAngleDegrees;

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
        this(timestampMillis, speedKph, brakePressed, gear, airbagDeployed, cabinTemperatureC,
                overheatWarning, latitude, longitude, headingDegrees, false, null, null);
    }

    private VehicleSnapshot(
            long timestampMillis,
            @Nullable Float speedKph,
            @Nullable Boolean brakePressed,
            Gear gear,
            @Nullable Boolean airbagDeployed,
            @Nullable Float cabinTemperatureC,
            boolean overheatWarning,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable Float headingDegrees,
            boolean brakeFluidLow,
            @Nullable float[] tirePressures,
            @Nullable Float steeringAngleDegrees) {
        this.timestampMillis = timestampMillis;
        this.speedKph = speedKph;
        this.brakePressed = brakePressed;
        this.gear = gear;
        this.airbagDeployed = airbagDeployed;
        this.cabinTemperatureC = cabinTemperatureC;
        this.overheatWarning = overheatWarning;
        this.latitude = latitude;
        this.longitude = longitude;
        this.headingDegrees = headingDegrees;
        this.brakeFluidLow = brakeFluidLow;
        this.tirePressures = tirePressures == null ? null : tirePressures.clone();
        this.steeringAngleDegrees = steeringAngleDegrees;
    }

    /** A copy with chassis readings added: brake fluid, tire pressures, steering angle. */
    public VehicleSnapshot withChassis(
            boolean brakeFluidLow,
            @Nullable float[] tirePressures,
            @Nullable Float steeringAngleDegrees) {
        return new VehicleSnapshot(timestampMillis, speedKph, brakePressed, gear, airbagDeployed,
                cabinTemperatureC, overheatWarning, latitude, longitude, headingDegrees,
                brakeFluidLow, tirePressures, steeringAngleDegrees);
    }

    /** Pressure of one wheel (0-3), or null if unknown. NaN in the array also means unknown. */
    @Nullable
    public Float tirePressure(int wheel) {
        if (tirePressures == null || wheel >= tirePressures.length) return null;
        float value = tirePressures[wheel];
        return Float.isNaN(value) ? null : value;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
