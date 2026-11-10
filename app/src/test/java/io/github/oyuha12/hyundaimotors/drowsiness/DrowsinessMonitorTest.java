package io.github.oyuha12.hyundaimotors.drowsiness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.github.oyuha12.hyundaimotors.drowsiness.DrowsinessMonitor.Alert;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import org.junit.Test;

public class DrowsinessMonitorTest {
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;

    private final DrowsinessMonitor monitor = new DrowsinessMonitor();
    private long now = 0L;

    private Alert drive(float speed, Float heading, long millis) {
        Alert first = null;
        for (long t = 0; t < millis; t += SECOND) {
            Alert alert = monitor.update(new VehicleSnapshot(
                    now, speed, false, Gear.DRIVE, false, 22f, false, null, null, heading));
            if (first == null) first = alert;
            now += SECOND;
        }
        return first;
    }

    @Test
    public void steadyStraightCruise_remindsAfter15Minutes() {
        assertNull(drive(90f, 45f, 14 * MINUTE));
        assertEquals(Alert.MONOTONOUS_DRIVING, drive(90f, 45f, 2 * MINUTE));
    }

    @Test
    public void smallWobble_stillCountsAsTheSameDirection() {
        Alert first = null;
        for (long t = 0; t < 16 * MINUTE; t += SECOND) {
            float heading = 45f + (float) Math.sin(t / 1000.0) * 5f;
            Alert alert = monitor.update(new VehicleSnapshot(
                    now, 90f, false, Gear.DRIVE, false, 22f, false, null, null, heading));
            if (first == null) first = alert;
            now += SECOND;
        }
        assertEquals(Alert.MONOTONOUS_DRIVING, first);
    }

    @Test
    public void turning_restartsTheCount() {
        assertNull(drive(90f, 45f, 10 * MINUTE));
        assertNull(drive(90f, 120f, 10 * MINUTE));
    }

    @Test
    public void headingWrapsAroundNorth() {
        assertEquals(10f, DrowsinessMonitor.angleBetween(355f, 5f), 0.01f);
        assertNull(drive(90f, 358f, 8 * MINUTE));
        assertEquals(Alert.MONOTONOUS_DRIVING, drive(90f, 3f, 8 * MINUTE));
    }

    @Test
    public void slowCityDriving_isNotMonotonous() {
        assertNull(drive(40f, 45f, 30 * MINUTE));
    }

    @Test
    public void unknownHeading_isNotMonotonous() {
        assertNull(drive(90f, null, 30 * MINUTE));
    }

    @Test
    public void twoHoursOfDriving_remindsToRest() {
        // City speed, so only the long-driving rule can fire.
        assertNull(drive(40f, null, 119 * MINUTE));
        assertEquals(Alert.LONG_DRIVING, drive(40f, null, 2 * MINUTE));
    }

    @Test
    public void tenMinuteBreak_resetsDrivingTime() {
        assertNull(drive(40f, null, 100 * MINUTE));
        assertNull(drive(0f, null, 11 * MINUTE));
        assertNull(drive(40f, null, 100 * MINUTE));
    }

    @Test
    public void reminders_doNotRepeatWithinTheCooldown() {
        assertEquals(Alert.MONOTONOUS_DRIVING, drive(90f, 45f, 16 * MINUTE));
        assertNull(drive(90f, 45f, 14 * MINUTE));
    }

    @Test
    public void demoConfig_remindsWithinSeconds() {
        monitor.setConfig(DrowsinessMonitor.DEMO);
        assertEquals(Alert.MONOTONOUS_DRIVING, drive(90f, 45f, 25 * SECOND));
    }
}
