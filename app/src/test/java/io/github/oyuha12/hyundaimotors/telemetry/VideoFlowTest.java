package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.demo.VideoConfig;
import io.github.oyuha12.hyundaimotors.diagnosis.Detection;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSequencer;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The video scenarios end to end, without an emulator: detection, guidance steps, waiting for
 * the car to stop, slowing down, then the next step. Wired the way MainViewModel wires them:
 * every reading goes to the sequencer, and reaching a step that waits for a stop asks the
 * simulation to pull over. Reading a step aloud takes a fixed time here, and the flow must be
 * the same whether the voice is fast or slow.
 */
public class VideoFlowTest {
    private static final long TICK = 250L;
    /** Time to read one step, from very fast to slow. */
    private static final long[] SPEECH_MILLIS = {1_000L, 2_000L, 4_000L, 8_000L};

    private static final List<String> ACCELERATION_FLOW = Arrays.asList(
            "detected", "step 1", "step 2", "step 3", "wait", "stopped", "step 4", "finished");
    private static final List<String> FIRE_FLOW = Arrays.asList(
            "detected", "step 1", "wait", "stopped", "step 2", "step 3", "step 4", "finished");

    /** What happened in one run. Times are scenario milliseconds. */
    private static final class Run {
        EmergencyType detected;
        long detectedAt = -1L;
        final List<String> events = new ArrayList<>();
        long waitStepAt = -1L;
        long stopRequestedAt = SimulatedTelemetrySource.NOT_STOPPING;
        /** First reading slower than the one before, after the stop request. */
        long slowingAt = -1L;
        /** First reading below 1 km/h after the stop request. */
        long stoppedAt = -1L;
        /** Speed when the step after the waiting one began. */
        float speedAfterWait = Float.NaN;
        float topSpeed;
        final List<Gear> gears = new ArrayList<>();
        float coolestAfterDetection = Float.MAX_VALUE;
        boolean brakeAtDetection;
    }

    private static Run play(Scenario scenario, long leadInMillis, long speechMillis) {
        Run run = new Run();
        EmergencyDetector detector = new EmergencyDetector();
        long[] now = {0L};
        long[] spokenAt = {-1L};
        float[] speed = {0f};
        int[] waitIndex = {-1};
        GuidanceSequencer sequencer = new GuidanceSequencer(new GuidanceSequencer.Listener() {
            @Override
            public void onStep(int index, GuidanceStep step) {
                run.events.add("step " + (index + 1));
                spokenAt[0] = now[0] + speechMillis;
                if (waitIndex[0] >= 0 && index == waitIndex[0] + 1) run.speedAfterWait = speed[0];
                if (step.waitsForStop && waitIndex[0] < 0) {
                    waitIndex[0] = index;
                    run.waitStepAt = now[0];
                    // Like SimulatedTelemetrySource.startStopping(): from the next reading on.
                    run.stopRequestedAt = now[0] + TICK;
                }
            }

            @Override
            public void onWaitingForStop(boolean waiting) {
                run.events.add(waiting ? "wait" : "stopped");
            }

            @Override
            public void onFinished() {
                run.events.add("finished");
            }
        });

        float previous = Float.NaN;
        for (long t = 0; t <= leadInMillis + 60_000L; t += TICK) {
            now[0] = t;
            VehicleSnapshot reading = SimulatedTelemetrySource.snapshotAt(
                    scenario, t, leadInMillis, t, null, run.stopRequestedAt);
            speed[0] = reading.speedKph;
            run.topSpeed = Math.max(run.topSpeed, reading.speedKph);
            if (run.gears.isEmpty() || run.gears.get(run.gears.size() - 1) != reading.gear) {
                run.gears.add(reading.gear);
            }
            if (run.stopRequestedAt != SimulatedTelemetrySource.NOT_STOPPING
                    && t >= run.stopRequestedAt) {
                if (run.slowingAt < 0 && reading.speedKph < previous) run.slowingAt = t;
                if (run.stoppedAt < 0 && GuidanceSequencer.isStopped(reading.speedKph)) {
                    run.stoppedAt = t;
                }
            }
            if (run.detected != null) {
                run.coolestAfterDetection =
                        Math.min(run.coolestAfterDetection, reading.cabinTemperatureC);
            }
            previous = reading.speedKph;

            sequencer.onSpeed(reading.speedKph);
            if (run.detected == null) {
                Detection detection = detector.evaluate(reading);
                if (detection != null) {
                    run.detected = detection.type;
                    run.detectedAt = t;
                    run.brakeAtDetection = Boolean.TRUE.equals(reading.brakePressed);
                    run.events.add("detected");
                    // Video mode skips the yes/no question: guidance starts at once.
                    sequencer.start(EmergencyPlan.steps(detection.type));
                }
            } else if (spokenAt[0] >= 0 && t >= spokenAt[0]) {
                spokenAt[0] = -1L;
                sequencer.onStepSpoken();
            }
        }
        return run;
    }

    @Test
    public void acceleration_sameFlowAtAnyVoiceSpeedAndLeadIn() {
        for (long speech : SPEECH_MILLIS) {
            for (int seconds : VideoConfig.LEAD_IN_SECONDS) {
                long leadIn = seconds * 1000L;
                Run run = play(Scenario.VIDEO_ACCELERATION, leadIn, speech);
                String where = "speech " + speech + " ms, lead-in " + seconds + " s";
                assertEquals(where, EmergencyType.UNINTENDED_ACCELERATION, run.detected);
                assertTrue(where, run.detectedAt >= leadIn);
                assertTrue(where, run.brakeAtDetection);
                assertEquals(where, ACCELERATION_FLOW, run.events);
                assertTrue(where, run.speedAfterWait < GuidanceSequencer.STOPPED_BELOW_KPH);
                assertEquals(where, Arrays.asList(Gear.DRIVE, Gear.NEUTRAL, Gear.PARK), run.gears);
            }
        }
    }

    @Test
    public void fire_sameFlowAtAnyVoiceSpeedAndLeadIn() {
        for (long speech : SPEECH_MILLIS) {
            for (int seconds : VideoConfig.LEAD_IN_SECONDS) {
                long leadIn = seconds * 1000L;
                Run run = play(Scenario.VIDEO_FIRE, leadIn, speech);
                String where = "speech " + speech + " ms, lead-in " + seconds + " s";
                assertEquals(where, EmergencyType.FIRE, run.detected);
                assertTrue(where, run.detectedAt >= leadIn);
                assertEquals(where, FIRE_FLOW, run.events);
                assertTrue(where, run.speedAfterWait < GuidanceSequencer.STOPPED_BELOW_KPH);
                assertEquals(where, Arrays.asList(Gear.DRIVE, Gear.PARK), run.gears);
                // The cabin stays hot after detection, also once stopped.
                assertTrue(where, run.coolestAfterDetection >= 50f);
            }
        }
    }

    /** Slowing starts only a few seconds after the parking brake step, and takes 6 to 8 s. */
    @Test
    public void acceleration_slowsDownOnlyAfterTheParkingBrakeStep() {
        for (long speech : SPEECH_MILLIS) {
            Run run = play(Scenario.VIDEO_ACCELERATION, 5_000L, speech);
            String where = "speech " + speech + " ms";
            assertEquals(where, run.waitStepAt + TICK, run.stopRequestedAt);
            assertTrue(where, run.slowingAt
                    >= run.stopRequestedAt + SimulatedTelemetrySource.STOP_DELAY_MILLIS);
            long slowing = run.stoppedAt - run.slowingAt;
            assertTrue(where + ": " + slowing, slowing >= 6_000L && slowing <= 8_000L);
            assertTrue(where, run.topSpeed <= SimulatedTelemetrySource.ACCELERATION_TOP_KPH);
        }
    }

    /** At a normal voice speed the car reaches about 90 km/h before the parking brake step. */
    @Test
    public void acceleration_reachesAbout90() {
        for (long speech = 2_000L; speech <= 8_000L; speech += 2_000L) {
            Run run = play(Scenario.VIDEO_ACCELERATION, 0L, speech);
            assertEquals("speech " + speech + " ms", 90f, run.topSpeed, 0.01f);
        }
    }

    @Test
    public void fire_pullsOverOnlyAfterTheShoulderStep() {
        for (long speech : SPEECH_MILLIS) {
            Run run = play(Scenario.VIDEO_FIRE, 3_000L, speech);
            String where = "speech " + speech + " ms";
            assertEquals(where, run.detectedAt, run.waitStepAt);
            assertTrue(where, run.slowingAt
                    >= run.stopRequestedAt + SimulatedTelemetrySource.STOP_DELAY_MILLIS);
            assertTrue(where, run.stoppedAt - run.slowingAt <= SimulatedTelemetrySource.FIRE_STOP_MILLIS);
            assertEquals(where, 60f, run.topSpeed, 0.01f);
        }
    }

    /** The third scenario is the existing collision with a lead-in: no stop waits involved. */
    @Test
    public void collision_readsStraightThrough() {
        Run run = play(VideoConfig.SCENARIO_THIRD, 3_000L, 2_000L);
        assertEquals(EmergencyType.ACCIDENT, run.detected);
        assertEquals(Arrays.asList(
                "detected", "step 1", "step 2", "step 3", "step 4", "step 5", "finished"),
                run.events);
        assertEquals(SimulatedTelemetrySource.NOT_STOPPING, run.stopRequestedAt);
    }
}
