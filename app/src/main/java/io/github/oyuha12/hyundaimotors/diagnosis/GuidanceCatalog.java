package io.github.oyuha12.hyundaimotors.diagnosis;

import androidx.annotation.StringRes;
import io.github.oyuha12.hyundaimotors.R;
import java.util.Arrays;
import java.util.List;

/**
 * Response steps for each condition. This is general guidance and must be reviewed against the
 * owner's manual of the target vehicle before real use.
 */
public final class GuidanceCatalog {
    private GuidanceCatalog() {}

    public static List<GuidanceStep> stepsFor(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return Arrays.asList(
                        new GuidanceStep("브레이크", R.string.step_ua_brake),
                        new GuidanceStep("N", R.string.step_ua_neutral),
                        new GuidanceStep("P", R.string.step_ua_parking_brake),
                        new GuidanceStep("▲", R.string.step_ua_steer),
                        new GuidanceStep("견인", R.string.step_ua_after));
            case BRAKE_FAILURE:
                return Arrays.asList(
                        new GuidanceStep("브레이크", R.string.step_brake_press),
                        new GuidanceStep("P", R.string.step_brake_parking),
                        new GuidanceStep("▲", R.string.step_brake_steer),
                        new GuidanceStep("견인", R.string.step_brake_after));
            case COLLISION_SUSPECTED:
                return Arrays.asList(
                        new GuidanceStep("▲", R.string.step_crash_hazard),
                        new GuidanceStep("+", R.string.step_crash_injury),
                        new GuidanceStep("대피", R.string.step_crash_evacuate),
                        new GuidanceStep("신고", R.string.step_crash_report));
            case OVERHEAT:
                return Arrays.asList(
                        new GuidanceStep("▲", R.string.step_heat_stop),
                        new GuidanceStep("OFF", R.string.step_heat_engine),
                        new GuidanceStep("!", R.string.step_heat_hood),
                        new GuidanceStep("정비", R.string.step_heat_service));
            case POWERTRAIN_FAULT:
                return Arrays.asList(
                        new GuidanceStep("▲", R.string.step_power_stop),
                        new GuidanceStep("!", R.string.step_power_check),
                        new GuidanceStep("정비", R.string.step_power_service));
            case SAFETY_SYSTEM_FAULT:
            default:
                return Arrays.asList(
                        new GuidanceStep("!", R.string.step_safety_caution),
                        new GuidanceStep("정비", R.string.step_safety_service));
        }
    }

    @StringRes
    public static int titleFor(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return R.string.emergency_ua;
            case BRAKE_FAILURE:
                return R.string.emergency_brake;
            case COLLISION_SUSPECTED:
                return R.string.emergency_collision;
            case OVERHEAT:
                return R.string.emergency_overheat;
            case POWERTRAIN_FAULT:
                return R.string.emergency_powertrain;
            case SAFETY_SYSTEM_FAULT:
            default:
                return R.string.emergency_safety;
        }
    }
}
