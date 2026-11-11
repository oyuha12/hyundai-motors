package io.github.oyuha12.hyundaimotors.facility;

/** Distance and direction between two positions on the Earth, in meters and degrees. */
public final class GeoMath {
    /** Mean Earth radius (IUGG), in meters. */
    static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoMath() {}

    /** Great-circle distance by the haversine formula. */
    public static double distanceMeters(
            double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double phi1 = Math.toRadians(fromLatitude);
        double phi2 = Math.toRadians(toLatitude);
        double halfDeltaPhi = (phi2 - phi1) / 2;
        double halfDeltaLambda = Math.toRadians(toLongitude - fromLongitude) / 2;
        double a = Math.sin(halfDeltaPhi) * Math.sin(halfDeltaPhi)
                + Math.cos(phi1) * Math.cos(phi2)
                        * Math.sin(halfDeltaLambda) * Math.sin(halfDeltaLambda);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Initial bearing from the first position to the second: 0 north, 90 east, in [0, 360). */
    public static double bearingDegrees(
            double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double phi1 = Math.toRadians(fromLatitude);
        double phi2 = Math.toRadians(toLatitude);
        double deltaLambda = Math.toRadians(toLongitude - fromLongitude);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return (degrees % 360 + 360) % 360;
    }
}
