package io.github.oyuha12.hyundaimotors.guidance;

/**
 * Video mode rules for the guidance voice, kept apart from the engines so they can be checked
 * in unit tests. Outside video mode {@link GuidanceSpeaker} works as before.
 *
 * <p>The goal: every guidance sentence is heard the moment its text appears, without a button.
 * So nothing is stopped that is not playing, a sentence never races a stop that was just sent,
 * a sentence that comes before any engine is ready waits for one, and the Pleos engine is only
 * given up for an engine that can read Korean.
 */
final class SpeechPolicy {
    private SpeechPolicy() {}

    /** After a stop, a new sentence waits this long so the engine's stop cannot cut it. */
    static final long STOP_SETTLE_MILLIS = 400L;
    /** A Pleos sentence with no sign of being read after this counts as silent. */
    static final long PLEOS_SILENCE_MILLIS = 5_000L;
    /** A sentence that came before any engine was ready is read once one is, if this is not over. */
    static final long WAIT_FOR_ENGINE_MILLIS = 8_000L;
    /** Silent Pleos sentences in a row before the Android engine reads all the rest. */
    static final int SILENT_IN_A_ROW_TO_SWITCH = 2;

    /** What to do when a Pleos sentence stays silent. */
    enum Silence {
        /** No other engine can read Korean: leave the Pleos engine be. */
        KEEP_WAITING,
        /** Read this sentence with the Android engine; try Pleos again next time. */
        ANDROID_THIS_TIME,
        /** Read this and every later sentence with the Android engine. */
        ANDROID_FROM_NOW_ON
    }

    /** Whether a stop reaches the engines: in video mode only while a sentence is being read. */
    static boolean sendsStop(boolean videoMode, boolean speaking) {
        return !videoMode || speaking;
    }

    /** How long a new sentence waits after the last stop sent to the engines. */
    static long settleDelay(boolean videoMode, long lastStopAt, long now) {
        if (!videoMode || lastStopAt <= 0L) return 0L;
        return Math.max(0L, lastStopAt + STOP_SETTLE_MILLIS - now);
    }

    /**
     * @param androidKorean the Android engine is ready and has a Korean voice
     * @param silentBefore silent Pleos sentences right before this one
     */
    static Silence onSilence(boolean androidKorean, int silentBefore) {
        if (!androidKorean) return Silence.KEEP_WAITING;
        return silentBefore + 1 >= SILENT_IN_A_ROW_TO_SWITCH
                ? Silence.ANDROID_FROM_NOW_ON
                : Silence.ANDROID_THIS_TIME;
    }

    /** Whether a sentence held for an engine is still worth reading now that one is ready. */
    static boolean readsWhenReady(boolean videoMode, long waitingSince, long now) {
        return videoMode && waitingSince > 0L && now - waitingSince <= WAIT_FOR_ENGINE_MILLIS;
    }
}
