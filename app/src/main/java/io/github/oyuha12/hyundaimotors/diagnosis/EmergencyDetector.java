package io.github.oyuha12.hyundaimotors.diagnosis;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Warning;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rule-based screening of vehicle readings.
 *
 * <p>No API exposes the accelerator pedal, so unintended acceleration is inferred from its
 * observable effect: speed rising while the brake stays pressed. Thresholds are initial values
 * for the demo and have not been validated on a real vehicle. A steep downhill can match the
 * same pattern, so this is a warning aid, not a safety system.
 */
public final class EmergencyDetector {
    static final long HISTORY_MILLIS = 5_000L;
    static final long ACCELERATION_WINDOW_MILLIS = 1_500L;
    static final float MIN_ACCELERATION_SPEED_KPH = 10f;
    static final float MIN_SPEED_GAIN_KPH = 5f;
    static final long BRAKE_FAILURE_WINDOW_MILLIS = 3_000L;
    static final float MIN_BRAKE_FAILURE_SPEED_KPH = 30f;
    static final float MIN_EXPECTED_BRAKING_KPH = 1f;
    // Over 0.75 s this is about 1.3 g, beyond what the brakes alone produce on dry road.
    static final long COLLISION_WINDOW_MILLIS = 750L;
    static final float MIN_COLLISION_DROP_KPH = 35f;

    private final Deque<VehicleSnapshot> history = new ArrayDeque<>();

    /** Adds a reading. Returns the most serious condition it shows, or null if none. */
    @Nullable
    public EmergencyType evaluate(VehicleSnapshot snapshot) {
        history.addLast(snapshot);
        while (snapshot.timestampMillis - history.peekFirst().timestampMillis > HISTORY_MILLIS) {
            history.removeFirst();
        }

        if (isCollision(snapshot)) return EmergencyType.COLLISION_SUSPECTED;
        if (isUnintendedAcceleration(snapshot)) return EmergencyType.UNINTENDED_ACCELERATION;
        if (isBrakeFailure(snapshot)) return EmergencyType.BRAKE_FAILURE;
        if (snapshot.warnings.contains(Warning.OVERHEAT)) return EmergencyType.OVERHEAT;
        if (snapshot.warnings.contains(Warning.POWERTRAIN)) return EmergencyType.POWERTRAIN_FAULT;
        if (snapshot.warnings.contains(Warning.SAFETY_SYSTEM)) {
            return EmergencyType.SAFETY_SYSTEM_FAULT;
        }
        return null;
    }

    public void reset() {
        history.clear();
    }

    private boolean isCollision(VehicleSnapshot current) {
        if (current.speedKph == null) return false;
        for (VehicleSnapshot earlier : history) {
            boolean inWindow = current.timestampMillis - earlier.timestampMillis
                    <= COLLISION_WINDOW_MILLIS;
            if (inWindow
                    && earlier.speedKph != null
                    && earlier.speedKph - current.speedKph >= MIN_COLLISION_DROP_KPH) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnintendedAcceleration(VehicleSnapshot current) {
        if (current.speedKph == null
                || current.speedKph < MIN_ACCELERATION_SPEED_KPH
                || current.gear == Gear.PARK
                || current.gear == Gear.REVERSE) {
            return false;
        }
        VehicleSnapshot start = brakeHeldSince(current, ACCELERATION_WINDOW_MILLIS);
        return start != null
                && start.speedKph != null
                && current.speedKph - start.speedKph >= MIN_SPEED_GAIN_KPH;
    }

    private boolean isBrakeFailure(VehicleSnapshot current) {
        if (current.speedKph == null || current.speedKph < MIN_BRAKE_FAILURE_SPEED_KPH) {
            return false;
        }
        VehicleSnapshot start = brakeHeldSince(current, BRAKE_FAILURE_WINDOW_MILLIS);
        return start != null
                && start.speedKph != null
                && start.speedKph - current.speedKph < MIN_EXPECTED_BRAKING_KPH;
    }

    /**
     * Returns the reading from the start of the window, or null unless the brake has been
     * pressed for the whole window and the history actually covers it.
     */
    @Nullable
    private VehicleSnapshot brakeHeldSince(VehicleSnapshot current, long windowMillis) {
        VehicleSnapshot start = null;
        var readings = history.descendingIterator();
        while (readings.hasNext()) {
            VehicleSnapshot reading = readings.next();
            if (!Boolean.TRUE.equals(reading.brakePressed)) return null;
            start = reading;
            if (current.timestampMillis - reading.timestampMillis >= windowMillis) return start;
        }
        return null;
    }
}
