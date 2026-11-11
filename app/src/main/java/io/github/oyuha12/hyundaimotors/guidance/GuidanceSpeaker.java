package io.github.oyuha12.hyundaimotors.guidance;

import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.constant.Mode;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.listener.OnInitializeListener;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Reads guidance aloud with the Pleos on-device engine, and falls back to the Android engine
 * when the Pleos one is not available. On-device mode works without a network connection.
 */
public final class GuidanceSpeaker {
    private static final String TAG = "GuidanceSpeaker";
    private static final String UTTERANCE_ID = "guidance";

    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private TextToSpeech pleos;
    private boolean pleosReady;
    @Nullable private android.speech.tts.TextToSpeech fallback;
    private boolean fallbackReady;
    @Nullable private Runnable onDone;
    @Nullable private String pendingText;

    private final EventListener pleosEvents = new EventListener() {
        @Override public void onStart() {}

        @Override public void onReady() {}

        @Override public void onUpdatedRms(double rms) {}

        @Override public void onStop() {}

        @Override
        public void onDone() {
            finish();
        }

        @Override
        public void onError(ErrorType error) {
            Log.w(TAG, "Pleos TTS error: " + error);
            handler.post(() -> {
                pleosReady = false;
                if (pendingText != null) speakWithFallback(pendingText);
            });
        }
    };

    public GuidanceSpeaker(Context context) {
        Context appContext = context.getApplicationContext();
        startFallback(appContext);
        try {
            pleos = new TextToSpeech(appContext, Mode.ON_DEVICE);
            pleos.initialize(new OnInitializeListener() {
                @Override
                public void onSuccess() {
                    handler.post(() -> {
                        pleos.addEventListener(pleosEvents);
                        pleos.setLocale(Locale.KOREAN);
                        pleosReady = true;
                    });
                }

                @Override
                public void onFailure(ErrorType error) {
                    Log.w(TAG, "Pleos TTS unavailable: " + error);
                }
            });
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Pleos TTS unavailable", error);
        }
    }

    private void startFallback(Context context) {
        fallback = new android.speech.tts.TextToSpeech(context, status -> {
            if (status != android.speech.tts.TextToSpeech.SUCCESS || fallback == null) return;
            fallback.setLanguage(Locale.KOREAN);
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
        });
    }

    /** Speaks text. onDone may not run if no engine works, so callers need their own timeout. */
    public void speak(String text, @Nullable Runnable onDone) {
        this.onDone = onDone;
        pendingText = text;
        if (pleosReady && pleos != null) {
            pleos.speak(text, true);
        } else {
            speakWithFallback(text);
        }
    }

    private void speakWithFallback(String text) {
        if (fallbackReady && fallback != null) {
            fallback.speak(
                    text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
        }
    }

    private void finish() {
        handler.post(() -> {
            Runnable done = onDone;
            onDone = null;
            pendingText = null;
            if (done != null) done.run();
        });
    }

    public void stop() {
        onDone = null;
        pendingText = null;
        if (pleosReady && pleos != null) pleos.stop();
        if (fallback != null) fallback.stop();
    }

    public void release() {
        stop();
        if (pleos != null) {
            try {
                if (pleosReady) pleos.removeEventListener(pleosEvents);
                pleos.release();
            } catch (RuntimeException error) {
                Log.w(TAG, "Pleos TTS release failed", error);
            }
        }
        if (fallback != null) fallback.shutdown();
    }
}
