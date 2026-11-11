package io.github.oyuha12.hyundaimotors;

import android.content.Context;
import io.github.oyuha12.hyundaimotors.facility.Facility;

final class Directions {
    private Directions() {}

    /** Example: "300m · 북동쪽". */
    static String describe(Context context, Facility facility) {
        String[] names = context.getResources().getStringArray(R.array.compass_directions);
        int sector = Math.round(facility.bearingDegrees / 45f) % names.length;
        String distance = facility.distanceMeters >= 1_000
                ? context.getString(R.string.distance_km, facility.distanceMeters / 1000f)
                : context.getString(R.string.distance_m, facility.distanceMeters);
        return context.getString(R.string.distance_direction, distance, names[sector]);
    }
}
