package io.github.oyuha12.hyundaimotors.drowsiness;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;

/**
 * Spots driving patterns that make drowsiness likely and asks for a gentle reminder. This is a
 * side feature: it never raises an emergency and never takes over the screen.
 *
 * <ul>
 *   <li>Monotonous driving: steady speed in nearly the same direction for a long time.
 *   <li>Long driving: hours behind the wheel without a real break.
 * </ul>
 *
 * Thresholds are initial values for the demo, not validated on real drivers.
 */
public final class DrowsinessMonitor {
    public enum Alert { MONOTONOUS_DRIVING, LONG_DRIVING }

    public static final class Config {
        final float minCruiseSpeedKph;
        final float maxHeadingChangeDegrees;
        final long monotonousMillis;
        final long longDrivingMillis;
        final long breakResetMillis;
        final long cooldownMillis;

        public Config(
                float minCruiseSpeedKph,
                float maxHeadingChangeDegrees,
                long monotonousMillis,
                long longDrivingMillis,
                long breakResetMillis,
                long cooldownMillis) {
            this.minCruiseSpeedKph = minCruiseSpeedKph;
            this.maxHeadingChangeDegrees = maxHeadingChangeDegrees;
            this.monotonousMillis = monotonousMillis;
            this.longDrivingMillis = longDrivingMillis;
            this.breakResetMillis = breakResetMillis;
            this.cooldownMillis = cooldownMillis;
        }
    }

    private static final long MINUTE = 60_000L;

    /** 15 min of steady straight driving; a rest reminder every 2 h; a 10 min stop counts as a break. */
    public static final Config DEFAULT =
            new Config(60f, 10f, 15 * MINUTE, 120 * MINUTE, 10 * MINUTE, 15 * MINUTE);

    /** Same rules on a short clock, so the demo finishes in seconds. */
    public static final Config DEMO = new Config(60f, 10f, 20_000L, 120 * MINUTE, 10 * MINUTE, 30_000L);

    static final float MOVING_SPEED_KPH = 5f;

    private Config config = DEFAULT;
    private long drivingSince = -1L;
    private long stoppedSince = -1L;
    private long steadySince = -1L;
    private float referenceHeading;
    private long lastAlertAt = -1L;

    public void setConfig(Config config) {
        this.config = config;
        reset();
    }

    public void reset() {
        drivingSince = -1L;
        stoppedSince = -1L;
        steadySince = -1L;
        lastAlertAt = -1L;
    }

    /** Adds a reading. Returns a reminder to give now, or null. */
    @Nullable
    public Alert update(VehicleSnapshot reading) {
        if (reading.speedKph == null) return null;
        long now = reading.timestampMillis;

        if (reading.speedKph < MOVING_SPEED_KPH) {
            if (stoppedSince < 0) stoppedSince = now;
            if (now - stoppedSince >= config.breakResetMillis) drivingSince = -1L;
            steadySince = -1L;
            return null;
        }
        stoppedSince = -1L;
        if (drivingSince < 0) drivingSince = now;
        trackSteadyDirection(reading, now);

        if (lastAlertAt >= 0 && now - lastAlertAt < config.cooldownMillis) return null;

        if (steadySince >= 0 && now - steadySince >= config.monotonousMillis) {
            lastAlertAt = now;
            steadySince = now;
            return Alert.MONOTONOUS_DRIVING;
        }
        if (now - drivingSince >= config.longDrivingMillis) {
            lastAlertAt = now;
            drivingSince = now;
            return Alert.LONG_DRIVING;
        }
        return null;
    }

    private void trackSteadyDirection(VehicleSnapshot reading, long now) {
        boolean cruising = reading.speedKph >= config.minCruiseSpeedKph && reading.headingDegrees != null;
        if (!cruising) {
            steadySince = -1L;
            return;
        }
        float heading = reading.headingDegrees;
        if (steadySince < 0 || angleBetween(heading, referenceHeading) > config.maxHeadingChangeDegrees) {
            steadySince = now;
            referenceHeading = heading;
        }
    }

    /** Smallest angle between two compass headings, 0 to 180. */
    static float angleBetween(float a, float b) {
        float diff = Math.abs(a - b) % 360f;
        return diff > 180f ? 360f - diff : diff;
    }
}
