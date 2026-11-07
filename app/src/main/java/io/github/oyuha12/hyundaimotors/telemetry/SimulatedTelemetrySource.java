package io.github.oyuha12.hyundaimotors.telemetry;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Warning;
import java.util.EnumSet;

/**
 * Replays an emergency for demos. An emulator cannot reproduce these conditions safely on its
 * own, and they must never be provoked in a real vehicle.
 */
public final class SimulatedTelemetrySource implements TelemetrySource {
    public enum Scenario {
        UNINTENDED_ACCELERATION,
        BRAKE_FAILURE,
        COLLISION,
        OVERHEAT,
        POWERTRAIN_FAULT
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
        EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);

        switch (scenario) {
            case UNINTENDED_ACCELERATION:
                brake = seconds >= 1f;
                speed = seconds < 1f ? 40f : 40f + (seconds - 1f) * 8f;
                break;
            case BRAKE_FAILURE:
                brake = seconds >= 1f;
                break;
            case COLLISION:
                speed = seconds < 1f ? 60f : Math.max(0f, 60f - (seconds - 1f) * 60f);
                break;
            case OVERHEAT:
                if (seconds >= 1f) warnings.add(Warning.OVERHEAT);
                break;
            case POWERTRAIN_FAULT:
                if (seconds >= 1f) warnings.add(Warning.POWERTRAIN);
                break;
        }
        return new VehicleSnapshot(
                timestampMillis,
                speed,
                brake,
                Gear.DRIVE,
                warnings,
                SAMPLE_LATITUDE,
                SAMPLE_LONGITUDE);
    }
}
