package io.github.oyuha12.hyundaimotors.facility;

public final class Facility {
    public enum Type { SAFE_STOP, EVACUATION_SITE, FIRE_STATION, POLICE, HOSPITAL, REPAIR_SHOP }

    public final Type type;
    public final String name;
    public final double latitude;
    public final double longitude;
    public final int distanceMeters;
    public final int bearingDegrees;

    Facility(
            Type type,
            String name,
            double latitude,
            double longitude,
            int distanceMeters,
            int bearingDegrees) {
        this.type = type;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.bearingDegrees = bearingDegrees;
    }
}
