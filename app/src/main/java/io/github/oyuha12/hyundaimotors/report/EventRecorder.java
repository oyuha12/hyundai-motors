package io.github.oyuha12.hyundaimotors.report;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Keeps the last minute of vehicle readings and turns them into an event record when an
 * emergency starts, like a simplified EDR (event data recorder). Records stay in the app's
 * private storage; nothing is uploaded.
 */
public final class EventRecorder {
    static final long KEEP_MILLIS = 60_000L;

    private final Deque<VehicleSnapshot> readings = new ArrayDeque<>();

    public void add(VehicleSnapshot reading) {
        readings.addLast(reading);
        while (reading.timestampMillis - readings.peekFirst().timestampMillis > KEEP_MILLIS) {
            readings.removeFirst();
        }
    }

    public int size() {
        return readings.size();
    }

    /** File name for a record, for example "20261110_153012_FIRE.json". */
    public static String fileName(EmergencyType type, long wallClockMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return format.format(new Date(wallClockMillis)) + "_" + type.name() + ".json";
    }

    /** The record as JSON: what happened, when, and the readings before it. */
    public String toJson(EmergencyType type, boolean detected, String reason, long wallClockMillis) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        long last = readings.isEmpty() ? 0 : readings.peekLast().timestampMillis;

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"type\": \"").append(type.name()).append("\",\n")
                .append("  \"detected\": ").append(detected).append(",\n")
                .append("  \"reason\": ").append(quote(reason)).append(",\n")
                .append("  \"recordedAt\": \"").append(iso.format(new Date(wallClockMillis))).append("\",\n")
                .append("  \"readings\": [");
        boolean first = true;
        for (VehicleSnapshot r : readings) {
            json.append(first ? "\n" : ",\n").append("    {\"t\": ").append(r.timestampMillis - last)
                    .append(", \"speedKph\": ").append(number(r.speedKph))
                    .append(", \"brake\": ").append(r.brakePressed)
                    .append(", \"gear\": \"").append(r.gear.name()).append('"')
                    .append(", \"airbag\": ").append(r.airbagDeployed)
                    .append(", \"cabinC\": ").append(number(r.cabinTemperatureC))
                    .append(", \"overheat\": ").append(r.overheatWarning)
                    .append(", \"brakeFluidLow\": ").append(r.brakeFluidLow)
                    .append(", \"tires\": [").append(number(r.tirePressure(0))).append(", ")
                    .append(number(r.tirePressure(1))).append(", ").append(number(r.tirePressure(2)))
                    .append(", ").append(number(r.tirePressure(3))).append(']')
                    .append(", \"steeringDeg\": ").append(number(r.steeringAngleDegrees))
                    .append(", \"lat\": ").append(number(r.latitude))
                    .append(", \"lon\": ").append(number(r.longitude))
                    .append('}');
            first = false;
        }
        json.append(first ? "]\n}\n" : "\n  ]\n}\n");
        return json.toString();
    }

    private static String number(@Nullable Number value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }
}
