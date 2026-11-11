package io.github.oyuha12.hyundaimotors.telemetry;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;

/**
 * Replays an emergency for demos. An emulator cannot reproduce these conditions safely on its
 * own, and they must never be provoked in a real vehicle.
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
        CURVE_OVERSPEED
    }

    private static final long TICK_MILLIS = 250L;
    private static final double SAMPLE_LATITUDE = 37.48544722;
    private static final double SAMPLE_LONGITUDE = 127.03636666;

    private final Scenario scenario;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private long startedAtMillis;
    private long tickCount;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            // Scenario time advances per tick, so a slow device cannot change the outcome.
            long elapsed = tickCount++ * TICK_MILLIS;
            listener.onSnapshot(snapshotAt(scenario, elapsed, startedAtMillis + elapsed));
            handler.postDelayed(this, TICK_MILLIS);
        }
    };

    public SimulatedTelemetrySource(Scenario scenario) {
        this.scenario = scenario;
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

    static VehicleSnapshot snapshotAt(Scenario scenario, long elapsedMillis, long timestampMillis) {
        float seconds = elapsedMillis / 1000f;
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
        }
        return new VehicleSnapshot(
                timestampMillis,
                speed,
                brake,
                gear != null ? gear : speed > 0f ? Gear.DRIVE : Gear.PARK,
                airbag,
                cabin,
                false,
                SAMPLE_LATITUDE,
                SAMPLE_LONGITUDE,
                // A steady heading with a little natural wobble.
                45f + (float) Math.sin(seconds) * 3f)
                .withChassis(false, new float[] {frontLeftTire, 230f, 230f, 230f}, steering);
    }
}
