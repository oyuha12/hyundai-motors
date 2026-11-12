package io.github.oyuha12.hyundaimotors.facility;

import androidx.annotation.Nullable;

public final class Facility {
    public enum Type {
        SAFE_STOP,
        EVACUATION_SITE,
        FIRE_STATION,
        POLICE,
        HOSPITAL,
        /** Expressway operator branch: patrol and emergency towing. */
        ROAD_AUTHORITY,
        REPAIR_SHOP
    }

    public final Type type;
    public final String name;
    public final double latitude;
    public final double longitude;
    public final int distanceMeters;
    public final int bearingDegrees;
    /** Street address for the navigation app, or null when unknown (sample places). */
    @Nullable public final String address;

    Facility(
            Type type,
            String name,
            double latitude,
            double longitude,
            int distanceMeters,
            int bearingDegrees,
            @Nullable String address) {
        this.type = type;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.bearingDegrees = bearingDegrees;
        this.address = address;
    }
}
