package io.github.oyuha12.hyundaimotors.facility;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import java.util.ArrayList;
import java.util.List;

/** Places that can help, with distance and direction from a position. */
public interface FacilityRepository {
    /** All places, nearest first, measured from the given position. */
    List<Facility> findNearby(double latitude, double longitude);

    /** The nearest place of one kind, or null if there is none. */
    @Nullable
    default Facility nearest(Type type, double latitude, double longitude) {
        for (Facility facility : findNearby(latitude, longitude)) {
            if (facility.type == type) return facility;
        }
        return null;
    }

    /** The nearest place of each kind, in the order given. Kinds with none are skipped. */
    default List<Facility> nearestOfEach(List<Type> types, double latitude, double longitude) {
        List<Facility> result = new ArrayList<>();
        for (Type type : types) {
            Facility facility = nearest(type, latitude, longitude);
            if (facility != null) result.add(facility);
        }
        return result;
    }
}
