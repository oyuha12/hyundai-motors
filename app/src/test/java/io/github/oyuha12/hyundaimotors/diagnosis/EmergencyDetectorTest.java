package io.github.oyuha12.hyundaimotors.diagnosis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Warning;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

public class EmergencyDetectorTest {
    private static final long STEP_MILLIS = 250L;

    private final EmergencyDetector detector = new EmergencyDetector();
    private long now = 0L;

    private EmergencyType feed(Float speed, Boolean brake, Gear gear, Set<Warning> warnings) {
        EmergencyType result = detector.evaluate(new VehicleSnapshot(
                now, speed, brake, gear, warnings, null, null));
        now += STEP_MILLIS;
        return result;
    }

    private EmergencyType feed(Float speed, Boolean brake) {
        return feed(speed, brake, Gear.DRIVE, EnumSet.noneOf(Warning.class));
    }

    /** Feeds speed changing by perSecond for the given time and returns the last result. */
    @Nullable
    private EmergencyType ramp(float speed, float perSecond, boolean brake, long millis) {
        EmergencyType last = null;
        for (long t = 0; t <= millis; t += STEP_MILLIS) {
            last = feed(speed + perSecond * t / 1000f, brake);
        }
        return last;
    }

    @Test
    public void speedRisingWithBrakeHeld_isUnintendedAcceleration() {
        assertEquals(EmergencyType.UNINTENDED_ACCELERATION, ramp(40f, 8f, true, 2_000L));
    }

    @Test
    public void normalAcceleration_withoutBrake_isNotDetected() {
        assertNull(ramp(40f, 8f, false, 3_000L));
    }

    @Test
    public void normalBraking_isNotDetected() {
        assertNull(ramp(80f, -12f, true, 4_000L));
    }

    @Test
    public void briefBrakeTap_isNotDetected() {
        assertNull(ramp(40f, 8f, true, 1_000L));
    }

    @Test
    public void releasingBrake_interruptsTheWindow() {
        ramp(40f, 8f, true, 1_000L);
        assertNull(feed(50f, false));
        assertNull(feed(51f, true));
    }

    @Test
    public void lowSpeedCreep_isNotDetected() {
        assertNull(ramp(1f, 2f, true, 3_000L));
    }

    @Test
    public void parkedOrReversing_isNotUnintendedAcceleration() {
        for (long t = 0; t <= 2_000L; t += STEP_MILLIS) {
            assertNull(feed(20f + t / 100f, true, Gear.REVERSE, EnumSet.noneOf(Warning.class)));
        }
    }

    @Test
    public void speedHeldWithBrakeHeld_isBrakeFailure() {
        assertEquals(EmergencyType.BRAKE_FAILURE, ramp(50f, 0f, true, 3_250L));
    }

    @Test
    public void suddenSpeedDrop_isCollisionSuspected() {
        feed(60f, false);
        feed(60f, false);
        feed(45f, false);
        assertEquals(EmergencyType.COLLISION_SUSPECTED, feed(20f, false));
    }

    @Test
    public void hardBraking_isNotCollision() {
        // About 0.9 g, roughly the limit of emergency braking on dry road.
        assertNull(ramp(80f, -32f, true, 2_000L));
    }

    @Test
    public void missingSpeed_doesNotTrigger() {
        for (int i = 0; i < 20; i++) {
            assertNull(feed(null, true));
        }
    }

    @Test
    public void warningLights_mapToFaults() {
        assertEquals(
                EmergencyType.OVERHEAT,
                feed(0f, false, Gear.PARK, EnumSet.of(Warning.OVERHEAT)));
        assertEquals(
                EmergencyType.POWERTRAIN_FAULT,
                feed(0f, false, Gear.PARK, EnumSet.of(Warning.POWERTRAIN)));
        assertEquals(
                EmergencyType.SAFETY_SYSTEM_FAULT,
                feed(0f, false, Gear.PARK, EnumSet.of(Warning.SAFETY_SYSTEM)));
    }

    @Test
    public void criticalCondition_outranksWarningLight() {
        EmergencyType last = null;
        for (long t = 0; t <= 2_000L; t += STEP_MILLIS) {
            last = feed(40f + 8f * t / 1000f, true, Gear.DRIVE, EnumSet.of(Warning.OVERHEAT));
        }
        assertEquals(EmergencyType.UNINTENDED_ACCELERATION, last);
    }
}
