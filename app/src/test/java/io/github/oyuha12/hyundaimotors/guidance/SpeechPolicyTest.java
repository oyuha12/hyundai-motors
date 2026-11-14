package io.github.oyuha12.hyundaimotors.guidance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeechPolicyTest {
    @Test
    public void stop_reachesTheEnginesAsBefore_outsideVideoMode() {
        assertTrue(SpeechPolicy.sendsStop(false, false));
        assertTrue(SpeechPolicy.sendsStop(false, true));
    }

    @Test
    public void stop_inVideoMode_onlyWhileASentenceIsRead() {
        // The guidance starts with a stop of whatever was playing; with nothing playing, the
        // first sentence must go straight to the engine.
        assertFalse(SpeechPolicy.sendsStop(true, false));
        assertTrue(SpeechPolicy.sendsStop(true, true));
    }

    @Test
    public void settle_onlyInVideoMode_andOnlyRightAfterAStop() {
        long stop = 10_000L;
        assertEquals(0L, SpeechPolicy.settleDelay(false, stop, stop));
        assertEquals(0L, SpeechPolicy.settleDelay(true, 0L, stop)); // Never stopped
        assertEquals(SpeechPolicy.STOP_SETTLE_MILLIS, SpeechPolicy.settleDelay(true, stop, stop));
        assertEquals(100L, SpeechPolicy.settleDelay(true, stop, stop + SpeechPolicy.STOP_SETTLE_MILLIS - 100L));
        assertEquals(0L, SpeechPolicy.settleDelay(true, stop, stop + 5_000L));
    }

    @Test
    public void silence_keepsPleos_whenNothingElseReadsKorean() {
        assertSame(SpeechPolicy.Silence.KEEP_WAITING, SpeechPolicy.onSilence(false, 0));
        assertSame(SpeechPolicy.Silence.KEEP_WAITING, SpeechPolicy.onSilence(false, 5));
    }

    @Test
    public void silence_switchesForGood_onlyAfterTwoInARow() {
        // A slow first sentence (the engine loading) costs one sentence, not the Pleos voice.
        assertSame(SpeechPolicy.Silence.ANDROID_THIS_TIME, SpeechPolicy.onSilence(true, 0));
        assertSame(SpeechPolicy.Silence.ANDROID_FROM_NOW_ON, SpeechPolicy.onSilence(true, 1));
    }

    @Test
    public void heldSentence_isReadWhenAnEngineIsReady_ifNotTooLate() {
        long since = 20_000L;
        assertTrue(SpeechPolicy.readsWhenReady(true, since, since + 1_000L));
        assertTrue(SpeechPolicy.readsWhenReady(true, since, since + SpeechPolicy.WAIT_FOR_ENGINE_MILLIS));
        assertFalse(SpeechPolicy.readsWhenReady(true, since, since + SpeechPolicy.WAIT_FOR_ENGINE_MILLIS + 1L));
        assertFalse(SpeechPolicy.readsWhenReady(true, 0L, since)); // Nothing held
        assertFalse(SpeechPolicy.readsWhenReady(false, since, since)); // Not in video mode
    }
}
