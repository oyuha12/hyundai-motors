package io.github.oyuha12.hyundaimotors.diagnosis;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Rule-based screening of vehicle readings. Thresholds are initial values for the demo and
 * have not been validated on a real vehicle. This is a warning aid, not a safety system.
 *
 * <ul>
 *   <li>Unintended acceleration: no API exposes the accelerator pedal, so it is inferred from
 *       speed rising while the brake stays pressed. A steep downhill can look the same.
 *   <li>Accident: an airbag deploying while the app runs, or a speed drop sharper than
 *       braking alone produces.
 *   <li>Fire: the engine temperature warning, or cabin temperature both high and rising fast.
 *       No smoke or flame sensor is available.
 *   <li>Brake failure: brake held at speed without slowing down, or a low brake fluid warning.
 *   <li>Tire failure: a tire losing a quarter of its pressure within 10 s, as when a wheel
 *       comes off or bursts.
 *   <li>Flooding, driver health and secondary accidents: no sensor reports these, so they are
 *       chosen by the user.
 * </ul>
 */
public final class EmergencyDetector {
    static final long HISTORY_MILLIS = 5_000L;
    static final long ACCELERATION_WINDOW_MILLIS = 1_500L;
    static final float MIN_ACCELERATION_SPEED_KPH = 10f;
    static final float MIN_SPEED_GAIN_KPH = 5f;
    // Over 0.75 s this is about 1.3 g, beyond what the brakes alone produce on dry road.
    static final long COLLISION_WINDOW_MILLIS = 750L;
    static final float MIN_COLLISION_DROP_KPH = 35f;
    static final long CABIN_WINDOW_MILLIS = 60_000L;
    static final float MIN_FIRE_CABIN_C = 50f;
    static final float MIN_FIRE_CABIN_RISE_C = 10f;

    static final long BRAKE_FAILURE_WINDOW_MILLIS = 3_000L;
    static final float MIN_BRAKE_FAILURE_SPEED_KPH = 30f;
    static final float MIN_EXPECTED_BRAKING_KPH = 1f;
    static final float MOVING_SPEED_KPH = 5f;
    static final long TIRE_WINDOW_MILLIS = 10_000L;
    /** A quarter of a tire's pressure gone within the window: a puncture, not slow leakage. */
    static final float MIN_TIRE_PRESSURE_DROP_RATIO = 0.25f;

    private final Deque<VehicleSnapshot> history = new ArrayDeque<>();
    private final Deque<VehicleSnapshot> cabinHistory = new ArrayDeque<>();
    private final Deque<VehicleSnapshot> tireHistory = new ArrayDeque<>();

    /**
     * Only a change from "not deployed" to "deployed" counts. A value that is already
     * "deployed" when the app starts says nothing about now: the Pleos emulator reports it that
     * way by default, and a real vehicle may keep it after an old, repaired accident.
     */
    @Nullable private Boolean lastAirbagDeployed;
    private boolean airbagAccidentActive;

    /** Adds a reading. Returns the most serious condition it shows, or null if none. */
    @Nullable
    public Detection evaluate(VehicleSnapshot snapshot) {
        remember(history, snapshot, HISTORY_MILLIS);
        if (snapshot.cabinTemperatureC != null) {
            remember(cabinHistory, snapshot, CABIN_WINDOW_MILLIS);
        }
        remember(tireHistory, snapshot, TIRE_WINDOW_MILLIS);

        boolean airbagJustDeployed = Boolean.FALSE.equals(lastAirbagDeployed)
                && Boolean.TRUE.equals(snapshot.airbagDeployed);
        if (snapshot.airbagDeployed != null) lastAirbagDeployed = snapshot.airbagDeployed;
        if (airbagJustDeployed || airbagAccidentActive) {
            airbagAccidentActive = Boolean.TRUE.equals(snapshot.airbagDeployed);
            if (airbagAccidentActive) {
                return new Detection(EmergencyType.ACCIDENT, R.string.reason_airbag);
            }
        }
        if (isSuddenStop(snapshot)) {
            return new Detection(EmergencyType.ACCIDENT, R.string.reason_sudden_stop);
        }
        if (isUnintendedAcceleration(snapshot)) {
            return new Detection(
                    EmergencyType.UNINTENDED_ACCELERATION, R.string.reason_acceleration);
        }
        if (isBrakeNotSlowing(snapshot)) {
            return new Detection(EmergencyType.BRAKE_FAILURE, R.string.reason_brake_no_effect);
        }
        if (snapshot.brakeFluidLow && isMoving(snapshot)) {
            return new Detection(EmergencyType.BRAKE_FAILURE, R.string.reason_brake_fluid);
        }
        if (hasSuddenTirePressureLoss(snapshot)) {
            return new Detection(EmergencyType.TIRE_FAILURE, R.string.reason_tire_pressure);
        }
        if (isCabinHeating(snapshot)) {
            return new Detection(EmergencyType.FIRE, R.string.reason_cabin_heat);
        }
        if (snapshot.overheatWarning) {
            return new Detection(EmergencyType.FIRE, R.string.reason_overheat);
        }
        return null;
    }

    /**
     * Whether a condition raised by detection has gone away. Accidents never clear on their own:
     * the vehicle stopping is part of a crash, not a sign that everyone is fine. Flooding is
     * never detected, so it never clears either.
     *
     * @param current what evaluate() returned for the same reading
     */
    public boolean isBackToNormal(
            EmergencyType type, VehicleSnapshot snapshot, @Nullable Detection current) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return current == null || current.type != EmergencyType.UNINTENDED_ACCELERATION;
            case FIRE:
                return !snapshot.overheatWarning
                        && (snapshot.cabinTemperatureC == null
                                || snapshot.cabinTemperatureC < MIN_FIRE_CABIN_C);
            case BRAKE_FAILURE:
                return !snapshot.brakeFluidLow
                        && (current == null || current.type != EmergencyType.BRAKE_FAILURE);
            case ACCIDENT:
            case FLOOD:
            case TIRE_FAILURE: // Lost pressure does not come back by itself.
            default:
                return false;
        }
    }

    public void reset() {
        history.clear();
        cabinHistory.clear();
        tireHistory.clear();
        lastAirbagDeployed = null;
        airbagAccidentActive = false;
    }

    private static void remember(Deque<VehicleSnapshot> readings, VehicleSnapshot latest, long keepMillis) {
        readings.addLast(latest);
        while (latest.timestampMillis - readings.peekFirst().timestampMillis > keepMillis) {
            readings.removeFirst();
        }
    }

    private boolean isSuddenStop(VehicleSnapshot current) {
        if (current.speedKph == null) return false;
        for (VehicleSnapshot earlier : history) {
            boolean inWindow =
                    current.timestampMillis - earlier.timestampMillis <= COLLISION_WINDOW_MILLIS;
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

    /** Brake held for 3 s at speed, yet the car is not slowing down. */
    private boolean isBrakeNotSlowing(VehicleSnapshot current) {
        if (current.speedKph == null || current.speedKph < MIN_BRAKE_FAILURE_SPEED_KPH) {
            return false;
        }
        VehicleSnapshot start = brakeHeldSince(current, BRAKE_FAILURE_WINDOW_MILLIS);
        return start != null
                && start.speedKph != null
                && start.speedKph - current.speedKph < MIN_EXPECTED_BRAKING_KPH;
    }

    private static boolean isMoving(VehicleSnapshot current) {
        return current.speedKph != null && current.speedKph >= MOVING_SPEED_KPH;
    }

    private boolean hasSuddenTirePressureLoss(VehicleSnapshot current) {
        for (int wheel = 0; wheel < 4; wheel++) {
            Float now = current.tirePressure(wheel);
            if (now == null) continue;
            for (VehicleSnapshot earlier : tireHistory) {
                Float before = earlier.tirePressure(wheel);
                if (before != null && before > 0
                        && (before - now) / before >= MIN_TIRE_PRESSURE_DROP_RATIO) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCabinHeating(VehicleSnapshot current) {
        if (current.cabinTemperatureC == null || current.cabinTemperatureC < MIN_FIRE_CABIN_C) {
            return false;
        }
        float lowest = current.cabinTemperatureC;
        for (VehicleSnapshot reading : cabinHistory) {
            lowest = Math.min(lowest, reading.cabinTemperatureC);
        }
        return current.cabinTemperatureC - lowest >= MIN_FIRE_CABIN_RISE_C;
    }

    /**
     * Returns the reading from the start of the window, or null unless the brake has been
     * pressed for the whole window and the history actually covers it.
     */
    @Nullable
    private VehicleSnapshot brakeHeldSince(VehicleSnapshot current, long windowMillis) {
        Iterator<VehicleSnapshot> readings = history.descendingIterator();
        while (readings.hasNext()) {
            VehicleSnapshot reading = readings.next();
            if (!Boolean.TRUE.equals(reading.brakePressed)) return null;
            if (current.timestampMillis - reading.timestampMillis >= windowMillis) return reading;
        }
        return null;
    }
}
