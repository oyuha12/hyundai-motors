package io.github.oyuha12.hyundaimotors.diagnosis;

import androidx.annotation.StringRes;
import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep.Motion;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import java.util.Arrays;
import java.util.List;

/**
 * What each situation screen shows: response steps, where to evacuate, and which facilities
 * can help. Steps follow the team's response table ("사고 발생, 대처방법.xlsx") and public
 * guidance reported by Korean media. Review them against official sources and the vehicle's
 * owner's manual before real use.
 */
public final class EmergencyPlan {
    private EmergencyPlan() {}

    public static List<GuidanceStep> steps(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                // The team's confirmed voice script. The engine goes off only once the car stops.
                return Arrays.asList(
                        new GuidanceStep("N", Motion.PRESS, R.string.step_ua_neutral),
                        new GuidanceStep(R.drawable.ic_footprint, Motion.PRESS, R.string.step_ua_brake),
                        new GuidanceStep("P", Motion.PULL, R.string.step_ua_parking_brake).untilStopped(),
                        new GuidanceStep(R.drawable.ic_power_settings_new, Motion.PULSE, R.string.step_ua_power_off));
            case BRAKE_FAILURE:
                return Arrays.asList(
                        new GuidanceStep("−", Motion.PRESS, R.string.step_brake_downshift),
                        new GuidanceStep("P", Motion.PULL, R.string.step_brake_parking),
                        new GuidanceStep(R.drawable.ic_warning, Motion.BLINK, R.string.step_brake_hazard),
                        new GuidanceStep(R.drawable.ic_directions_car, Motion.SWAY, R.string.step_brake_last_resort));
            case ACCIDENT:
                return Arrays.asList(
                        new GuidanceStep(R.drawable.ic_warning, Motion.BLINK, R.string.step_accident_hazard),
                        new GuidanceStep(R.drawable.ic_personal_injury, Motion.PULSE, R.string.step_accident_injury),
                        new GuidanceStep(R.drawable.ic_directions_walk, Motion.MOVE_AWAY, R.string.step_accident_evacuate),
                        new GuidanceStep(R.drawable.ic_emergency, Motion.PULL, R.string.step_accident_triangle),
                        new GuidanceStep(R.drawable.ic_sos, Motion.PULSE, R.string.step_accident_report));
            case FIRE:
                // The team's confirmed voice script. Evacuation starts only once the car stops.
                return Arrays.asList(
                        new GuidanceStep(R.drawable.ic_warning, Motion.BLINK, R.string.step_fire_stop).untilStopped(),
                        new GuidanceStep(R.drawable.ic_directions_walk, Motion.MOVE_AWAY, R.string.step_fire_engine),
                        new GuidanceStep(R.drawable.ic_do_not_touch, Motion.SHAKE, R.string.step_fire_hood),
                        new GuidanceStep(R.drawable.ic_sos, Motion.PULSE, R.string.step_fire_report));
            case FLOOD:
                return Arrays.asList(
                        new GuidanceStep(R.drawable.ic_window, Motion.SWING, R.string.step_flood_open),
                        new GuidanceStep(R.drawable.ic_door_open, Motion.SWING, R.string.step_flood_belt),
                        new GuidanceStep(R.drawable.ic_window, Motion.TAP, R.string.step_flood_window),
                        new GuidanceStep(R.drawable.ic_hiking, Motion.RISE, R.string.step_flood_high_ground));
            case TIRE_FAILURE:
                return Arrays.asList(
                        new GuidanceStep(R.drawable.ic_footprint, Motion.SHAKE, R.string.step_tire_no_brake),
                        new GuidanceStep(R.drawable.ic_directions_car, Motion.PULSE, R.string.step_tire_hold),
                        new GuidanceStep(R.drawable.ic_speed, Motion.PRESS, R.string.step_tire_slow),
                        new GuidanceStep(R.drawable.ic_directions_car, Motion.SWAY, R.string.step_tire_shoulder));
            case DRIVER_HEALTH:
                return Arrays.asList(
                        new GuidanceStep(R.drawable.ic_ecg_heart, Motion.BLINK, R.string.step_health_wake),
                        new GuidanceStep(R.drawable.ic_warning, Motion.BLINK, R.string.step_health_passenger),
                        new GuidanceStep(R.drawable.ic_personal_injury, Motion.PULSE, R.string.step_health_check),
                        new GuidanceStep(R.drawable.ic_sos, Motion.PULSE, R.string.step_health_report));
            case SECONDARY_ACCIDENT:
            default:
                return Arrays.asList(
                        new GuidanceStep(R.drawable.ic_warning, Motion.BLINK, R.string.step_secondary_signal),
                        new GuidanceStep(R.drawable.ic_directions_walk, Motion.MOVE_AWAY, R.string.step_secondary_evacuate),
                        new GuidanceStep(R.drawable.ic_sos, Motion.PULSE, R.string.step_secondary_report));
        }
    }

    @StringRes
    public static int title(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return R.string.emergency_ua;
            case BRAKE_FAILURE:
                return R.string.emergency_brake;
            case ACCIDENT:
                return R.string.emergency_accident;
            case FIRE:
                return R.string.emergency_fire;
            case FLOOD:
                return R.string.emergency_flood;
            case TIRE_FAILURE:
                return R.string.emergency_tire;
            case DRIVER_HEALTH:
                return R.string.emergency_health;
            case SECONDARY_ACCIDENT:
            default:
                return R.string.emergency_secondary;
        }
    }

    /**
     * The situation's name in "○○이(가) 의심됩니다". The voice script calls a fire "차량 화재";
     * other situations use their title.
     */
    @StringRes
    public static int suspectedName(EmergencyType type) {
        return type == EmergencyType.FIRE ? R.string.suspected_name_fire : title(type);
    }

    /** Where people should go. */
    public static Type evacuationTarget(EmergencyType type) {
        switch (type) {
            case FIRE:
            case FLOOD:
                return Type.EVACUATION_SITE;
            default:
                return Type.SAFE_STOP;
        }
    }

    @StringRes
    public static int evacuationInstruction(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return R.string.evacuation_ua;
            case BRAKE_FAILURE:
                return R.string.evacuation_brake;
            case ACCIDENT:
                return R.string.evacuation_accident;
            case FIRE:
                return R.string.evacuation_fire;
            case FLOOD:
                return R.string.evacuation_flood;
            case TIRE_FAILURE:
                return R.string.evacuation_tire;
            case DRIVER_HEALTH:
                return R.string.evacuation_health;
            case SECONDARY_ACCIDENT:
            default:
                return R.string.evacuation_secondary;
        }
    }

    /** Facilities that can help, most relevant first. */
    public static List<Type> supportTypes(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return Arrays.asList(Type.POLICE, Type.ROAD_AUTHORITY, Type.HOSPITAL, Type.REPAIR_SHOP);
            case BRAKE_FAILURE:
                return Arrays.asList(Type.POLICE, Type.ROAD_AUTHORITY, Type.HOSPITAL, Type.REPAIR_SHOP);
            case ACCIDENT:
                return Arrays.asList(Type.HOSPITAL, Type.FIRE_STATION, Type.POLICE, Type.ROAD_AUTHORITY);
            case FIRE:
                return Arrays.asList(Type.FIRE_STATION, Type.HOSPITAL, Type.POLICE);
            case FLOOD:
                return Arrays.asList(Type.FIRE_STATION, Type.POLICE, Type.HOSPITAL, Type.ROAD_AUTHORITY);
            case TIRE_FAILURE:
                return Arrays.asList(Type.ROAD_AUTHORITY, Type.REPAIR_SHOP, Type.POLICE);
            case DRIVER_HEALTH:
                return Arrays.asList(Type.HOSPITAL, Type.FIRE_STATION, Type.POLICE);
            case SECONDARY_ACCIDENT:
            default:
                return Arrays.asList(Type.ROAD_AUTHORITY, Type.POLICE, Type.HOSPITAL);
        }
    }

    /**
     * A driver who may be unconscious cannot ask for help, so confirming this situation also
     * records a (mock) report right away.
     */
    public static boolean reportsOnConfirm(EmergencyType type) {
        return type == EmergencyType.DRIVER_HEALTH;
    }
}
