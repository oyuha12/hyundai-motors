package io.github.oyuha12.hyundaimotors.facility;

import ai.pleos.playground.navi.constants.TurnType;
import ai.pleos.playground.navi.data.TBTInfo;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import io.github.oyuha12.hyundaimotors.R;
import java.util.List;

/** Turn-by-turn information from the navigation app (NaviHelper onTBTInfo), in short words. */
public final class TurnGuide {
    private TurnGuide() {}

    /** The nearest turn ahead, or null if there is none. */
    @Nullable
    public static TBTInfo next(@Nullable List<TBTInfo> turns) {
        if (turns == null) return null;
        TBTInfo nearest = null;
        for (TBTInfo turn : turns) {
            if (turn == null || turn.getDistance() < 0) continue;
            if (nearest == null || turn.getDistance() < nearest.getDistance()) nearest = turn;
        }
        return nearest;
    }

    /**
     * The turn in words, such as "우회전". 0 for an unknown turn: then the navigation app's own
     * description is shown instead.
     */
    @StringRes
    public static int label(TurnType type) {
        switch (type) {
            case TURN_STRAIGHT:
                return R.string.turn_straight;
            case TURN_RIGHT:
                return R.string.turn_right;
            case TURN_LEFT:
                return R.string.turn_left;
            case TURN_KEEP_R:
                return R.string.turn_keep_right;
            case TURN_KEEP_L:
                return R.string.turn_keep_left;
            case TURN_SHARP_R:
                return R.string.turn_sharp_right;
            case TURN_SHARP_L:
                return R.string.turn_sharp_left;
            case TURN_UTURN:
                return R.string.turn_uturn;
            case TURN_ENTER_IC_R:
            case TURN_ENTER_IC_L:
            case TURN_ENTER_IC_C:
                return R.string.turn_enter_ic;
            case TURN_EXIT_IC_R:
            case TURN_EXIT_IC_L:
            case TURN_EXIT_IC_C:
                return R.string.turn_exit_ic;
            case TURN_ENTER_UNDER_R:
            case TURN_ENTER_UNDER_L:
            case TURN_ENTER_UNDER_C:
                return R.string.turn_enter_underpass;
            case TURN_AVOID_UNDER_R:
            case TURN_AVOID_UNDER_L:
            case TURN_AVOID_UNDER_C:
                return R.string.turn_avoid_underpass;
            case TURN_ENTER_OVER_R:
            case TURN_ENTER_OVER_L:
            case TURN_ENTER_OVER_C:
                return R.string.turn_enter_overpass;
            case TURN_AVOID_OVER_R:
            case TURN_AVOID_OVER_L:
            case TURN_AVOID_OVER_C:
                return R.string.turn_avoid_overpass;
            case TURN_ENTER_ROUNDABOUT_02:
            case TURN_ENTER_ROUNDABOUT_03:
            case TURN_ENTER_ROUNDABOUT_04:
            case TURN_ENTER_ROUNDABOUT_06:
            case TURN_ENTER_ROUNDABOUT_08:
            case TURN_ENTER_ROUNDABOUT_09:
            case TURN_ENTER_ROUNDABOUT_10:
            case TURN_ENTER_ROUNDABOUT_12:
                return R.string.turn_enter_roundabout;
            case TURN_EXIT_ROUNDABOUT:
                return R.string.turn_exit_roundabout;
            case TURN_WAY:
                return R.string.turn_waypoint;
            case TURN_ARRIVE:
                return R.string.turn_arrive;
            case TURN_TUNNEL:
                return R.string.turn_tunnel;
            case TURN_DEVIDE_MERGE:
                return R.string.turn_merge;
            case TURN_TOLL:
                return R.string.turn_toll;
            case TURN_ENTER_SERVICEAREA:
                return R.string.turn_service_area;
            case TURN_RESTAREA:
                return R.string.turn_rest_area;
            case TURN_UNKNOWN:
            default:
                return 0;
        }
    }
}
