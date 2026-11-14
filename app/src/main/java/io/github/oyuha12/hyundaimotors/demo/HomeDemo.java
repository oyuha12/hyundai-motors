package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;

/**
 * Home screen demo outside video mode. "주행으로 전환" plays a lively cruising speed
 * ({@link Scenario#DEMO_CRUISE}). The 급발진 and 화재 buttons first change the values the way the
 * situation would, then ask after {@link #BUILD_UP_MILLIS}; the other buttons ask at once.
 *
 * <p>While a build-up plays, no detection or reminder answers the staged values: the question
 * comes from the button, once.
 */
public final class HomeDemo {
    /** From the button to the question. */
    public static final long BUILD_UP_MILLIS = 2_000L;

    private HomeDemo() {}

    /** The values a situation button plays before asking, or null to ask at once. */
    @Nullable
    public static Scenario buildUp(EmergencyType type, boolean videoMode) {
        if (videoMode) return null;
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return Scenario.DEMO_ACCELERATION;
            case FIRE:
                return Scenario.DEMO_FIRE;
            default:
                return null;
        }
    }

    public static boolean isBuildUp(@Nullable Scenario scenario) {
        return scenario == Scenario.DEMO_ACCELERATION || scenario == Scenario.DEMO_FIRE;
    }

    /** What the question and the situation screen say happened. */
    @StringRes
    public static int reason(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return R.string.reason_demo_acceleration;
            case FIRE:
                return R.string.reason_cabin_heat;
            default:
                return R.string.reason_manual;
        }
    }
}
