package io.github.oyuha12.hyundaimotors.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSequencer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ReportFlowTest {
    private final List<String> events = new ArrayList<>();
    private final ReportFlow flow = new ReportFlow(new ReportFlow.Listener() {
        @Override
        public void onPrompt() {
            events.add("prompt");
        }

        @Override
        public void onListen() {
            events.add("listen");
        }

        @Override
        public void onReport(ReportFlow.Trigger trigger, @Nullable String heard) {
            events.add("report " + trigger + (heard == null ? "" : " " + heard));
        }

        @Override
        public void onReportFinished() {
            events.add("status");
        }
    });

    @Test
    public void voice_afterTheLastStep() {
        flow.onGuidanceFinished();
        assertEquals(Arrays.asList("prompt"), events);
        // Nothing is heard while the offer is being read.
        assertFalse(flow.isAwaiting());
        assertFalse(flow.onHeard("신고해 줘"));

        flow.onPromptSpoken();
        assertTrue(flow.isAwaiting());
        assertTrue(flow.onHeard("신고해 줘"));
        assertFalse(flow.isAwaiting());
        flow.onAnnounced();
        assertEquals(
                Arrays.asList("prompt", "listen", "report VOICE 신고해 줘", "status"), events);
    }

    @Test
    public void otherReplies_keepWaitingForTheButtonOrStandIn() {
        flow.onGuidanceFinished();
        flow.onPromptSpoken();
        assertFalse(flow.onHeard("괜찮아요"));
        assertFalse(flow.onHeard(""));
        assertTrue(flow.isAwaiting());
        assertTrue(flow.onStagedVoice());
        assertEquals(Arrays.asList("prompt", "listen", "report STAGED_VOICE"), events);
    }

    @Test
    public void standIn_onlyWhileAwaiting() {
        assertFalse(flow.onStagedVoice());
        flow.onGuidanceFinished();
        assertFalse(flow.onStagedVoice()); // Still reading the offer.
        flow.onPromptSpoken();
        assertTrue(flow.onStagedVoice());
        assertFalse(flow.onStagedVoice());
        assertEquals(Arrays.asList("prompt", "listen", "report STAGED_VOICE"), events);
    }

    /** The button works at any time, once. The status waits for the guidance to end. */
    @Test
    public void button_duringTheGuidance() {
        assertTrue(flow.onButton());
        assertFalse(flow.onButton());
        flow.onAnnounced();
        assertEquals(Arrays.asList("report BUTTON"), events);

        flow.onGuidanceFinished(); // No offer: already reported.
        assertEquals(Arrays.asList("report BUTTON", "status"), events);
    }

    @Test
    public void button_whileTheOfferIsRead() {
        flow.onGuidanceFinished();
        assertTrue(flow.onButton());
        flow.onPromptSpoken(); // Too late: no listening after a report.
        flow.onAnnounced();
        assertEquals(Arrays.asList("prompt", "report BUTTON", "status"), events);
    }

    /** Driver health reports on confirmation; no offer or announcement follows. */
    @Test
    public void reportedOnConfirmation() {
        flow.markReported();
        assertTrue(flow.isReported());
        assertFalse(flow.onButton());
        flow.onGuidanceFinished();
        assertEquals(Arrays.asList("status"), events);
    }

    @Test
    public void readingAgain_offersAgainAtTheEnd() {
        flow.onGuidanceFinished();
        flow.onPromptSpoken();
        flow.onGuidanceRestarted();
        assertFalse(flow.isAwaiting());
        flow.onGuidanceFinished();
        assertEquals(Arrays.asList("prompt", "listen", "prompt"), events);
    }

    /** An announcement cut off by "다시 듣기" counts as made, so the status still follows. */
    @Test
    public void readingAgain_duringTheAnnouncement() {
        flow.onButton();
        flow.onGuidanceRestarted();
        flow.onAnnounced(); // The late timeout changes nothing.
        flow.onGuidanceFinished();
        assertEquals(Arrays.asList("report BUTTON", "status"), events);
    }

    @Test
    public void reset_startsOver() {
        flow.onButton();
        flow.reset();
        assertFalse(flow.isReported());
        flow.onGuidanceFinished();
        assertEquals(Arrays.asList("report BUTTON", "prompt"), events);
    }

    /**
     * Wired as in MainViewModel: the end of the guidance offers a report, a request pauses the
     * guidance for the announcement, and the status follows.
     */
    @Test
    public void withTheGuidance_endToEnd() {
        List<String> all = new ArrayList<>();
        GuidanceSequencer[] sequencer = new GuidanceSequencer[1];
        ReportFlow[] report = new ReportFlow[1];
        sequencer[0] = new GuidanceSequencer(new GuidanceSequencer.Listener() {
            @Override
            public void onStep(int index, GuidanceStep step) {
                all.add("step " + (index + 1));
            }

            @Override
            public void onWaitingForStop(boolean waiting) {
                all.add(waiting ? "wait" : "stopped");
            }

            @Override
            public void onFinished() {
                report[0].onGuidanceFinished();
            }
        });
        report[0] = new ReportFlow(new ReportFlow.Listener() {
            @Override
            public void onPrompt() {
                all.add("prompt");
            }

            @Override
            public void onListen() {
                all.add("listen");
            }

            @Override
            public void onReport(ReportFlow.Trigger trigger, @Nullable String heard) {
                all.add("report " + trigger);
                sequencer[0].pause();
            }

            @Override
            public void onReportFinished() {
                all.add("status");
            }
        });

        sequencer[0].onSpeed(90f);
        sequencer[0].start(EmergencyPlan.steps(EmergencyType.UNINTENDED_ACCELERATION));
        for (int i = 0; i < 3; i++) sequencer[0].onStepSpoken();
        sequencer[0].onSpeed(0f);
        sequencer[0].onStepSpoken();
        report[0].onPromptSpoken();
        report[0].onHeard("119 불러 줘");
        report[0].onAnnounced();
        sequencer[0].resume();

        assertEquals(Arrays.asList(
                "step 1", "step 2", "step 3", "wait", "stopped", "step 4",
                "prompt", "listen", "report VOICE", "status"), all);
    }
}
