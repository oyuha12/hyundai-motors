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
 * <p>Logcat tag "GuidanceSpeaker" says which engine started, which one read each sentence, and
 * why the Pleos one could not be used. {@link #engine()} gives the same for the screen.
 */
public final class GuidanceSpeaker {
    /** Which engine reads, as far as the app can tell. */
    public enum Engine {
        /** Neither engine has answered yet. */
        STARTING,
        /** The Pleos on-device engine. */
        PLEOS,
        /** The Android engine, because the Pleos one is not available. */
        ANDROID,
        /** The Android engine, but it has no Korean voice. */
        ANDROID_NO_KOREAN,
        /** Neither engine works. */
        NONE
    }

    private enum State { STARTING, READY, FAILED }

    private static final String TAG = "GuidanceSpeaker";
    private static final String UTTERANCE_ID = "guidance";
    /** With the silence check on: a Pleos sentence with no sign of life after this is given up. */
    private static final long PLEOS_SILENCE_MILLIS = 3_500L;
    /** {@link #isBusy()} gives up on a sentence that never reports its end after this long. */
    private static final long BUSY_MAX_MILLIS = 12_000L;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private TextToSpeech pleos;
    private boolean pleosReady;
    private State pleosState = State.STARTING;
    /** The Pleos engine took a sentence and never answered: see {@link #setSilenceFallback}. */
    private boolean pleosSilent;
    private boolean silenceFallback;
    private boolean pleosAnswered;
    @Nullable private android.speech.tts.TextToSpeech fallback;
    private boolean fallbackReady;
    private State fallbackState = State.STARTING;
    private boolean fallbackKorean;
    @Nullable private Runnable onDone;
    @Nullable private String pendingText;
    /** When the sentence in {@link #pendingText} went to an engine; 0 when none took it. */
    private long speakingSince;

    private final Runnable silenceCheck = this::onPleosSilent;

    private final EventListener pleosEvents = new EventListener() {
        @Override
        public void onStart() {
            pleosAnswered();
        }

        @Override
        public void onReady() {
            pleosAnswered();
        }

        @Override
        public void onUpdatedRms(double rms) {
            pleosAnswered();
        }

        @Override
        public void onStop() {
            pleosAnswered();
        }

        @Override
        public void onDone() {
            pleosAnswered();
            finish();
        }

        @Override
        public void onError(ErrorType error) {
            Log.w(TAG, "Pleos TTS error: " + error);
            handler.post(() -> {
                handler.removeCallbacks(silenceCheck);
                pleosReady = false;
                pleosState = State.FAILED;
                if (pendingText != null) speakWithFallback(pendingText);
            });
        }
    };

    public GuidanceSpeaker(Context context) {
        appContext = context.getApplicationContext();
        startFallback(appContext);
        startPleos();
    }

    private void startPleos() {
        pleosState = State.STARTING;
        try {
            TextToSpeech engine = new TextToSpeech(appContext, Mode.ON_DEVICE);
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
                        Log.i(TAG, "Pleos TTS ready (on-device)");
                    });
                }

                @Override
                public void onFailure(ErrorType error) {
                    Log.w(TAG, "Pleos TTS unavailable: " + error);
                    handler.post(() -> {
                        if (pleos == engine) pleosState = State.FAILED;
                    });
                }
            });
        } catch (RuntimeException | LinkageError error) {
            // A missing pleos.car.permission.TTS_SERVICE ends here as a SecurityException.
            Log.w(TAG, "Pleos TTS unavailable", error);
            pleosState = State.FAILED;
        }
    }

    /**
     * Tries the Pleos engine again if it did not start, for example after the TTS permission
     * was granted. Does nothing while it works.
     */
    public void retryPleos() {
        if (pleosState != State.FAILED) return;
        Log.i(TAG, "Retrying Pleos TTS");
        releasePleos();
        startPleos();
    }

    private void startFallback(Context context) {
        fallback = new android.speech.tts.TextToSpeech(context, status -> {
            if (status != android.speech.tts.TextToSpeech.SUCCESS || fallback == null) {
                Log.w(TAG, "Android TTS unavailable: " + status);
                fallbackState = State.FAILED;
                return;
            }
            int language = fallback.setLanguage(Locale.KOREAN);
            fallbackKorean = language >= android.speech.tts.TextToSpeech.LANG_AVAILABLE;
            if (!fallbackKorean) Log.w(TAG, "Android TTS has no Korean voice: " + language);
            fallback.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    finish();
                }

                @Override
                public void onError(String utteranceId) {
                    finish();
                }
            });
            fallbackReady = true;
            fallbackState = State.READY;
            Log.i(TAG, "Android TTS ready, engine " + fallback.getDefaultEngine());
        });
    }

    /**
     * Video mode: when the Pleos engine takes a sentence and gives no sign of reading it, stop
     * it, read the sentence with the Android engine, and keep using that one. Off: as before.
     */
    public void setSilenceFallback(boolean on) {
        silenceFallback = on;
        if (!on) handler.removeCallbacks(silenceCheck);
    }

    /** Speaks text. onDone may not run if no engine works, so callers need their own timeout. */
    public void speak(String text, @Nullable Runnable onDone) {
        this.onDone = onDone;
        pendingText = text;
        speakingSince = 0L;
        handler.removeCallbacks(silenceCheck);
        if (usesPleos()) {
            try {
                pleosAnswered = false;
                pleos.speak(text, true);
                speakingSince = SystemClock.elapsedRealtime();
                Log.d(TAG, "Pleos: " + text);
                if (silenceFallback) handler.postDelayed(silenceCheck, PLEOS_SILENCE_MILLIS);
                return;
            } catch (RuntimeException error) {
                Log.w(TAG, "Pleos TTS speak failed", error);
                pleosReady = false;
                pleosState = State.FAILED;
            }
        }
        speakWithFallback(text);
    }

    private boolean usesPleos() {
        return pleosReady && pleos != null && !(silenceFallback && pleosSilent);
    }

    private void speakWithFallback(String text) {
        if (fallbackReady && fallback != null) {
            fallback.speak(
                    text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
            speakingSince = SystemClock.elapsedRealtime();
            Log.d(TAG, "Android: " + text);
        } else {
            Log.w(TAG, "No voice engine for: " + text);
        }
    }

    private void pleosAnswered() {
        handler.post(() -> {
            pleosAnswered = true;
            handler.removeCallbacks(silenceCheck);
        });
    }

    private void onPleosSilent() {
        if (pleosAnswered || pendingText == null) return;
        Log.w(TAG, "Pleos TTS gave no sign of speaking; using the Android engine from now on");
        pleosSilent = true;
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
        onDone = null;
        pendingText = null;
        speakingSince = 0L;
        handler.removeCallbacks(silenceCheck);
        if (pleosReady && pleos != null) pleos.stop();
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
