package io.github.oyuha12.hyundaimotors.drowsiness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.drowsiness.DrivingWarningMonitor.Warning;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import org.junit.Test;

public class DrivingWarningMonitorTest {
    private static final long STEP = 250L;

    private final DrivingWarningMonitor monitor = new DrivingWarningMonitor();
    private long now = 0L;

    @Nullable
    private Warning feed(float speed, Gear gear, boolean brake, @Nullable Float steering) {
        Warning warning = monitor.update(new VehicleSnapshot(
                now, speed, brake, gear, false, 22f, false, null, null)
                .withChassis(false, null, steering));
        now += STEP;
        return warning;
    }

    @Test
    public void hardLaunchFromStandstill_isPedalMixUp() {
        feed(0f, Gear.DRIVE, false, null);
        feed(10f, Gear.DRIVE, false, null);
        feed(20f, Gear.DRIVE, false, null);
        assertEquals(Warning.PEDAL_MISAPPLICATION, feed(28f, Gear.DRIVE, false, null));
    }

    @Test
    public void normalStart_isFine() {
        // 0 to 30 km/h over 6 s: an ordinary start.
        for (int i = 0; i <= 24; i++) {
            assertNull(feed(i * 1.25f, Gear.DRIVE, false, null));
        }
    }

    @Test
    public void fastReverse_warns() {
        assertNull(feed(8f, Gear.REVERSE, false, null));
        assertEquals(Warning.FAST_REVERSE, feed(16f, Gear.REVERSE, false, null));
    }

    @Test
    public void hardTurnAtSpeed_isCurveOverspeed() {
        assertNull(feed(80f, Gear.DRIVE, false, 10f));
        assertEquals(Warning.CURVE_OVERSPEED, feed(80f, Gear.DRIVE, false, 150f));
    }

    @Test
    public void gentleCurveOrSlowTurn_isFine() {
        // About 1.9 m/s², a typical expressway curve.
        assertNull(feed(100f, Gear.DRIVE, false, 6f));
        assertNull(feed(30f, Gear.DRIVE, false, 360f));
    }

    @Test
    public void lateralAcceleration_matchesTheBicycleModel() {
        VehicleSnapshot reading = new VehicleSnapshot(0L, 72f, false, Gear.DRIVE, false, 22f,
                false, null, null).withChassis(false, null, 90f);
        // 20 m/s, 6 degrees at the road wheel, 2.9 m wheelbase: 400 x tan(6°) / 2.9 ≈ 14.5.
        assertEquals(14.5, DrivingWarningMonitor.lateralAcceleration(reading), 0.1);
    }

    @Test
    public void sameWarning_doesNotRepeatWithinTheCooldown() {
        assertEquals(Warning.FAST_REVERSE, feed(20f, Gear.REVERSE, false, null));
        for (int i = 0; i < 40; i++) {
            assertNull(feed(20f, Gear.REVERSE, false, null));
        }
        now += DrivingWarningMonitor.COOLDOWN_MILLIS;
        assertTrue(feed(20f, Gear.REVERSE, false, null) == Warning.FAST_REVERSE);
    }
}
