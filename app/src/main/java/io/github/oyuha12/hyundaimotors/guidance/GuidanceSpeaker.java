package io.github.oyuha12.hyundaimotors.guidance;

import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.constant.Mode;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.listener.OnInitializeListener;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Reads guidance aloud with the Pleos on-device engine, and falls back to the Android engine
 * when the Pleos one is not available. On-device mode works without a network connection.
 *
 * <p>Video mode ({@link #setVideoMode}) makes every guidance sentence come out by itself the
 * moment its text appears, following {@link SpeechPolicy}: no stop for an idle engine, no
 * sentence racing a stop, a sentence held until an engine is ready, the Pleos engine retried in
 * hybrid mode when on-device synthesis fails, and given up only for an engine that reads Korean.
 *
 * <p>Logcat tag "GuidanceSpeaker" says which engine started, which one read each sentence, and
 * why. {@link #engine()} and {@link #lastOutcome()} give the same for the video menu.
 */
public final class GuidanceSpeaker {
    /** Which engine reads, as far as the app can tell. */
    public enum Engine {
        /** Neither engine has answered yet. */
        STARTING,
        /** The Pleos engine. */
        PLEOS,
        /** The Android engine, because the Pleos one is not available. */
        ANDROID,
        /** The Android engine, but it has no Korean voice. */
        ANDROID_NO_KOREAN,
        /** Neither engine works. */
        NONE
    }

    /** What became of the last sentence, as far as the engines said. */
    public enum Outcome {
        NONE,
        /** Held until an engine is ready. */
        WAITING_FOR_ENGINE,
        /** Sent to the Pleos engine, which has not said it started. */
        PLEOS_SENT,
        PLEOS_STARTED,
        /** The Pleos engine did not answer and no other engine reads Korean. */
        PLEOS_SILENT,
        /** Sent to the Android engine, which has not said it started. */
        ANDROID_SENT,
        ANDROID_STARTED,
        ANDROID_ERROR,
        /** No engine could take the sentence. */
        NO_ENGINE
    }

    private enum State { STARTING, READY, FAILED }

    private static final String TAG = "GuidanceSpeaker";
    private static final String UTTERANCE_ID = "guidance";
    /** {@link #isBusy()} gives up on a sentence that never reports its end after this long. */
    private static final long BUSY_MAX_MILLIS = 12_000L;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private TextToSpeech pleos;
    private boolean pleosReady;
    private State pleosState = State.STARTING;
    private Mode pleosMode = Mode.ON_DEVICE;
    private boolean triedHybrid;
    /** Video mode: the Pleos engine stayed silent too often; the Android engine reads instead. */
    private boolean pleosSilent;
    private int silentInARow;
    private boolean videoMode;
    private boolean pleosAnswered;
    @Nullable private android.speech.tts.TextToSpeech fallback;
    private boolean fallbackReady;
    private State fallbackState = State.STARTING;
    private boolean fallbackKorean;
    @Nullable private Runnable onDone;
    @Nullable private String pendingText;
    /** When the sentence in {@link #pendingText} went to an engine; 0 when none took it. */
    private long speakingSince;
    /** Video mode: when a sentence began waiting for an engine; 0 when none waits. */
    private long waitingSince;
    /** When a stop last went to the engines; 0 for never. */
    private long lastStopAt;
    private Outcome outcome = Outcome.NONE;

    private final Runnable silenceCheck = this::onPleosSilent;
    private final Runnable settledSpeak = this::speakNow;

    private final EventListener pleosEvents = new EventListener() {
        @Override
        public void onStart() {
            handler.post(() -> {
                pleosAnswered();
                if (pendingText != null) outcome = Outcome.PLEOS_STARTED;
            });
        }

        @Override
        public void onReady() {
            handler.post(GuidanceSpeaker.this::pleosAnswered);
        }

        @Override
        public void onUpdatedRms(double rms) {
            handler.post(GuidanceSpeaker.this::pleosAnswered);
        }

        @Override
        public void onStop() {
            handler.post(GuidanceSpeaker.this::pleosAnswered);
        }

        @Override
        public void onDone() {
            handler.post(GuidanceSpeaker.this::pleosAnswered);
            finish();
        }

        @Override
        public void onError(ErrorType error) {
            Log.w(TAG, "Pleos TTS error (" + pleosMode + "): " + error);
            handler.post(() -> {
                handler.removeCallbacks(silenceCheck);
                pleosReady = false;
                pleosState = State.FAILED;
                if (pendingText != null) speakWithFallback(pendingText);
                tryHybrid();
            });
        }
    };

    public GuidanceSpeaker(Context context) {
        appContext = context.getApplicationContext();
        startFallback(appContext);
        startPleos(Mode.ON_DEVICE);
    }

    private void startPleos(Mode mode) {
        pleosMode = mode;
        pleosState = State.STARTING;
        try {
            TextToSpeech engine = new TextToSpeech(appContext, mode);
            pleos = engine;
            engine.initialize(new OnInitializeListener() {
                @Override
                public void onSuccess() {
                    handler.post(() -> {
                        if (pleos != engine) return;
                        engine.addEventListener(pleosEvents);
                        engine.setLocale(Locale.KOREAN);
                        pleosReady = true;
                        pleosState = State.READY;
                        Log.i(TAG, "Pleos TTS ready (" + mode + ")");
                        onEngineReady();
                    });
                }

                @Override
                public void onFailure(ErrorType error) {
                    Log.w(TAG, "Pleos TTS unavailable (" + mode + "): " + error);
                    handler.post(() -> {
                        if (pleos != engine) return;
                        pleosState = State.FAILED;
                        tryHybrid();
                        onEngineReady();
                    });
                }
            });
        } catch (RuntimeException | LinkageError error) {
            // A missing pleos.car.permission.TTS_SERVICE ends here as a SecurityException.
            Log.w(TAG, "Pleos TTS unavailable (" + mode + ")", error);
            pleosState = State.FAILED;
        }
    }

    /**
     * Video mode: the on-device engine failed, so try the Pleos engine once more in hybrid mode,
     * which synthesizes on the server when the network is up and on the device otherwise.
     */
    private void tryHybrid() {
        if (!videoMode || triedHybrid || pleosMode == Mode.HYBRID) return;
        triedHybrid = true;
        Log.i(TAG, "Trying Pleos TTS in HYBRID mode");
        releasePleos();
        startPleos(Mode.HYBRID);
    }

    /**
     * Tries the Pleos engine again from on-device mode if it did not start, for example after
     * the TTS permission was granted. Does nothing while it works.
     */
    public void retryPleos() {
        if (pleosState != State.FAILED) return;
        Log.i(TAG, "Retrying Pleos TTS");
        triedHybrid = false;
        releasePleos();
        startPleos(Mode.ON_DEVICE);
    }

    private void startFallback(Context context) {
        fallback = new android.speech.tts.TextToSpeech(context, status -> {
            if (status != android.speech.tts.TextToSpeech.SUCCESS || fallback == null) {
                Log.w(TAG, "Android TTS unavailable: " + status);
                handler.post(() -> {
                    fallbackState = State.FAILED;
                    onEngineReady();
                });
                return;
            }
            int language = fallback.setLanguage(Locale.KOREAN);
            if (language < android.speech.tts.TextToSpeech.LANG_AVAILABLE) {
                // Some engines know Korean only with the country: ko-KR.
                language = fallback.setLanguage(Locale.KOREA);
            }
            boolean korean = language >= android.speech.tts.TextToSpeech.LANG_AVAILABLE;
            if (!korean) Log.w(TAG, "Android TTS has no Korean voice: " + language);
            for (android.speech.tts.TextToSpeech.EngineInfo info : fallback.getEngines()) {
                Log.i(TAG, "Android TTS engine installed: " + info.name);
            }
            fallback.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    handler.post(() -> {
                        if (pendingText != null) outcome = Outcome.ANDROID_STARTED;
                    });
                }

                @Override
                public void onDone(String utteranceId) {
                    finish();
                }

                @Override
                public void onError(String utteranceId) {
                    onError(utteranceId, 0);
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    Log.w(TAG, "Android TTS error: " + errorCode);
                    handler.post(() -> outcome = Outcome.ANDROID_ERROR);
                    finish();
                }
            });
            handler.post(() -> {
                fallbackKorean = korean;
                fallbackReady = true;
                fallbackState = State.READY;
                Log.i(TAG, "Android TTS ready, engine " + fallback.getDefaultEngine()
                        + (korean ? ", Korean" : ", no Korean"));
                onEngineReady();
            });
        });
    }

    /**
     * Video mode on: the rules in {@link SpeechPolicy}. Off: as before. Also tries the Pleos
     * engine in hybrid mode if the on-device one has already failed.
     */
    public void setVideoMode(boolean on) {
        videoMode = on;
        if (!on) {
            handler.removeCallbacks(silenceCheck);
            waitingSince = 0L;
        } else if (pleosState == State.FAILED) {
            tryHybrid();
        }
    }

    /**
     * Speaks text, replacing any sentence still being read. onDone may not run if no engine
     * works, so callers need their own timeout.
     */
    public void speak(String text, @Nullable Runnable onDone) {
        this.onDone = onDone;
        pendingText = text;
        speakingSince = 0L;
        waitingSince = 0L;
        handler.removeCallbacks(silenceCheck);
        handler.removeCallbacks(settledSpeak);
        long settle = SpeechPolicy.settleDelay(videoMode, lastStopAt, SystemClock.elapsedRealtime());
        if (settle > 0L) {
            handler.postDelayed(settledSpeak, settle);
        } else {
            speakNow();
        }
    }

    /** Hands {@link #pendingText} to the first engine that can take it. */
    private void speakNow() {
        String text = pendingText;
        if (text == null) return;
        if (usesPleos()) {
            try {
                pleosAnswered = false;
                pleos.speak(text, true);
                speakingSince = SystemClock.elapsedRealtime();
                outcome = Outcome.PLEOS_SENT;
                Log.d(TAG, "Pleos (" + pleosMode + "): " + text);
                if (videoMode) handler.postDelayed(silenceCheck, SpeechPolicy.PLEOS_SILENCE_MILLIS);
                return;
            } catch (RuntimeException error) {
                Log.w(TAG, "Pleos TTS speak failed", error);
                pleosReady = false;
                pleosState = State.FAILED;
                tryHybrid();
            }
        }
        if (fallbackReady && fallback != null) {
            speakWithFallback(text);
        } else if (videoMode && engine() == Engine.STARTING) {
            // Read once an engine is ready: see onEngineReady.
            waitingSince = SystemClock.elapsedRealtime();
            outcome = Outcome.WAITING_FOR_ENGINE;
            Log.i(TAG, "Waiting for a voice engine: " + text);
        } else {
            outcome = Outcome.NO_ENGINE;
            Log.w(TAG, "No voice engine for: " + text);
        }
    }

    /** An engine became ready or failed: a sentence held for one is read now if still due. */
    private void onEngineReady() {
        if (pendingText == null || speakingSince > 0L) return;
        if (!SpeechPolicy.readsWhenReady(videoMode, waitingSince, SystemClock.elapsedRealtime())) return;
        waitingSince = 0L;
        speakNow();
    }

    private boolean usesPleos() {
        return pleosReady && pleos != null && !(videoMode && pleosSilent);
    }

    private void speakWithFallback(String text) {
        if (fallbackReady && fallback != null) {
            fallback.speak(
                    text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
            speakingSince = SystemClock.elapsedRealtime();
            outcome = Outcome.ANDROID_SENT;
            Log.d(TAG, "Android: " + text);
        } else {
            outcome = Outcome.NO_ENGINE;
            Log.w(TAG, "No voice engine for: " + text);
        }
    }

    /** Any event from the Pleos engine: it is alive. Runs on the main thread. */
    private void pleosAnswered() {
        pleosAnswered = true;
        silentInARow = 0;
        handler.removeCallbacks(silenceCheck);
    }

    private void onPleosSilent() {
        if (pleosAnswered || pendingText == null) return;
        SpeechPolicy.Silence action =
                SpeechPolicy.onSilence(fallbackReady && fallbackKorean, silentInARow);
        if (action == SpeechPolicy.Silence.KEEP_WAITING) {
            // Stopping it would leave nothing that can read Korean.
            Log.w(TAG, "Pleos TTS gave no sign of speaking; no Korean Android voice to use instead");
            outcome = Outcome.PLEOS_SILENT;
            return;
        }
        silentInARow++;
        if (action == SpeechPolicy.Silence.ANDROID_FROM_NOW_ON) {
            Log.w(TAG, "Pleos TTS silent " + silentInARow + " times; using the Android engine from now on");
            pleosSilent = true;
        } else {
            Log.w(TAG, "Pleos TTS gave no sign of speaking; this sentence goes to the Android engine");
        }
        try {
            if (pleos != null) pleos.stop();
        } catch (RuntimeException error) {
            Log.w(TAG, "Pleos TTS stop failed", error);
        }
        speakWithFallback(pendingText);
    }

    /** A sentence is being read, as far as the engines have said, for at most a few seconds. */
    public boolean isBusy() {
        return pendingText != null
                && speakingSince > 0L
                && SystemClock.elapsedRealtime() - speakingSince < BUSY_MAX_MILLIS;
    }

    /** Which engine the next sentence goes to. */
    public Engine engine() {
        if (usesPleos()) return Engine.PLEOS;
        if (fallbackReady) return fallbackKorean ? Engine.ANDROID : Engine.ANDROID_NO_KOREAN;
        if (pleosState == State.STARTING || fallbackState == State.STARTING) return Engine.STARTING;
        return Engine.NONE;
    }

    /** Whether the Pleos engine runs in hybrid mode, after on-device synthesis failed. */
    public boolean isPleosHybrid() {
        return pleosMode == Mode.HYBRID;
    }

    /** What became of the last sentence. */
    public Outcome lastOutcome() {
        return outcome;
    }

    private void finish() {
        handler.post(() -> {
            Runnable done = onDone;
            onDone = null;
            pendingText = null;
            speakingSince = 0L;
            handler.removeCallbacks(silenceCheck);
            if (done != null) done.run();
        });
    }

    public void stop() {
        boolean speaking = pendingText != null;
        onDone = null;
        pendingText = null;
        speakingSince = 0L;
        waitingSince = 0L;
        handler.removeCallbacks(silenceCheck);
        handler.removeCallbacks(settledSpeak);
        // Video mode: an idle engine gets no stop, so the next sentence goes out at once.
        if (!SpeechPolicy.sendsStop(videoMode, speaking)) return;
        lastStopAt = SystemClock.elapsedRealtime();
        try {
            if (pleosReady && pleos != null) pleos.stop();
        } catch (RuntimeException error) {
            Log.w(TAG, "Pleos TTS stop failed", error);
        }
        if (fallback != null) fallback.stop();
    }

    private void releasePleos() {
        if (pleos == null) return;
        try {
            if (pleosReady) pleos.removeEventListener(pleosEvents);
            pleos.release();
        } catch (RuntimeException error) {
            Log.w(TAG, "Pleos TTS release failed", error);
        }
        pleos = null;
        pleosReady = false;
    }

    public void release() {
        stop();
        releasePleos();
        if (fallback != null) fallback.shutdown();
    }
}
