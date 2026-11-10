package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;

import io.github.oyuha12.hyundaimotors.diagnosis.Detection;
import io.github.oyuha12.hyundaimotors.drowsiness.DrivingWarningMonitor;
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

    @Test
    public void newScenarios_reachTheirCondition() {
        assertEquals(EmergencyType.BRAKE_FAILURE, firstDetected(Scenario.BRAKE_FAILURE));
        assertEquals(EmergencyType.TIRE_FAILURE, firstDetected(Scenario.TIRE_PRESSURE_LOSS));
    }

    /** Warning and reminder demos must never be mistaken for an emergency. */
    @Test
    public void warningDemos_raiseNoEmergency() {
        assertEquals(null, firstDetected(Scenario.MONOTONOUS_DRIVE));
        assertEquals(null, firstDetected(Scenario.PEDAL_MISAPPLICATION));
        assertEquals(null, firstDetected(Scenario.FAST_REVERSE));
        assertEquals(null, firstDetected(Scenario.CURVE_OVERSPEED));
    }

    /** Each warning demo must actually trigger its warning. */
    @Test
    public void warningDemos_triggerTheirWarning() {
        assertEquals(DrivingWarningMonitor.Warning.PEDAL_MISAPPLICATION,
                firstWarning(Scenario.PEDAL_MISAPPLICATION));
        assertEquals(DrivingWarningMonitor.Warning.FAST_REVERSE, firstWarning(Scenario.FAST_REVERSE));
        assertEquals(DrivingWarningMonitor.Warning.CURVE_OVERSPEED,
                firstWarning(Scenario.CURVE_OVERSPEED));
    }

    private static DrivingWarningMonitor.Warning firstWarning(Scenario scenario) {
        DrivingWarningMonitor monitor = new DrivingWarningMonitor();
        for (long t = 0; t <= 10_000L; t += 250L) {
            DrivingWarningMonitor.Warning warning =
                    monitor.update(SimulatedTelemetrySource.snapshotAt(scenario, t, t));
            if (warning != null) return warning;
        }
        return null;
    }
}
