package io.github.oyuha12.hyundaimotors.telemetry;

import androidx.annotation.Nullable;

/** A position in degrees. */
public final class GeoPoint {
    public final double latitude;
    public final double longitude;

    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** The reading's position, or null when it has none. */
    @Nullable
    public static GeoPoint of(VehicleSnapshot reading) {
        return reading.hasLocation() ? new GeoPoint(reading.latitude, reading.longitude) : null;
    }
}
