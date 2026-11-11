package io.github.oyuha12.hyundaimotors.drowsiness;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Voice warnings for driver mistakes from the team's response table. The table also asks the
 * car to cut engine power or brake automatically; no Pleos API allows that, so the app can only
 * warn. Like the drowsiness reminder, this never opens an emergency screen.
 *
 * <ul>
 *   <li>Pedal mix-up: the car launches hard from a standstill with no brake. The table's
 *       signals (accelerator position, obstacle sensors) are not available, so a sharp launch
 *       stands in for them.
 *   <li>Wrong gear: reversing fast, which in a parking lot usually means the wrong gear or pedal.
 *   <li>Curve overspeed: speed and steering angle together imply a hard turn.
 * </ul>
 *
 * Thresholds are initial values for the demo, not validated on a real vehicle.
 */
public final class DrivingWarningMonitor {
    public enum Warning { PEDAL_MISAPPLICATION, FAST_REVERSE, CURVE_OVERSPEED }

    static final long LAUNCH_WINDOW_MILLIS = 1_500L;
    static final float STANDSTILL_KPH = 5f;
    /** 25 km/h within 1.5 s from rest is about 4.6 m/s², far harder than a normal start. */
    static final float LAUNCH_SPEED_KPH = 25f;
    static final float FAST_REVERSE_KPH = 15f;
    static final float MIN_CURVE_SPEED_KPH = 50f;
    /** Sideways acceleration that feels like a hard turn for ordinary drivers. */
    static final double MAX_LATERAL_ACCELERATION = 4.0;
    /** Typical passenger car: steering wheel to road wheel ratio, and wheelbase. */
    static final double STEERING_RATIO = 15.0;
    static final double WHEELBASE_METERS = 2.9;
    static final long COOLDOWN_MILLIS = 20_000L;

    private final Deque<VehicleSnapshot> history = new ArrayDeque<>();
    private final Map<Warning, Long> lastWarned = new EnumMap<>(Warning.class);

    @Nullable
    public Warning update(VehicleSnapshot reading) {
        history.addLast(reading);
        while (reading.timestampMillis - history.peekFirst().timestampMillis > LAUNCH_WINDOW_MILLIS) {
            history.removeFirst();
        }
        Warning warning = detect(reading);
        if (warning == null) return null;
        Long last = lastWarned.get(warning);
        if (last != null && reading.timestampMillis - last < COOLDOWN_MILLIS) return null;
        lastWarned.put(warning, reading.timestampMillis);
        return warning;
    }

    public void reset() {
        history.clear();
        lastWarned.clear();
    }

    @Nullable
    private Warning detect(VehicleSnapshot reading) {
        if (reading.speedKph == null) return null;
        if (reading.gear == Gear.REVERSE && reading.speedKph >= FAST_REVERSE_KPH) {
            return Warning.FAST_REVERSE;
        }
        if (isHardLaunch(reading)) return Warning.PEDAL_MISAPPLICATION;
        if (lateralAcceleration(reading) >= MAX_LATERAL_ACCELERATION) return Warning.CURVE_OVERSPEED;
        return null;
    }

    private boolean isHardLaunch(VehicleSnapshot current) {
        if (current.gear != Gear.DRIVE
                || Boolean.TRUE.equals(current.brakePressed)
                || current.speedKph < LAUNCH_SPEED_KPH) {
            return false;
        }
        for (VehicleSnapshot earlier : history) {
            if (earlier.speedKph != null && earlier.speedKph < STANDSTILL_KPH) return true;
        }
        return false;
    }

    /** m/s², from a simple bicycle model. 0 when steering or speed is unknown or low. */
    static double lateralAcceleration(VehicleSnapshot reading) {
        if (reading.speedKph == null || reading.steeringAngleDegrees == null
                || reading.speedKph < MIN_CURVE_SPEED_KPH) {
            return 0;
        }
        double roadWheel = Math.toRadians(Math.abs(reading.steeringAngleDegrees) / STEERING_RATIO);
        double speed = reading.speedKph / 3.6;
        return speed * speed * Math.tan(roadWheel) / WHEELBASE_METERS;
    }
}
