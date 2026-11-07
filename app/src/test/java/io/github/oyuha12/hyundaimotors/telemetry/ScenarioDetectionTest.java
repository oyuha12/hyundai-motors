package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;

import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import org.junit.Test;

/** Every demo scenario must reach the condition it is named after. */
public class ScenarioDetectionTest {
    private static EmergencyType firstDetected(Scenario scenario) {
        EmergencyDetector detector = new EmergencyDetector();
        for (long t = 0; t <= 10_000L; t += 250L) {
            EmergencyType type = detector.evaluate(SimulatedTelemetrySource.snapshotAt(scenario, t, t));
            if (type != null) return type;
        }
        return null;
    }

    @Test
    public void scenarios_reachTheirCondition() {
        assertEquals(
                EmergencyType.UNINTENDED_ACCELERATION,
                firstDetected(Scenario.UNINTENDED_ACCELERATION));
        assertEquals(EmergencyType.BRAKE_FAILURE, firstDetected(Scenario.BRAKE_FAILURE));
        assertEquals(EmergencyType.COLLISION_SUSPECTED, firstDetected(Scenario.COLLISION));
        assertEquals(EmergencyType.OVERHEAT, firstDetected(Scenario.OVERHEAT));
        assertEquals(EmergencyType.POWERTRAIN_FAULT, firstDetected(Scenario.POWERTRAIN_FAULT));
    }
}
