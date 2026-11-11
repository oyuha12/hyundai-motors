package io.github.oyuha12.hyundaimotors;

import android.content.Context;
import io.github.oyuha12.hyundaimotors.facility.Facility;

final class Directions {
    private Directions() {}

    /** Example: "300m · 북동쪽". */
    static String describe(Context context, Facility facility) {
        String[] names = context.getResources().getStringArray(R.array.compass_directions);
        int sector = Math.round(facility.bearingDegrees / 45f) % names.length;
        return context.getString(R.string.distance_direction,
                distance(context, facility.distanceMeters), names[sector]);
    }

    /** Example: "300m" or "1.4km". */
    static String distance(Context context, int meters) {
        return meters >= 1_000
                ? context.getString(R.string.distance_km, meters / 1000f)
                : context.getString(R.string.distance_m, meters);
    }
}
