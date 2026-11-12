package io.github.oyuha12.hyundaimotors;

import ai.pleos.playground.navi.data.TBTInfo;
import android.content.Context;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.TurnGuide;

final class Directions {
    private Directions() {}

    /** Example: "300m · 북동쪽". */
    static String describe(Context context, Facility facility) {
        String[] names = context.getResources().getStringArray(R.array.compass_directions);
        int sector = Math.round(facility.bearingDegrees / 45f) % names.length;
        return context.getString(R.string.distance_direction,
                distance(context, facility.distanceMeters), names[sector]);
    }

    /** Example: "다음 안내: 300m 앞 우회전". The navigation app's own words for unknown turns. */
    static String nextTurn(Context context, TBTInfo turn) {
        int label = TurnGuide.label(turn.getType());
        String what = label != 0
                ? context.getString(label)
                : turn.getDescription().trim().isEmpty()
                        ? context.getString(R.string.turn_unknown)
                        : turn.getDescription().trim();
        return context.getString(R.string.tbt_next,
                distance(context, (int) Math.round(turn.getDistance())), what);
    }

    /** Example: "300m" or "1.4km". */
    static String distance(Context context, int meters) {
        return meters >= 1_000
                ? context.getString(R.string.distance_km, meters / 1000f)
                : context.getString(R.string.distance_m, meters);
    }
}
