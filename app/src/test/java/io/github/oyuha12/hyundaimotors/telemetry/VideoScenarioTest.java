package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.github.oyuha12.hyundaimotors.demo.VideoConfig;
import io.github.oyuha12.hyundaimotors.diagnosis.Detection;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import org.junit.Test;

/** The hidden menu's scenarios: nothing during the lead-in, then the named condition. */
public class VideoScenarioTest {
    private static final GeoPoint SDK = new GeoPoint(35.1, 129.0);

    @Test
    public void acceleration_detectedOnlyAfterLeadIn() {
        check(VideoConfig.SCENARIO_ACCELERATION, EmergencyType.UNINTENDED_ACCELERATION);
    }

    @Test
    public void fire_detectedOnlyAfterLeadIn() {
        check(VideoConfig.SCENARIO_FIRE, EmergencyType.FIRE);
    }

    @Test
    public void third_detectedOnlyAfterLeadIn() {
        check(VideoConfig.SCENARIO_THIRD, EmergencyType.ACCIDENT);
    }

    private static void check(Scenario scenario, EmergencyType expected) {
        for (int seconds : VideoConfig.LEAD_IN_SECONDS) {
            long leadIn = seconds * 1000L;
            EmergencyDetector detector = new EmergencyDetector();
            EmergencyType found = null;
            for (long t = 0; t <= leadIn + 20_000L && found == null; t += 250L) {
                Detection detection = detector.evaluate(
                        SimulatedTelemetrySource.snapshotAt(scenario, t, leadIn, t, SDK));
                if (t < leadIn) {
                    assertNull(scenario + " lead-in " + seconds + " s at " + t, detection);
                } else if (detection != null) {
                    found = detection.type;
                }
            }
            assertEquals(scenario + " lead-in " + seconds + " s", expected, found);
        }
    }
}
