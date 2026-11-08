package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;

import io.github.oyuha12.hyundaimotors.diagnosis.Detection;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import org.junit.Test;

/** Every demo scenario must reach the condition it is named after. */
public class ScenarioDetectionTest {
    private static EmergencyType firstDetected(Scenario scenario) {
        EmergencyDetector detector = new EmergencyDetector();
        for (long t = 0; t <= 20_000L; t += 250L) {
            Detection detection =
                    detector.evaluate(SimulatedTelemetrySource.snapshotAt(scenario, t, t));
            if (detection != null) return detection.type;
        }
        return null;
    }

    @Test
    public void scenarios_reachTheirCondition() {
        assertEquals(
                EmergencyType.UNINTENDED_ACCELERATION,
                firstDetected(Scenario.UNINTENDED_ACCELERATION));
        assertEquals(EmergencyType.ACCIDENT, firstDetected(Scenario.COLLISION));
        assertEquals(EmergencyType.ACCIDENT, firstDetected(Scenario.AIRBAG_DEPLOYED));
        assertEquals(EmergencyType.FIRE, firstDetected(Scenario.CABIN_FIRE));
    }

    /** The drowsiness demo must never be mistaken for an emergency. */
    @Test
    public void drowsinessDemo_raisesNoEmergency() {
        assertEquals(null, firstDetected(Scenario.MONOTONOUS_DRIVE));
    }
}
