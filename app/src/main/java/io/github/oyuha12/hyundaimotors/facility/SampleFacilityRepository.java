package io.github.oyuha12.hyundaimotors.facility;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fictional facilities placed around the current position, so the flow can be shown anywhere.
 * The Pleos SDK has no facility search. Replace this with a real place search before release:
 * these places do not exist.
 */
public final class SampleFacilityRepository {
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320d;

    private static final Object[][] SAMPLES = {
        {Type.SAFE_STOP, "샘플 안전 정차 구역", 300, 40},
        {Type.REPAIR_SHOP, "샘플 정비소 A", 900, 110},
        {Type.POLICE, "샘플 경찰서 A", 1_400, 200},
        {Type.HOSPITAL, "샘플 병원 A", 1_800, 320},
        {Type.FIRE_STATION, "샘플 소방서 A", 2_300, 70},
        {Type.REPAIR_SHOP, "샘플 정비소 B", 2_700, 250},
        {Type.HOSPITAL, "샘플 병원 B", 3_200, 150},
        {Type.POLICE, "샘플 경찰서 B", 3_900, 15},
        {Type.FIRE_STATION, "샘플 소방서 B", 4_400, 280},
    };

    /** Returns all sample facilities, nearest first. */
    public List<Facility> findNearby(double latitude, double longitude) {
        List<Facility> facilities = new ArrayList<>();
        for (Object[] sample : SAMPLES) {
            int distance = (Integer) sample[2];
            int bearing = (Integer) sample[3];
            double radians = Math.toRadians(bearing);
            double north = distance * Math.cos(radians);
            double east = distance * Math.sin(radians);
            double metersPerDegreeLongitude =
                    METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(latitude));
            facilities.add(new Facility(
                    (Type) sample[0],
                    (String) sample[1],
                    latitude + north / METERS_PER_DEGREE_LATITUDE,
                    longitude + east / metersPerDegreeLongitude,
                    distance,
                    bearing));
        }
        facilities.sort(Comparator.comparingInt(facility -> facility.distanceMeters));
        return facilities;
    }

    /** Picks the nearest facility that suits the condition. */
    @Nullable
    public Facility recommendedFor(EmergencyType emergency, double latitude, double longitude) {
        Type wanted = preferredType(emergency);
        for (Facility facility : findNearby(latitude, longitude)) {
            if (facility.type == wanted) return facility;
        }
        return null;
    }

    static Type preferredType(EmergencyType emergency) {
        switch (emergency) {
            case COLLISION_SUSPECTED:
                return Type.HOSPITAL;
            case UNINTENDED_ACCELERATION:
            case BRAKE_FAILURE:
                return Type.SAFE_STOP;
            default:
                return Type.REPAIR_SHOP;
        }
    }
}
