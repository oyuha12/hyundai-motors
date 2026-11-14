package io.github.oyuha12.hyundaimotors.guidance;

import ai.pleos.playground.stt.SpeechToText;
import ai.pleos.playground.stt.constant.ErrorType;
import ai.pleos.playground.stt.constant.Mode;
import ai.pleos.playground.stt.listener.OnInitializeListener;
import ai.pleos.playground.stt.listener.ResultListener;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Listens for one short spoken reply. Uses the Pleos on-device recognizer through the vehicle
 * microphone, then the Android recognizer, and reports "unavailable" when neither works so the
 * screen can rely on its buttons.
 */
public final class VoiceAnswerListener {
    public interface Callback {
        /** The recognized reply. Empty when the user said nothing recognizable. */
        void onHeard(String text);

        void onUnavailable();
    }

    private static final String TAG = "VoiceAnswer";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private SpeechToText pleos;
    private boolean pleosReady;
    @Nullable private SpeechRecognizer fallback;
    @Nullable private Callback pending;
    /** Video mode: {@link #stop()} leaves the recognizer alone while it is not listening. */
    private boolean quietWhenIdle;

    private final ResultListener pleosResults = new ResultListener() {
        @Override public void onReady() {}

        @Override public void onStartedRecognition() {}

        @Override public void onUpdatedEpdData(long on, long off) {}

        @Override public void onUpdatedRms(double rms) {}

        @Override
        public void onUpdated(String text, boolean completed) {
            if (completed) handler.post(() -> deliver(text == null ? "" : text));
        }

        @Override
        public void onEndedRecognition() {
            // A final result arrives through onUpdated first; this covers silence.
            handler.postDelayed(() -> deliver(""), 300);
        }

        @Override
        public void onError(ErrorType error) {
            Log.w(TAG, "Pleos STT error: " + error);
            handler.post(() -> {
                pleosReady = false;
                if (pending != null) listenWithFallback();
            });
        }
    };

    private final RecognitionListener fallbackResults = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}

        @Override public void onBeginningOfSpeech() {}

        @Override public void onRmsChanged(float rmsdB) {}

        @Override public void onBufferReceived(byte[] buffer) {}

        @Override public void onEndOfSpeech() {}

        @Override public void onPartialResults(Bundle partialResults) {}

        @Override public void onEvent(int eventType, Bundle params) {}

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> texts =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            deliver(texts == null || texts.isEmpty() ? "" : texts.get(0));
        }

        @Override
        public void onError(int error) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH
                    || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                deliver("");
            } else {
                Log.w(TAG, "Android STT error: " + error);
                Callback callback = pending;
                pending = null;
                if (callback != null) callback.onUnavailable();
            }
        }
    };

    public VoiceAnswerListener(Context context) {
        this.context = context.getApplicationContext();
        try {
            pleos = new SpeechToText(this.context, Mode.ON_DEVICE);
            pleos.initialize(new OnInitializeListener() {
                @Override
                public void onSuccess() {
                    handler.post(() -> {
                        pleos.addListener(pleosResults);
                        pleos.setLocale(Locale.KOREAN);
                        pleosReady = true;
                    });
                }

                @Override
                public void onFailure(ErrorType error) {
                    Log.w(TAG, "Pleos STT unavailable: " + error);
                }
            });
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Pleos STT unavailable", error);
        }
    }

    /** Starts listening for one reply. A newer call replaces an older one. */
    public void listen(Callback callback) {
        stop();
        pending = callback;
        if (pleosReady && pleos != null) {
            try {
                pleos.request();
                return;
            } catch (RuntimeException error) {
                Log.w(TAG, "Pleos STT request failed", error);
            }
        }
        listenWithFallback();
    }

    private void listenWithFallback() {
        boolean micAllowed = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (!micAllowed || !SpeechRecognizer.isRecognitionAvailable(context)) {
            Callback callback = pending;
            pending = null;
            if (callback != null) callback.onUnavailable();
            return;
        }
        if (fallback == null) {
            fallback = SpeechRecognizer.createSpeechRecognizer(context);
            fallback.setRecognitionListener(fallbackResults);
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        fallback.startListening(intent);
    }

    private void deliver(String text) {
        Callback callback = pending;
        pending = null;
        if (callback != null) callback.onHeard(text);
    }

    /**
     * Video mode: no stop goes to an idle recognizer, so it cannot reach the speech service
     * just as the next guidance sentence starts. Off: as before.
     */
    public void setQuietWhenIdle(boolean on) {
        quietWhenIdle = on;
    }

    public void stop() {
        boolean listening = pending != null;
        pending = null;
        handler.removeCallbacksAndMessages(null);
        if (quietWhenIdle && !listening) return;
        try {
            if (pleosReady && pleos != null) pleos.stop();
        } catch (RuntimeException error) {
            Log.w(TAG, "Pleos STT stop failed", error);
        }
        if (fallback != null) fallback.cancel();
    }

    public void release() {
        stop();
        if (pleos != null) {
            try {
                if (pleosReady) pleos.removeListener(pleosResults);
                pleos.release();
            } catch (RuntimeException error) {
                Log.w(TAG, "Pleos STT release failed", error);
            }
        }
        if (fallback != null) fallback.destroy();
    }
}
