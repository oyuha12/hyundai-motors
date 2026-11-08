package io.github.oyuha12.hyundaimotors.diagnosis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import org.junit.Test;

public class EmergencyDetectorTest {
    private static final long STEP_MILLIS = 250L;

    private final EmergencyDetector detector = new EmergencyDetector();
    private long now = 0L;

    @Nullable
    private EmergencyType feed(
            Float speed, Boolean brake, Gear gear, Boolean airbag, Float cabin, boolean overheat) {
        Detection result = detector.evaluate(new VehicleSnapshot(
                now, speed, brake, gear, airbag, cabin, overheat, null, null));
        now += STEP_MILLIS;
        return result == null ? null : result.type;
    }

    @Nullable
    private EmergencyType feed(Float speed, Boolean brake) {
        return feed(speed, brake, Gear.DRIVE, false, 22f, false);
    }

    @Nullable
    private EmergencyType feedCabin(float celsius) {
        return feed(0f, false, Gear.PARK, false, celsius, false);
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
    public void reversing_isNotUnintendedAcceleration() {
        for (long t = 0; t <= 2_000L; t += STEP_MILLIS) {
            assertNull(feed(20f + t / 100f, true, Gear.REVERSE, false, 22f, false));
        }
    }

    @Test
    public void suddenSpeedDrop_isAccident() {
        feed(60f, false);
        feed(60f, false);
        feed(45f, false);
        assertEquals(EmergencyType.ACCIDENT, feed(20f, false));
    }

    @Test
    public void hardBraking_isNotAccident() {
        // About 0.9 g, roughly the limit of emergency braking on dry road.
        assertNull(ramp(80f, -32f, true, 2_000L));
    }

    @Test
    public void airbagDeployingDuringTheTrip_isAccident() {
        assertNull(feed(30f, false, Gear.DRIVE, false, 22f, false));
        assertEquals(EmergencyType.ACCIDENT, feed(0f, false, Gear.PARK, true, 22f, false));
        assertEquals(EmergencyType.ACCIDENT, feed(0f, false, Gear.PARK, true, 22f, false));
    }

    @Test
    public void airbagAlreadyDeployedAtStart_isNotAccident() {
        for (int i = 0; i < 10; i++) {
            assertNull(feed(0f, false, Gear.PARK, true, 22f, false));
        }
    }

    @Test
    public void cabinHeatingFast_isFire() {
        EmergencyType last = null;
        for (int i = 0; i <= 40; i++) {
            last = feedCabin(25f + i);
        }
        assertEquals(EmergencyType.FIRE, last);
    }

    @Test
    public void hotButSteadyCabin_isNotFire() {
        // A car parked in summer sun can stay this hot without any fire.
        for (int i = 0; i < 400; i++) {
            assertNull(feedCabin(65f));
        }
    }

    @Test
    public void slowWarming_isNotFire() {
        // 0.1 degree per second: 6 degrees a minute stays under the rise threshold.
        for (int i = 0; i < 1_200; i++) {
            assertNull(feedCabin(45f + i * 0.025f));
        }
    }

    @Test
    public void overheatWarning_isFireRisk() {
        assertEquals(EmergencyType.FIRE, feed(0f, false, Gear.PARK, false, 22f, true));
    }

    @Test
    public void missingData_doesNotTrigger() {
        for (int i = 0; i < 20; i++) {
            assertNull(feed(null, true, Gear.UNKNOWN, null, null, false));
        }
    }

    private static VehicleSnapshot reading(Float speed, Float cabin, boolean overheat) {
        return new VehicleSnapshot(0L, speed, false, Gear.DRIVE, false, cabin, overheat, null, null);
    }

    @Test
    public void fire_isNormalOnceCabinCoolsAndWarningClears() {
        assertEquals(false, detector.isBackToNormal(EmergencyType.FIRE, reading(0f, 70f, false), null));
        assertEquals(false, detector.isBackToNormal(EmergencyType.FIRE, reading(0f, 30f, true), null));
        assertEquals(true, detector.isBackToNormal(EmergencyType.FIRE, reading(0f, 30f, false), null));
    }

    @Test
    public void unintendedAcceleration_isNormalWhenThePatternStops() {
        assertEquals(true, detector.isBackToNormal(
                EmergencyType.UNINTENDED_ACCELERATION, reading(0f, 22f, false), null));
        assertEquals(false, detector.isBackToNormal(
                EmergencyType.UNINTENDED_ACCELERATION, reading(60f, 22f, false),
                new Detection(EmergencyType.UNINTENDED_ACCELERATION, 0)));
    }

    @Test
    public void accidentAndFlood_neverClearOnTheirOwn() {
        assertEquals(false, detector.isBackToNormal(EmergencyType.ACCIDENT, reading(0f, 22f, false), null));
        assertEquals(false, detector.isBackToNormal(EmergencyType.FLOOD, reading(0f, 22f, false), null));
    }

    @Test
    public void accident_outranksOtherConditions() {
        feed(0f, false, Gear.PARK, false, 22f, true);
        assertEquals(EmergencyType.ACCIDENT, feed(0f, false, Gear.PARK, true, 22f, true));
    }
}
