package io.github.oyuha12.hyundaimotors;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.oyuha12.hyundaimotors.data.FakeDrivingStateSource;
import io.github.oyuha12.hyundaimotors.diagnosis.Detection;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.drowsiness.DrowsinessMonitor;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.RouteNavigator;
import io.github.oyuha12.hyundaimotors.facility.SampleFacilityRepository;
import io.github.oyuha12.hyundaimotors.guidance.AnswerClassifier;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSpeaker;
import io.github.oyuha12.hyundaimotors.guidance.VoiceAnswerListener;
import io.github.oyuha12.hyundaimotors.report.MockEmergencyReporter;
import io.github.oyuha12.hyundaimotors.telemetry.PleosTelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.TelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import java.util.List;

/**
 * Runs diagnosis and guidance, and keeps state across activity recreation on window resize.
 *
 * <p>Flow: a detection or a situation button first asks "is this an emergency?" by voice and on
 * screen. "Yes" opens the situation screen; "no" returns home. A detected condition that goes
 * back to normal also returns home.
 */
public final class MainViewModel extends AndroidViewModel {
    /** An emergency in progress: what it is, why it was raised, and whether it is confirmed. */
    public static final class ActiveEmergency {
        public final EmergencyType type;
        @StringRes public final int reason;
        /** Raised by detection rather than by the user. */
        public final boolean detected;
        /** The user confirmed it, or detection got no answer in time. */
        public final boolean confirmed;

        ActiveEmergency(EmergencyType type, @StringRes int reason, boolean detected, boolean confirmed) {
            this.type = type;
            this.reason = reason;
            this.detected = detected;
            this.confirmed = confirmed;
        }

        ActiveEmergency asConfirmed() {
            return new ActiveEmergency(type, reason, detected, true);
        }
    }

    /** Used only while no position is available, and shown as such on screen. */
    private static final double SAMPLE_LATITUDE = 37.48544722;
    private static final double SAMPLE_LONGITUDE = 127.03636666;

    private static final long MAX_STEP_MILLIS = 10_000L;
    private static final long STEP_PAUSE_MILLIS = 700L;
    /** Starts listening even if the question's "speech finished" event never arrives. */
    private static final long MAX_QUESTION_MILLIS = 8_000L;
    private static final int MAX_QUESTIONS = 2;
    /**
     * A detected emergency with no answer proceeds to guidance after this long: a driver who
     * cannot answer may be the one who needs help most.
     */
    static final int NO_ANSWER_SECONDS = 15;
    /** A detected condition must stay normal this long before the screen closes. */
    private static final long NORMAL_HOLD_MILLIS = 5_000L;

    private final FakeDrivingStateSource drivingState = new FakeDrivingStateSource();
    private final EmergencyDetector detector = new EmergencyDetector();
    private final DrowsinessMonitor drowsiness = new DrowsinessMonitor();
    private final SampleFacilityRepository facilities = new SampleFacilityRepository();
    private final MockEmergencyReporter reporter = new MockEmergencyReporter();
    private final GuidanceSpeaker speaker;
    private final VoiceAnswerListener voice;
    private final RouteNavigator navigator;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<VehicleSnapshot> snapshot = new MutableLiveData<>();
    private final MutableLiveData<ActiveEmergency> emergency = new MutableLiveData<>();
    private final MutableLiveData<Integer> stepIndex = new MutableLiveData<>(0);
    private final MutableLiveData<MockEmergencyReporter.Report> lastReport = new MutableLiveData<>();
    /** Voice status on the confirmation screen, as a string resource, or 0. */
    private final MutableLiveData<Integer> voiceStatus = new MutableLiveData<>(0);
    /** Seconds until a detected emergency proceeds without an answer, or -1. */
    private final MutableLiveData<Integer> countdown = new MutableLiveData<>(-1);
    /** Drowsiness reminder text on the home screen, as a string resource, or 0. */
    private final MutableLiveData<Integer> drowsinessAlert = new MutableLiveData<>(0);

    private TelemetrySource vehicleSource;
    @Nullable private TelemetrySource simulation;
    @Nullable private EmergencyType dismissed;
    private final Runnable stepTimeout = this::advanceStep;
    private final Runnable countdownTick = this::tickCountdown;
    private List<GuidanceStep> steps;
    private int questionsAsked;
    /** Increases with every new question, so late voice callbacks can be ignored. */
    private int questionId;
    private long normalSince = -1L;

    @IdRes
    private int selectedDestination = R.id.nav_home;

    public MainViewModel(@NonNull Application application) {
        super(application);
        speaker = new GuidanceSpeaker(application);
        voice = new VoiceAnswerListener(application);
        navigator = new RouteNavigator(application);
        startVehicleSource();
    }

    private void startVehicleSource() {
        vehicleSource = new PleosTelemetrySource(getApplication());
        vehicleSource.start(reading -> {
            if (simulation == null) onReading(reading);
        });
    }

    /** Permissions take effect on a fresh connection. */
    public void onPermissionsChanged() {
        vehicleSource.stop();
        startVehicleSource();
    }

    private void onReading(VehicleSnapshot reading) {
        snapshot.setValue(reading);
        Detection detected = detector.evaluate(reading);
        ActiveEmergency current = emergency.getValue();

        if (current != null) {
            checkBackToNormal(current, reading, detected);
            return;
        }
        checkDrowsiness(reading);
        if (detected == null) {
            dismissed = null;
            return;
        }
        if (detected.type != dismissed) {
            askToConfirm(new ActiveEmergency(detected.type, detected.reason, true, false));
        }
    }

    // ---- Confirmation -------------------------------------------------------------------------

    /** A situation button was pressed. Asks first, like a detection does. */
    public void selectEmergency(EmergencyType type) {
        stopSimulation();
        askToConfirm(new ActiveEmergency(type, R.string.reason_manual, false, false));
    }

    private void askToConfirm(ActiveEmergency pending) {
        stopAllSpeech();
        emergency.setValue(pending);
        lastReport.setValue(null);
        navigator.clearStatus();
        drowsinessAlert.setValue(0);
        normalSince = -1L;
        questionsAsked = 0;
        String situation = getApplication().getString(EmergencyPlan.title(pending.type));
        ask(getApplication().getString(
                pending.detected ? R.string.confirm_prompt_detected : R.string.confirm_prompt_manual,
                situation));
        if (pending.detected) {
            countdown.setValue(NO_ANSWER_SECONDS);
            handler.postDelayed(countdownTick, 1_000L);
        } else {
            countdown.setValue(-1);
        }
    }

    private void ask(String question) {
        int id = ++questionId;
        questionsAsked++;
        voiceStatus.setValue(R.string.voice_asking);
        Runnable listen = () -> listenForAnswer(id);
        speaker.speak(question, () -> handler.post(listen));
        handler.postDelayed(listen, MAX_QUESTION_MILLIS);
    }

    private void listenForAnswer(int id) {
        if (id != questionId || !isAwaitingAnswer()) return;
        questionId++; // Ignore the other trigger for this question.
        int listenId = questionId;
        voiceStatus.setValue(R.string.voice_listening);
        voice.listen(new VoiceAnswerListener.Callback() {
            @Override
            public void onHeard(String text) {
                if (listenId == questionId && isAwaitingAnswer()) onAnswer(text);
            }

            @Override
            public void onUnavailable() {
                if (listenId == questionId && isAwaitingAnswer()) {
                    voiceStatus.setValue(R.string.voice_unavailable);
                }
            }
        });
    }

    private void onAnswer(String text) {
        switch (AnswerClassifier.classify(text)) {
            case YES:
                confirmEmergency();
                break;
            case NO:
                declineEmergency();
                break;
            case UNCLEAR:
            default:
                if (questionsAsked < MAX_QUESTIONS) {
                    ask(getApplication().getString(R.string.confirm_reprompt));
                } else {
                    voiceStatus.setValue(R.string.voice_not_understood);
                }
                break;
        }
    }

    private boolean isAwaitingAnswer() {
        ActiveEmergency current = emergency.getValue();
        return current != null && !current.confirmed;
    }

    private void tickCountdown() {
        Integer left = countdown.getValue();
        if (!isAwaitingAnswer() || left == null || left < 0) return;
        if (left <= 1) {
            confirmEmergency();
            return;
        }
        countdown.setValue(left - 1);
        handler.postDelayed(countdownTick, 1_000L);
    }

    /** "Yes", by voice or touch, or no answer to a detection in time. */
    public void confirmEmergency() {
        ActiveEmergency current = emergency.getValue();
        if (current == null || current.confirmed) return;
        stopAllSpeech();
        emergency.setValue(current.asConfirmed());
        steps = EmergencyPlan.steps(current.type);
        speakStep(0);
    }

    /** "No", by voice or touch. Returns to the home screen. */
    public void declineEmergency() {
        closeEmergency(R.string.confirm_declined);
    }

    // ---- Guidance -----------------------------------------------------------------------------

    private void speakStep(int index) {
        handler.removeCallbacks(stepTimeout);
        stepIndex.setValue(index);
        String text = getApplication().getString(steps.get(index).text);
        speaker.speak(text, () -> {
            handler.removeCallbacks(stepTimeout);
            handler.postDelayed(stepTimeout, STEP_PAUSE_MILLIS);
        });
        handler.postDelayed(stepTimeout, MAX_STEP_MILLIS);
    }

    private void advanceStep() {
        Integer index = stepIndex.getValue();
        ActiveEmergency current = emergency.getValue();
        if (current == null || !current.confirmed) return;
        if (steps == null || index == null || index + 1 >= steps.size()) return;
        speakStep(index + 1);
    }

    public void repeatGuidance() {
        ActiveEmergency current = emergency.getValue();
        if (steps == null || current == null || !current.confirmed) return;
        speaker.stop();
        speakStep(0);
    }

    /** "상황 종료" on the situation screen. */
    public void dismissEmergency() {
        closeEmergency(0);
    }

    private void checkBackToNormal(ActiveEmergency current, VehicleSnapshot reading, @Nullable Detection detected) {
        if (!current.detected || !detector.isBackToNormal(current.type, reading, detected)) {
            normalSince = -1L;
            return;
        }
        if (normalSince < 0) normalSince = reading.timestampMillis;
        if (reading.timestampMillis - normalSince >= NORMAL_HOLD_MILLIS) {
            closeEmergency(R.string.back_to_normal);
        }
    }

    /** Returns to the home screen, optionally saying why. */
    private void closeEmergency(@StringRes int announcement) {
        ActiveEmergency current = emergency.getValue();
        stopAllSpeech();
        dismissed = current != null && current.detected ? current.type : null;
        emergency.setValue(null);
        lastReport.setValue(null);
        normalSince = -1L;
        stopSimulation();
        if (announcement != 0) speaker.speak(getApplication().getString(announcement), null);
    }

    private void stopAllSpeech() {
        questionId++;
        handler.removeCallbacksAndMessages(null);
        speaker.stop();
        voice.stop();
        voiceStatus.setValue(0);
        countdown.setValue(-1);
    }

    // ---- Drowsiness reminder (side feature) ---------------------------------------------------

    private void checkDrowsiness(VehicleSnapshot reading) {
        DrowsinessMonitor.Alert alert = drowsiness.update(reading);
        if (alert == null) return;
        int message = alert == DrowsinessMonitor.Alert.MONOTONOUS_DRIVING
                ? R.string.drowsy_monotonous
                : R.string.drowsy_long_driving;
        drowsinessAlert.setValue(message);
        speaker.speak(getApplication().getString(message), null);
    }

    public void dismissDrowsinessAlert() {
        drowsinessAlert.setValue(0);
    }

    // ---- Mock report and demos ----------------------------------------------------------------

    /** TEST ONLY: records a report in memory. Nothing leaves the app. */
    public void submitMockReport() {
        ActiveEmergency active = emergency.getValue();
        if (active == null) return;
        VehicleSnapshot reading = snapshot.getValue();
        lastReport.setValue(reporter.submit(
                active.type,
                System.currentTimeMillis(),
                reading == null ? null : reading.latitude,
                reading == null ? null : reading.longitude,
                reading == null ? null : reading.speedKph));
    }

    public void startScenario(SimulatedTelemetrySource.Scenario scenario) {
        stopSimulation();
        detector.reset();
        dismissed = null;
        if (scenario == SimulatedTelemetrySource.Scenario.MONOTONOUS_DRIVE) {
            drowsiness.setConfig(DrowsinessMonitor.DEMO);
        }
        simulation = new SimulatedTelemetrySource(scenario);
        simulation.start(this::onReading);
    }

    /** Ends a running demo, for example the drowsiness one, which never ends by itself. */
    public void stopScenario() {
        stopSimulation();
        drowsinessAlert.setValue(0);
    }

    private void stopSimulation() {
        if (simulation != null) {
            simulation.stop();
            simulation = null;
            detector.reset();
            drowsiness.setConfig(DrowsinessMonitor.DEFAULT);
        }
    }

    // ---- Facilities and navigation ------------------------------------------------------------

    public boolean hasRealLocation() {
        VehicleSnapshot reading = snapshot.getValue();
        return reading != null && reading.hasLocation();
    }

    private double latitude() {
        return hasRealLocation() ? snapshot.getValue().latitude : SAMPLE_LATITUDE;
    }

    private double longitude() {
        return hasRealLocation() ? snapshot.getValue().longitude : SAMPLE_LONGITUDE;
    }

    public List<Facility> nearbyFacilities() {
        return facilities.findNearby(latitude(), longitude());
    }

    @Nullable
    public Facility evacuationTarget(EmergencyType type) {
        return facilities.nearest(EmergencyPlan.evacuationTarget(type), latitude(), longitude());
    }

    public List<Facility> supportFacilities(EmergencyType type) {
        return facilities.nearestOfEach(EmergencyPlan.supportTypes(type), latitude(), longitude());
    }

    public void navigateTo(Facility facility) {
        navigator.navigateTo(facility);
    }

    // ---- Observables --------------------------------------------------------------------------

    public LiveData<RouteNavigator.Status> getRouteStatus() {
        return navigator.getStatus();
    }

    public LiveData<VehicleSnapshot> getSnapshot() {
        return snapshot;
    }

    public LiveData<ActiveEmergency> getEmergency() {
        return emergency;
    }

    public LiveData<Integer> getStepIndex() {
        return stepIndex;
    }

    public LiveData<MockEmergencyReporter.Report> getLastReport() {
        return lastReport;
    }

    public LiveData<Integer> getVoiceStatus() {
        return voiceStatus;
    }

    public LiveData<Integer> getCountdown() {
        return countdown;
    }

    public LiveData<Integer> getDrowsinessAlert() {
        return drowsinessAlert;
    }

    public int getMockReportCount() {
        return reporter.getReports().size();
    }

    public LiveData<Boolean> isDriving() {
        return drivingState.isDriving();
    }

    public void toggleDriving() {
        drivingState.toggle();
    }

    @IdRes
    public int getSelectedDestination() {
        return selectedDestination;
    }

    public void setSelectedDestination(@IdRes int destination) {
        selectedDestination = destination;
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacksAndMessages(null);
        if (simulation != null) simulation.stop();
        vehicleSource.stop();
        speaker.release();
        voice.release();
        navigator.release();
    }
}
