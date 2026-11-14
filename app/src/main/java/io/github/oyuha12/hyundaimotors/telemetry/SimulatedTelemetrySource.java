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
        VIDEO_FIRE,
        // Home screen demo, outside video mode. Each starts from the speed the car had
        // (baseSpeedKph) and pulls over once the guidance asks the driver to stop.
        /** "주행으로 전환": eases to about 60 km/h and keeps changing a little, like real driving. */
        DEMO_CRUISE,
        /** The 급발진 button: the speed climbs steeply, with no brake, before the question. */
        DEMO_ACCELERATION,
        /** The 화재 button: the cabin heats up fast before the question; the speed carries on. */
        DEMO_FIRE
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
    /** Home demo: cruising speed, and how fast the car eases to it. */
    static final float DEMO_CRUISE_KPH = 60f;
    /** Slow enough that leaving a standstill never looks like a pedal mistake (25 km/h in 1.5 s). */
    static final float DEMO_RAMP_KPH_PER_SECOND = 10f;
    /** Home demo 급발진: the climb, and its top (at least this, or 50 km/h above the start). */
    static final float DEMO_ACCELERATION_KPH_PER_SECOND = 25f;
    static final float DEMO_ACCELERATION_MIN_TOP_KPH = 100f;
    static final float DEMO_ACCELERATION_GAIN_KPH = 50f;
    /** Home demo 화재: the cabin climbs from a normal temperature to a peak. */
    static final float DEMO_CABIN_START_C = 24f;
    static final float DEMO_CABIN_C_PER_SECOND = 15f;
    static final float DEMO_CABIN_PEAK_C = 85f;

    private final Scenario scenario;
    private final long leadInMillis;
    private final PositionSource positions;
    /** The speed when a home demo scenario started; 0 for the others. */
    private final float baseSpeedKph;
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
                    startedAtMillis + elapsed, positions.lastPosition(), stopRequestedAtMillis,
                    baseSpeedKph));
            handler.postDelayed(this, TICK_MILLIS);
        }
    };

    /**
     * @param leadInMillis normal driving before the scenario begins, to line up with other
     *     footage; 0 starts it at once
     * @param positions where the car is, read on every reading
     */
    public SimulatedTelemetrySource(Scenario scenario, long leadInMillis, PositionSource positions) {
        this(scenario, leadInMillis, positions, 0f);
    }

    /** @param baseSpeedKph the speed the car had, where a home demo scenario starts from */
    public SimulatedTelemetrySource(
            Scenario scenario, long leadInMillis, PositionSource positions, float baseSpeedKph) {
        this.scenario = scenario;
        this.leadInMillis = Math.max(0L, leadInMillis);
        this.positions = positions;
        this.baseSpeedKph = Math.max(0f, baseSpeedKph);
    }

    public Scenario scenario() {
        return scenario;
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
     * The guidance has asked the driver to stop: a video or home demo scenario pulls over from
     * the next reading on. Other scenarios ignore it. Only the first request counts, so reading the
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

    static VehicleSnapshot snapshotAt(
            Scenario scenario,
            long elapsedMillis,
            long leadInMillis,
            long timestampMillis,
            @Nullable GeoPoint position,
            long stopRequestedAtMillis) {
        return snapshotAt(scenario, elapsedMillis, leadInMillis, timestampMillis, position,
                stopRequestedAtMillis, 0f);
    }

    /**
     * One reading. During the lead-in the scenario's first reading is held, so nothing unusual
     * happens; after it the scenario plays exactly as it would without a lead-in.
     *
     * @param position where the car is; null uses {@link VideoConfig#FALLBACK_POSITION}
     * @param stopRequestedAtMillis scenario time from which a video or home demo scenario pulls
     *     over, or {@link #NOT_STOPPING}
     * @param baseSpeedKph where a home demo scenario starts from
     */
    static VehicleSnapshot snapshotAt(
            Scenario scenario,
            long elapsedMillis,
            long leadInMillis,
            long timestampMillis,
            @Nullable GeoPoint position,
            long stopRequestedAtMillis,
            float baseSpeedKph) {
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
            case DEMO_CRUISE:
                speed = demoCruiseSpeed(baseSpeedKph, seconds);
                if (stopping) {
                    float atRequest = demoCruiseSpeed(baseSpeedKph,
                            scenarioSeconds(stopRequestedAtMillis, leadInMillis));
                    speed = stoppingSpeed(
                            atRequest, elapsedMillis, stopRequestedAtMillis, FIRE_STOP_MILLIS);
                    brake = speed > 0f;
                }
                break;
            case DEMO_ACCELERATION:
                speed = demoAccelerationSpeed(baseSpeedKph, seconds);
                if (stopping) {
                    // As in the video: neutral, parking brake held, then park.
                    float atRequest = demoAccelerationSpeed(baseSpeedKph,
                            scenarioSeconds(stopRequestedAtMillis, leadInMillis));
                    speed = stoppingSpeed(atRequest, elapsedMillis, stopRequestedAtMillis,
                            ACCELERATION_STOP_MILLIS);
                    brake = true;
                    gear = speed > 0f ? Gear.NEUTRAL : Gear.PARK;
                }
                break;
            case DEMO_FIRE:
                speed = demoFireSpeed(baseSpeedKph, seconds);
                cabin = demoFireCabin(seconds);
                if (stopping) {
                    float atRequest = demoFireSpeed(baseSpeedKph,
                            scenarioSeconds(stopRequestedAtMillis, leadInMillis));
                    speed = stoppingSpeed(
                            atRequest, elapsedMillis, stopRequestedAtMillis, FIRE_STOP_MILLIS);
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

    /**
     * A few slow waves of different lengths, within about ±4 km/h: the speed never repeats
     * exactly, like a driver keeping pace with traffic.
     */
    static float drift(float seconds) {
        return (float) (2.5 * Math.sin(0.45 * seconds)
                + 1.2 * Math.sin(1.3 * seconds + 1.0)
                + 0.5 * Math.sin(2.9 * seconds + 2.0));
    }

    /** Eases from the start speed to {@link #DEMO_CRUISE_KPH}, the drift growing with it. */
    static float demoCruiseSpeed(float baseSpeedKph, float seconds) {
        float rampSeconds = Math.abs(DEMO_CRUISE_KPH - baseSpeedKph) / DEMO_RAMP_KPH_PER_SECOND;
        float progress = rampSeconds <= 0f ? 1f : Math.min(1f, seconds / rampSeconds);
        float level = baseSpeedKph + (DEMO_CRUISE_KPH - baseSpeedKph) * progress;
        return Math.max(0f, level + drift(seconds) * progress);
    }

    /** Climbs steeply from the start speed to a top well above it. */
    static float demoAccelerationSpeed(float baseSpeedKph, float seconds) {
        float top = Math.max(
                DEMO_ACCELERATION_MIN_TOP_KPH, baseSpeedKph + DEMO_ACCELERATION_GAIN_KPH);
        return Math.min(top, baseSpeedKph + DEMO_ACCELERATION_KPH_PER_SECOND * seconds);
    }

    /** The car keeps its pace while the cabin heats up; a parked car stays parked. */
    static float demoFireSpeed(float baseSpeedKph, float seconds) {
        if (baseSpeedKph <= 0f) return 0f;
        return Math.max(0f, baseSpeedKph + drift(seconds) - drift(0f));
    }

    static float demoFireCabin(float seconds) {
        return Math.min(DEMO_CABIN_PEAK_C, DEMO_CABIN_START_C + DEMO_CABIN_C_PER_SECOND * seconds);
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
