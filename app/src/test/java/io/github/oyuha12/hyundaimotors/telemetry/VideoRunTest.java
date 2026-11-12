package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.demo.AutoRoute;
import io.github.oyuha12.hyundaimotors.demo.VideoConfig;
import io.github.oyuha12.hyundaimotors.demo.VideoMode;
import io.github.oyuha12.hyundaimotors.diagnosis.Detection;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.facility.RouteSession;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSequencer;
import io.github.oyuha12.hyundaimotors.report.ReportFlow;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The whole video-mode flow, twice in a row, wired as MainViewModel wires it: hidden trigger
 * (scenario start), detection, "의심됩니다" and the steps, waiting for the car to stop, the report
 * offer, a spoken (staged) report, the status screen, the safe stop route, then "상황 종료".
 * The second run must play exactly like the first: nothing may carry over.
 */
public class VideoRunTest {
    private static final long TICK = 250L;
    private static final long SPEECH_MILLIS = 3_000L;

    private final List<String> events = new ArrayList<>();
    private final RouteSession route = new RouteSession();
    private long now;
    private long spokenAt = -1L;
    private long offerSpokenAt = -1L;
    private long stopRequestedAt = SimulatedTelemetrySource.NOT_STOPPING;
    private float speed;

    private final GuidanceSequencer sequencer = new GuidanceSequencer(new GuidanceSequencer.Listener() {
        @Override
        public void onStep(int index, GuidanceStep step) {
            events.add("step " + (index + 1));
            spokenAt = now + SPEECH_MILLIS;
            if (step.waitsForStop && stopRequestedAt == SimulatedTelemetrySource.NOT_STOPPING) {
                stopRequestedAt = now + TICK;
            }
        }

        @Override
        public void onWaitingForStop(boolean waiting) {
            events.add(waiting ? "wait" : "stopped");
        }

        @Override
        public void onFinished() {
            report.onGuidanceFinished();
        }
    });

    private final ReportFlow report = new ReportFlow(new ReportFlow.Listener() {
        @Override
        public void onPrompt() {
            events.add("offer");
            offerSpokenAt = now + SPEECH_MILLIS;
        }

        @Override
        public void onListen() {
            events.add("listen");
        }

        @Override
        public void onReport(ReportFlow.Trigger trigger, @Nullable String heard) {
            events.add("report " + trigger);
            sequencer.pause();
        }

        @Override
        public void onReportFinished() {
            events.add("status");
            if (VideoMode.autoRoutesAt(true, AutoRoute.AFTER_REPORT, AutoRoute.AFTER_REPORT)) {
                route.onRequested(VideoConfig.safeStopFor(EmergencyType.UNINTENDED_ACCELERATION).name);
                events.add("route " + route.destination());
            }
        }
    });

    /** One run, from the hidden menu's scenario to "상황 종료". */
    private List<String> playOnce() {
        events.clear();
        spokenAt = -1L;
        offerSpokenAt = -1L;
        stopRequestedAt = SimulatedTelemetrySource.NOT_STOPPING;
        EmergencyDetector detector = new EmergencyDetector();
        boolean detected = false;
        boolean reported = false;
        for (now = 0; now <= 60_000L; now += TICK) {
            VehicleSnapshot reading = SimulatedTelemetrySource.snapshotAt(
                    Scenario.VIDEO_ACCELERATION, now, 0L, now, null, stopRequestedAt);
            speed = reading.speedKph;
            sequencer.onSpeed(reading.speedKph);
            if (!detected) {
                Detection detection = detector.evaluate(reading);
                if (detection != null) {
                    detected = true;
                    events.add("suspected " + detection.type);
                    sequencer.start(EmergencyPlan.steps(detection.type));
                }
            } else if (spokenAt >= 0 && now >= spokenAt) {
                spokenAt = -1L;
                sequencer.onStepSpoken();
            }
            // Listening starts only once the offer has been read.
            if (offerSpokenAt >= 0 && now >= offerSpokenAt) {
                offerSpokenAt = -1L;
                report.onPromptSpoken();
            }
            if (report.isAwaiting() && !reported) {
                reported = report.onStagedVoice();
                report.onAnnounced();
                sequencer.resume();
            }
        }
        // The navigation app answers: our own cancel first, then the new route.
        route.onRouteCancelled();
        route.onRouteStarted();
        route.onRoutingCompleted();
        events.add("navi " + route.phase());
        assertEquals(0f, speed, 0f);
        close();
        return new ArrayList<>(events);
    }

    /** "상황 종료": what closeEmergency resets. */
    private void close() {
        sequencer.stop();
        report.reset();
        if (route.isActive()) events.add("cancel route");
        route.clear();
    }

    @Test
    public void twoRunsInARow_playTheSame() {
        List<String> first = playOnce();
        assertEquals(Arrays.asList(
                "suspected UNINTENDED_ACCELERATION",
                "step 1", "step 2", "step 3", "wait", "stopped", "step 4",
                "offer", "listen", "report STAGED_VOICE", "status",
                "route 양재역 공영주차장", "navi GUIDING", "cancel route"), first);

        // Nothing is left over after "상황 종료".
        assertEquals(-1, sequencer.index());
        assertFalse(sequencer.isWaiting());
        assertFalse(report.isReported());
        assertFalse(report.isAwaiting());
        assertEquals(RouteSession.Phase.IDLE, route.phase());
        // The navigation app's answer to our cancel arrives late and changes nothing.
        assertFalse(route.onRouteCancelled());

        assertEquals(first, playOnce());
    }

    /** A guard on MainViewModel.closeEmergency: everything a second run needs is reset there. */
    @Test
    public void closeEmergency_resetsEverything() throws Exception {
        String source = new String(Files.readAllBytes(new File(
                "src/main/java/io/github/oyuha12/hyundaimotors/MainViewModel.java").toPath()),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private void closeEmergency(");
        String body = source.substring(start, source.indexOf("\n    }", start));
        for (String reset : new String[] {
                "stopAllSpeech()", // speech, listening, timers, countdown
                "sequencer.stop()", // steps and waiting
                "stepIndex.setValue(0)",
                "clearReport()", // report flow, receipt, bubble, status screen
                "navigator.endRoute()", // navigation app destination (video mode)
                "dispatches.setValue(Collections.emptyMap())", // arrival countdowns
                "stopSimulation()"}) {
            assertTrue(reset, body.contains(reset));
        }
    }
}
