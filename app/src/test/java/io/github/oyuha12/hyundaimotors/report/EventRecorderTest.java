package io.github.oyuha12.hyundaimotors.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import org.junit.Test;

public class EventRecorderTest {
    private static VehicleSnapshot at(long millis, float speed) {
        return new VehicleSnapshot(millis, speed, true, Gear.DRIVE, false, 22f, false, 37.5, 127.0)
                .withChassis(false, new float[] {230f, 230f, Float.NaN, 230f}, 5f);
    }

    @Test
    public void keepsOnlyTheLastMinute() {
        EventRecorder recorder = new EventRecorder();
        for (long t = 0; t <= 120_000L; t += 1_000L) {
            recorder.add(at(t, 50f));
        }
        assertEquals(61, recorder.size());
    }

    @Test
    public void json_describesTheEventAndReadings() {
        EventRecorder recorder = new EventRecorder();
        recorder.add(at(0L, 40f));
        recorder.add(at(1_000L, 48f));
        String json = recorder.toJson(EmergencyType.UNINTENDED_ACCELERATION, true, "브레이크 \"테스트\"", 0L);
        assertTrue(json, json.contains("\"type\": \"UNINTENDED_ACCELERATION\""));
        assertTrue(json, json.contains("\"detected\": true"));
        assertTrue(json, json.contains("\"reason\": \"브레이크 \\\"테스트\\\"\""));
        assertTrue(json, json.contains("\"recordedAt\": \"1970-01-01T00:00:00.000Z\""));
        // Times are relative to the last reading, so the event is at t = 0.
        assertTrue(json, json.contains("{\"t\": -1000, \"speedKph\": 40.0"));
        assertTrue(json, json.contains("{\"t\": 0, \"speedKph\": 48.0"));
        assertTrue(json, json.contains("\"tires\": [230.0, 230.0, null, 230.0]"));
    }

    @Test
    public void emptyRecord_isStillValid() {
        String json = new EventRecorder().toJson(EmergencyType.FLOOD, false, "", 0L);
        assertTrue(json, json.contains("\"readings\": []"));
    }

    @Test
    public void fileName_includesTimeAndType() {
        String name = EventRecorder.fileName(EmergencyType.FIRE, 0L);
        assertTrue(name, name.endsWith("_FIRE.json"));
        assertEquals("yyyyMMdd_HHmmss_FIRE.json".length(), name.length());
    }
}
