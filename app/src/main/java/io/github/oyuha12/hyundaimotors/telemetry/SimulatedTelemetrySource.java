package io.github.oyuha12.hyundaimotors.telemetry;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.demo.VideoConfig;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;

/**
 * Replays an emergency for demos. An emulator cannot reproduce these conditions safely on its
 * own, and they must never be provoked in a real vehicle.
 *
 * <p>Only vehicle values are simulated: speed, brake, gear, temperature, airbag, tires and
 * steering. The position comes from {@link PositionSource}: in video mode, where the location
 * SDK last put the car; otherwise the sample position, as before.
 */
public final class SimulatedTelemetrySource implements TelemetrySource {
    public enum Scenario {
        UNINTENDED_ACCELERATION,
        COLLISION,
        AIRBAG_DEPLOYED,
        CABIN_FIRE,
        BRAKE_FAILURE,
        TIRE_PRESSURE_LOSS,
        /** Steady speed in one direction, for the drowsiness reminder. */
        MONOTONOUS_DRIVE,
        // Driver mistake warnings: voice only, never an emergency screen.
        PEDAL_MISAPPLICATION,
        FAST_REVERSE,
        CURVE_OVERSPEED,
        // For the demo video. Each pulls over once the guidance asks the driver to stop
        // (startStopping); without that request it keeps going.
        /** 40 km/h, then the brake is held while the car speeds up to about 90 km/h. */
        VIDEO_ACCELERATION,
        /** 60 km/h, then the cabin heats up fast and stays hot. */
        VIDEO_FIRE
    }

    /**
     * Where the car is: in video mode the location SDK's last fix. Null before the first fix and
     * outside video mode, which use {@link VideoConfig#FALLBACK_POSITION}.
     */
    public interface PositionSource {
        @Nullable
        GeoPoint lastPosition();
    }

    private static final long TICK_MILLIS = 250L;
    /** No stop requested yet. */
    static final long NOT_STOPPING = -1L;
    /** From the guidance asking the driver to stop until the car starts slowing down. */
    static final long STOP_DELAY_MILLIS = 3_000L;
    /** Slowing from top speed to 0 with the parking brake held. */
    static final long ACCELERATION_STOP_MILLIS = 7_000L;
    /** Slowing from 60 km/h to 0 on the shoulder. */
    static final long FIRE_STOP_MILLIS = 6_000L;
    static final float ACCELERATION_TOP_KPH = 90f;
    static final float FIRE_CABIN_PEAK_C = 80f;

    private final Scenario scenario;
    private final long leadInMillis;
    private final PositionSource positions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private long startedAtMillis;
    private long tickCount;
    /** Scenario time from which the car pulls over, or NOT_STOPPING. */
    private long stopRequestedAtMillis = NOT_STOPPING;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            // Scenario time advances per tick, so a slow device cannot change the outcome.
            long elapsed = tickCount++ * TICK_MILLIS;
            listener.onSnapshot(snapshotAt(scenario, elapsed, leadInMillis,
                    startedAtMillis + elapsed, positions.lastPosition(), stopRequestedAtMillis));
            handler.postDelayed(this, TICK_MILLIS);
        }
    };

    /**
     * @param leadInMillis normal driving before the scenario begins, to line up with other
     *     footage; 0 starts it at once
     * @param positions where the car is, read on every reading
     */
    public SimulatedTelemetrySource(Scenario scenario, long leadInMillis, PositionSource positions) {
        this.scenario = scenario;
        this.leadInMillis = Math.max(0L, leadInMillis);
        this.positions = positions;
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        startedAtMillis = SystemClock.elapsedRealtime();
        handler.post(tick);
    }

    @Override
    public void stop() {
        handler.removeCallbacks(tick);
    }

    /**
     * The guidance has asked the driver to stop: a video scenario pulls over from the next
     * reading on. Other scenarios ignore it. Only the first request counts, so reading the
     * steps again does not restart the stop.
     */
    public void startStopping() {
        if (stopRequestedAtMillis == NOT_STOPPING) stopRequestedAtMillis = tickCount * TICK_MILLIS;
    }

    static VehicleSnapshot snapshotAt(Scenario scenario, long elapsedMillis, long timestampMillis) {
        return snapshotAt(scenario, elapsedMillis, 0L, timestampMillis, null);
    }

    static VehicleSnapshot snapshotAt(
            Scenario scenario,
            long elapsedMillis,
            long leadInMillis,
            long timestampMillis,
            @Nullable GeoPoint position) {
        return snapshotAt(
                scenario, elapsedMillis, leadInMillis, timestampMillis, position, NOT_STOPPING);
    }

    /**
     * One reading. During the lead-in the scenario's first reading is held, so nothing unusual
     * happens; after it the scenario plays exactly as it would without a lead-in.
     *
     * @param position where the car is; null uses {@link VideoConfig#FALLBACK_POSITION}
     * @param stopRequestedAtMillis scenario time from which a video scenario pulls over, or
     *     {@link #NOT_STOPPING}
     */
    static VehicleSnapshot snapshotAt(
            Scenario scenario,
            long elapsedMillis,
            long leadInMillis,
            long timestampMillis,
            @Nullable GeoPoint position,
            long stopRequestedAtMillis) {
        float seconds = scenarioSeconds(elapsedMillis, leadInMillis);
        boolean stopping =
                stopRequestedAtMillis != NOT_STOPPING && elapsedMillis >= stopRequestedAtMillis;
        float speed = 50f;
        boolean brake = false;
        boolean airbag = false;
        float cabin = 24f;
        Gear gear = null;
        float frontLeftTire = 230f; // kPa
        Float steering = 0f;

        switch (scenario) {
            case BRAKE_FAILURE:
                brake = seconds >= 1f;
                speed = 60f;
                break;
            case TIRE_PRESSURE_LOSS:
                speed = 80f;
                frontLeftTire = seconds < 2f ? 230f : Math.max(0f, 230f - (seconds - 2f) * 60f);
                break;
            case PEDAL_MISAPPLICATION:
                speed = seconds < 1f ? 0f : Math.min(40f, (seconds - 1f) * 25f);
                gear = Gear.DRIVE;
                break;
            case FAST_REVERSE:
                speed = Math.min(20f, seconds * 10f);
                gear = Gear.REVERSE;
                break;
            case CURVE_OVERSPEED:
                speed = 80f;
                steering = seconds < 1f ? 0f : 150f;
                break;
            case UNINTENDED_ACCELERATION:
                brake = seconds >= 1f;
                speed = seconds < 1f ? 40f : 40f + (seconds - 1f) * 8f;
                break;
            case COLLISION:
                speed = seconds < 1f ? 60f : Math.max(0f, 60f - (seconds - 1f) * 60f);
                break;
            case AIRBAG_DEPLOYED:
                airbag = seconds >= 1f;
                speed = seconds < 1f ? 30f : 0f;
                break;
            case CABIN_FIRE:
                speed = 0f;
                cabin = seconds < 1f ? 24f : 24f + (seconds - 1f) * 5f;
                break;
            case MONOTONOUS_DRIVE:
                speed = 90f;
                break;
            case VIDEO_ACCELERATION:
                speed = videoAccelerationSpeed(seconds);
                brake = seconds >= 1f;
                if (stopping) {
                    // Neutral, parking brake held: coasts briefly, slows to 0, then park.
                    float atRequest = videoAccelerationSpeed(
                            scenarioSeconds(stopRequestedAtMillis, leadInMillis));
                    speed = stoppingSpeed(atRequest, elapsedMillis, stopRequestedAtMillis,
                            ACCELERATION_STOP_MILLIS);
                    brake = true;
                    gear = speed > 0f ? Gear.NEUTRAL : Gear.PARK;
                }
                break;
            case VIDEO_FIRE:
                speed = 60f;
                cabin = seconds < 1f
                        ? 24f
                        : Math.min(FIRE_CABIN_PEAK_C, 24f + (seconds - 1f) * 6f);
                if (stopping) {
                    speed = stoppingSpeed(60f, elapsedMillis, stopRequestedAtMillis, FIRE_STOP_MILLIS);
                    brake = speed > 0f;
                }
                break;
        }
        GeoPoint at = position != null ? position : VideoConfig.FALLBACK_POSITION;
        float wobbleSeconds = elapsedMillis / 1000f;
        return new VehicleSnapshot(
                timestampMillis,
                speed,
                brake,
                gear != null ? gear : speed > 0f ? Gear.DRIVE : Gear.PARK,
                airbag,
                cabin,
                false,
                at.latitude,
                at.longitude,
                // A steady heading with a little natural wobble.
                45f + (float) Math.sin(wobbleSeconds) * 3f)
                .withChassis(false, new float[] {frontLeftTire, 230f, 230f, 230f}, steering);
    }

    /** Seconds since the lead-in ended. */
    private static float scenarioSeconds(long elapsedMillis, long leadInMillis) {
        return Math.max(0L, elapsedMillis - leadInMillis) / 1000f;
    }

    private static float videoAccelerationSpeed(float seconds) {
        return seconds < 1f ? 40f : Math.min(ACCELERATION_TOP_KPH, 40f + (seconds - 1f) * 10f);
    }

    /**
     * Pulling over: the speed at the request holds for {@link #STOP_DELAY_MILLIS}, then falls
     * evenly to 0 over stopMillis.
     */
    private static float stoppingSpeed(
            float speedAtRequest, long elapsedMillis, long stopRequestedAtMillis, long stopMillis) {
        long slowingFor = elapsedMillis - stopRequestedAtMillis - STOP_DELAY_MILLIS;
        if (slowingFor <= 0) return speedAtRequest;
        return Math.max(0f, speedAtRequest * (1f - slowingFor / (float) stopMillis));
    }
}
