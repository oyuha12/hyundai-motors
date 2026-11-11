package io.github.oyuha12.hyundaimotors.facility;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * One line of the evacuation and dispatch status: a kind of place, and the nearest one if it is
 * known. An unknown place still gets its line, so the screen can say "장소 미설정".
 */
public final class StatusPlace {
    public final Type type;
    @Nullable public final Facility facility;

    public StatusPlace(Type type, @Nullable Facility facility) {
        this.type = type;
        this.facility = facility;
    }

    /** One line per kind, in the order given, each with its nearest place or none. */
    public static List<StatusPlace> listFor(
            FacilityRepository places, List<Type> types, double latitude, double longitude) {
        List<StatusPlace> result = new ArrayList<>();
        for (Type type : types) {
            result.add(new StatusPlace(type, places.nearest(type, latitude, longitude)));
        }
        return result;
    }
}
