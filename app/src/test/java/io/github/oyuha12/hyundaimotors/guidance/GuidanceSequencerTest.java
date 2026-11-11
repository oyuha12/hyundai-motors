package io.github.oyuha12.hyundaimotors.guidance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class GuidanceSequencerTest {
    private final List<String> events = new ArrayList<>();
    private final GuidanceSequencer sequencer = new GuidanceSequencer(new GuidanceSequencer.Listener() {
        @Override
        public void onStep(int index, GuidanceStep step) {
            events.add("step " + (index + 1));
        }

        @Override
        public void onWaitingForStop(boolean waiting) {
            events.add(waiting ? "wait" : "stopped");
        }

        @Override
        public void onFinished() {
            events.add("finished");
        }
    });

    private void readSteps(int count) {
        for (int i = 0; i < count; i++) sequencer.onStepSpoken();
    }

    @Test
    public void stepsWithoutWaiting_followOneAnother_evenWhileDriving() {
        sequencer.onSpeed(80f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.ACCIDENT));
        readSteps(10);
        assertEquals(Arrays.asList("step 1", "step 2", "step 3", "step 4", "step 5", "finished"),
                events);
        assertEquals(4, sequencer.index());
        assertTrue(sequencer.isFinished());
    }

    @Test
    public void acceleration_waitsAfterTheParkingBrakeUntilStopped() {
        sequencer.onSpeed(90f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.UNINTENDED_ACCELERATION));
        readSteps(3);
        assertEquals(Arrays.asList("step 1", "step 2", "step 3", "wait"), events);
        assertTrue(sequencer.isWaiting());

        sequencer.onSpeed(40f);
        sequencer.onSpeed(1f); // 1 km/h is still moving.
        sequencer.onStepSpoken(); // A late "speech finished" changes nothing while waiting.
        assertEquals(2, sequencer.index());

        sequencer.onSpeed(0.5f);
        assertEquals(
                Arrays.asList("step 1", "step 2", "step 3", "wait", "stopped", "step 4"), events);
        assertFalse(sequencer.isWaiting());
    }

    @Test
    public void fire_waitsAfterTheFirstStep() {
        sequencer.onSpeed(60f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        readSteps(1);
        assertEquals(Arrays.asList("step 1", "wait"), events);
        sequencer.onSpeed(0f);
        readSteps(2);
        assertEquals(Arrays.asList("step 1", "wait", "stopped", "step 2", "step 3", "step 4"),
                events);
        readSteps(1);
        assertEquals("finished", events.get(events.size() - 1));
    }

    @Test
    public void alreadyStopped_noWaiting() {
        sequencer.onSpeed(0f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.UNINTENDED_ACCELERATION));
        readSteps(3);
        assertEquals(Arrays.asList("step 1", "step 2", "step 3", "step 4"), events);
    }

    @Test
    public void unknownSpeed_noWaiting() {
        sequencer.onSpeed(null);
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        readSteps(1);
        assertEquals(Arrays.asList("step 1", "step 2"), events);
    }

    @Test
    public void lastStep_endsWithoutWaiting() {
        sequencer.onSpeed(0f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.UNINTENDED_ACCELERATION));
        readSteps(3);
        sequencer.onSpeed(80f);
        readSteps(2);
        assertEquals(Arrays.asList("step 1", "step 2", "step 3", "step 4", "finished"), events);
        assertFalse(sequencer.isWaiting());
    }

    @Test
    public void readingAgain_startsOverAndStopsWaiting() {
        sequencer.onSpeed(60f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        readSteps(1);
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        assertEquals(Arrays.asList("step 1", "wait", "stopped", "step 1"), events);
        assertEquals(0, sequencer.index());
    }

    @Test
    public void stop_endsEverything() {
        sequencer.onSpeed(60f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        readSteps(1);
        sequencer.stop();
        sequencer.onSpeed(0f);
        readSteps(3);
        assertEquals(Arrays.asList("step 1", "wait", "stopped"), events);
        assertEquals(-1, sequencer.index());
    }

    /** Paused for a report announcement: the interrupted step is read again afterwards. */
    @Test
    public void pause_holdsTheStepAndReadsItAgain() {
        sequencer.onSpeed(0f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.ACCIDENT));
        sequencer.pause();
        readSteps(3);
        assertEquals(Arrays.asList("step 1"), events);
        sequencer.resume();
        assertEquals(Arrays.asList("step 1", "step 1"), events);
        readSteps(1);
        assertEquals(Arrays.asList("step 1", "step 1", "step 2"), events);
    }

    /** A stop during the pause starts the next step only once the pause ends. */
    @Test
    public void pause_whileWaiting_advancesOnResume() {
        sequencer.onSpeed(60f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        readSteps(1);
        sequencer.pause();
        sequencer.onSpeed(0f);
        assertEquals(Arrays.asList("step 1", "wait"), events);
        sequencer.resume();
        assertEquals(Arrays.asList("step 1", "wait", "stopped", "step 2"), events);
    }

    @Test
    public void pause_afterTheLastStep_changesNothing() {
        sequencer.onSpeed(0f);
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        readSteps(4);
        sequencer.pause();
        sequencer.resume();
        sequencer.onStepSpoken();
        assertEquals(
                Arrays.asList("step 1", "step 2", "step 3", "step 4", "finished"), events);
    }

    @Test
    public void resume_withoutPause_changesNothing() {
        sequencer.start(EmergencyPlan.steps(EmergencyType.FIRE));
        sequencer.resume();
        assertEquals(Arrays.asList("step 1"), events);
    }

    @Test
    public void stoppedBelowOneKph() {
        assertTrue(GuidanceSequencer.isStopped(0f));
        assertTrue(GuidanceSequencer.isStopped(0.99f));
        assertFalse(GuidanceSequencer.isStopped(1f));
        assertTrue(GuidanceSequencer.isStopped(null));
    }
}
