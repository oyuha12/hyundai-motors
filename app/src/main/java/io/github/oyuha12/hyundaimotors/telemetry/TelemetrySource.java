package io.github.oyuha12.hyundaimotors.telemetry;

/** Supplies vehicle readings on the main thread. */
public interface TelemetrySource {
    interface Listener {
        void onSnapshot(VehicleSnapshot snapshot);
    }

    void start(Listener listener);

    void stop();
}
