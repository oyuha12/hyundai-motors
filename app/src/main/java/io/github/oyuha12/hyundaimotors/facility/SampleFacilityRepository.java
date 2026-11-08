package io.github.oyuha12.hyundaimotors.facility;

import androidx.annotation.Nullable;
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
        {Type.EVACUATION_SITE, "샘플 고지대 대피소", 650, 20},
        {Type.REPAIR_SHOP, "샘플 정비소 A", 900, 110},
        {Type.POLICE, "샘플 경찰서 A", 1_400, 200},
        {Type.EVACUATION_SITE, "샘플 대피소 B", 1_600, 230},
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
        double metersPerDegreeLongitude =
                METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(latitude));
        for (Object[] sample : SAMPLES) {
            int distance = (Integer) sample[2];
            int bearing = (Integer) sample[3];
            double radians = Math.toRadians(bearing);
            facilities.add(new Facility(
                    (Type) sample[0],
                    (String) sample[1],
                    latitude + distance * Math.cos(radians) / METERS_PER_DEGREE_LATITUDE,
                    longitude + distance * Math.sin(radians) / metersPerDegreeLongitude,
                    distance,
                    bearing));
        }
        facilities.sort(Comparator.comparingInt(facility -> facility.distanceMeters));
        return facilities;
    }

    @Nullable
    public Facility nearest(Type type, double latitude, double longitude) {
        for (Facility facility : findNearby(latitude, longitude)) {
            if (facility.type == type) return facility;
        }
        return null;
    }

    /** The nearest facility of each type, in the order given. Types with none are skipped. */
    public List<Facility> nearestOfEach(List<Type> types, double latitude, double longitude) {
        List<Facility> result = new ArrayList<>();
        for (Type type : types) {
            Facility facility = nearest(type, latitude, longitude);
            if (facility != null) result.add(facility);
        }
        return result;
    }
}
