package io.github.oyuha12.hyundaimotors.facility;

import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places at fixed, known positions, such as the real places in the demo video. Distance comes
 * from the haversine formula and direction from the initial bearing, both from the current
 * position.
 */
public final class FixedFacilityRepository implements FacilityRepository {
    private static final class Place {
        final Type type;
        final String name;
        final double latitude;
        final double longitude;

        Place(Type type, String name, double latitude, double longitude) {
            this.type = type;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private final List<Place> places = new ArrayList<>();

    public FixedFacilityRepository add(Type type, String name, double latitude, double longitude) {
        places.add(new Place(type, name, latitude, longitude));
        return this;
    }

    @Override
    public List<Facility> findNearby(double latitude, double longitude) {
        List<Facility> result = new ArrayList<>();
        for (Place place : places) {
            double distance =
                    GeoMath.distanceMeters(latitude, longitude, place.latitude, place.longitude);
            double bearing =
                    GeoMath.bearingDegrees(latitude, longitude, place.latitude, place.longitude);
            result.add(new Facility(
                    place.type,
                    place.name,
                    place.latitude,
                    place.longitude,
                    (int) Math.round(distance),
                    (int) Math.round(bearing) % 360));
        }
        result.sort(Comparator.comparingInt(facility -> facility.distanceMeters));
        return result;
    }
}
