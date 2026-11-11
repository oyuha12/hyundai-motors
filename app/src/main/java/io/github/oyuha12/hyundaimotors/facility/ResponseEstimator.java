package io.github.oyuha12.hyundaimotors.facility;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;

/**
 * Estimates how long a responder takes to reach the vehicle once a report is received:
 * preparation time, then road distance at a typical average speed for that kind of vehicle.
 *
 * <p>This is a rough planning figure. Road distance is approximated from straight-line distance
 * because no routing data is available here, and there is no live traffic. Real arrival times
 * vary widely; the screen always labels these values as estimates.
 */
public final class ResponseEstimator {
    /** Roads are longer than the straight line between two points. */
    static final double ROAD_DETOUR_FACTOR = 1.3;

    private ResponseEstimator() {}

    /** Whether this kind of place sends someone to the vehicle. */
    public static boolean dispatches(Type type) {
        return preparationSeconds(type) >= 0;
    }

    /** Estimated arrival time in milliseconds after the report, or null if nobody comes. */
    @Nullable
    public static Long arrivalMillis(Facility facility) {
        int preparation = preparationSeconds(facility.type);
        if (preparation < 0) return null;
        double roadKm = facility.distanceMeters / 1000.0 * ROAD_DETOUR_FACTOR;
        double driveSeconds = roadKm / averageSpeedKph(facility.type) * 3600.0;
        return Math.round((preparation + driveSeconds) * 1000.0);
    }

    /** Whole minutes, rounded up, at least 1. Null if nobody comes. */
    @Nullable
    public static Integer arrivalMinutes(Facility facility) {
        Long millis = arrivalMillis(facility);
        if (millis == null) return null;
        return (int) Math.max(1, Math.ceil(millis / 60_000.0));
    }

    /** Time from the call to departure. -1 for places that do not dispatch. */
    static int preparationSeconds(Type type) {
        switch (type) {
            case FIRE_STATION:
                return 60;
            case POLICE:
                return 60;
            case HOSPITAL:
                return 90; // Ambulance
            case ROAD_AUTHORITY:
                return 300; // Patrol or emergency tow
            case REPAIR_SHOP:
                return 600; // Tow truck
            default:
                return -1;
        }
    }

    /** Average speed including junctions and traffic, km/h. */
    static double averageSpeedKph(Type type) {
        switch (type) {
            case FIRE_STATION:
            case HOSPITAL:
                return 40;
            case POLICE:
                return 45;
            case ROAD_AUTHORITY:
                return 50;
            case REPAIR_SHOP:
            default:
                return 35;
        }
    }
}
