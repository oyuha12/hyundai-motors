package io.github.oyuha12.hyundaimotors;

import ai.pleos.playground.navi.data.TBTInfo;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.oyuha12.hyundaimotors.data.FakeDrivingStateSource;
import io.github.oyuha12.hyundaimotors.demo.AutoRoute;
import io.github.oyuha12.hyundaimotors.demo.HomeDemo;
import io.github.oyuha12.hyundaimotors.demo.VideoConfig;
import io.github.oyuha12.hyundaimotors.demo.VideoMode;
import io.github.oyuha12.hyundaimotors.demo.VideoPlace;
import io.github.oyuha12.hyundaimotors.demo.VideoWording;
import io.github.oyuha12.hyundaimotors.diagnosis.Detection;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.drowsiness.DrivingWarningMonitor;
import io.github.oyuha12.hyundaimotors.drowsiness.DrowsinessMonitor;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.ResponseEstimator;
import io.github.oyuha12.hyundaimotors.facility.RouteNavigator;
import io.github.oyuha12.hyundaimotors.facility.FacilityRepository;
import io.github.oyuha12.hyundaimotors.facility.SampleFacilityRepository;
import io.github.oyuha12.hyundaimotors.facility.StatusPlace;
import io.github.oyuha12.hyundaimotors.guidance.AnswerClassifier;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSequencer;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSpeaker;
import io.github.oyuha12.hyundaimotors.guidance.KoreanParticle;
import io.github.oyuha12.hyundaimotors.guidance.VoiceAnswerListener;
import io.github.oyuha12.hyundaimotors.report.EventRecorder;
import io.github.oyuha12.hyundaimotors.report.MockEmergencyReporter;
import io.github.oyuha12.hyundaimotors.report.ReportFlow;
import io.github.oyuha12.hyundaimotors.report.ReportReceipt;
import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;
import io.github.oyuha12.hyundaimotors.telemetry.PleosTelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.TelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs diagnosis and guidance, and keeps state across activity recreation on window resize.
 *
 * <p>Flow: a detection or a situation button first asks "is this an emergency?" by voice and on
 * screen. "Yes" opens the situation screen; "no" returns home. A detected condition that goes
 * back to normal also returns home.
 *
 * <p>Guidance steps follow one another as they are read, but a step that waits for the car to
 * stop holds the next one until the speed is below 1 km/h ({@link GuidanceSequencer}).
 *
 * <p>Reporting takes one touch of the SOS button, or one phrase after the last step offers it
 * ({@link ReportFlow}). Reports are recorded in MockEmergencyReporter and reach no one.
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

    private static final long MAX_STEP_MILLIS = 10_000L;
    /**
     * A timer that would cut off a sentence the engine is still reading waits for it, checking
     * every READING_CHECK_MILLIS, up to MAX_READING_MILLIS after the sentence started. On a slow
     * device (a PC emulator) synthesis can start seconds late, and cutting it off sounded choppy.
     */
    private static final long MAX_READING_MILLIS = 20_000L;
    private static final long READING_CHECK_MILLIS = 500L;
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
    /** Starts listening even if the report offer's "speech finished" event never arrives. */
    private static final long REPORT_PROMPT_MAX_MILLIS = 10_000L;
    /** Carries on even if the report announcement's "speech finished" event never arrives. */
    private static final long REPORT_ANNOUNCE_MAX_MILLIS = 15_000L;
    /** How long a recognized request shows before the report is made. */
    private static final long HEARD_BUBBLE_MILLIS = 1_500L;

    private final FakeDrivingStateSource drivingState = new FakeDrivingStateSource();
    private final EmergencyDetector detector = new EmergencyDetector();
    private final DrowsinessMonitor drowsiness = new DrowsinessMonitor();
    private final DrivingWarningMonitor drivingWarnings = new DrivingWarningMonitor();
    private final EventRecorder eventRecorder = new EventRecorder();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SampleFacilityRepository sampleFacilities = new SampleFacilityRepository();
    /** The real places of the demo video. Places still marked TODO are left out. */
    private final FacilityRepository videoFacilities = VideoPlace.repositoryOf(VideoConfig.PLACES);
    private final MockEmergencyReporter reporter = new MockEmergencyReporter();
    private final GuidanceSpeaker speaker;
    private final VoiceAnswerListener voice;
    private final RouteNavigator navigator;
    private final GuidanceSequencer sequencer;
    private final ReportFlow reportFlow;
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
    /** Drowsiness or driver mistake message on the home screen, as a string resource, or 0. */
    private final MutableLiveData<Integer> drowsinessAlert = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> eventRecordCount = new MutableLiveData<>(0);
    private final MutableLiveData<Facility> demoRouteRequest = new MutableLiveData<>();
    /** After a step that waits for the car to stop, until it has. */
    private final MutableLiveData<Boolean> waitingForStop = new MutableLiveData<>(false);
    /** The receipt of this situation's report, or null. */
    private final MutableLiveData<ReportReceipt> reportReceipt = new MutableLiveData<>();
    /** A recognized report request, shown like a speech bubble, or null. */
    private final MutableLiveData<String> heardReport = new MutableLiveData<>();
    /** Listening for a spoken report, as a string resource, or 0. */
    private final MutableLiveData<Integer> reportVoiceStatus = new MutableLiveData<>(0);
    /** The evacuation and dispatch status screen, shown once a report has been announced. */
    private final MutableLiveData<Boolean> evacuationStatusShown = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> videoMode = new MutableLiveData<>(false);
    /** Shown driving state: the manual toggle, or in video mode the (simulated) speed. */
    private final MediatorLiveData<Boolean> driving = new MediatorLiveData<>();
    private boolean drivingBySpeed;
    /** The location SDK's last fix. Simulations in video mode keep the car here. */
    @Nullable private GeoPoint lastSdkPosition;

    private TelemetrySource vehicleSource;
    @Nullable private SimulatedTelemetrySource simulation;
    /** The car's own last reading, shown again when a simulation ends. */
    @Nullable private VehicleSnapshot lastVehicleReading;
    /** Home demo: the situation to ask about once its values have built up (HomeDemo). */
    @Nullable private EmergencyType buildUpType;
    private final Runnable buildUpQuestion = this::askAfterBuildUp;
    @Nullable private EmergencyType dismissed;
    private final Runnable stepTimeout = this::onStepTimeout;
    private final Runnable countdownTick = this::tickCountdown;
    private final Runnable reportPromptTimeout = this::onReportPromptTimeout;
    private final Runnable reportAnnounceTimeout = this::onReportAnnounceTimeout;
    private final Runnable announcedReport = this::fileAnnouncedReport;
    /** Read just before the first step, then cleared. */
    @Nullable private String guidanceLead;
    private int questionsAsked;
    /** Increases with every new question, so late voice callbacks can be ignored. */
    private int questionId;
    private long normalSince = -1L;
    private final MutableLiveData<Map<String, Long>> dispatches =
            new MutableLiveData<>(Collections.emptyMap());

    @IdRes
    private int selectedDestination = R.id.nav_home;

    public MainViewModel(@NonNull Application application) {
        super(application);
        speaker = new GuidanceSpeaker(application);
        voice = new VoiceAnswerListener(application);
        navigator = new RouteNavigator(application);
        sequencer = new GuidanceSequencer(new GuidanceSequencer.Listener() {
            @Override
            public void onStep(int index, GuidanceStep step) {
                speakStep(index, step);
                // A video scenario pulls over once the guidance asks the driver to stop.
                if (step.waitsForStop && simulation != null) simulation.startStopping();
            }

            @Override
            public void onWaitingForStop(boolean waiting) {
                waitingForStop.setValue(waiting);
            }

            @Override
            public void onFinished() {
                reportFlow.onGuidanceFinished();
            }
        });
        reportFlow = new ReportFlow(new ReportFlow.Listener() {
            @Override
            public void onPrompt() {
                // Listening starts only once this has been read.
                speaker.speak(getApplication().getString(R.string.report_prompt),
                        MainViewModel.this::onReportPromptSpoken);
                handler.postDelayed(reportPromptTimeout, REPORT_PROMPT_MAX_MILLIS);
            }

            @Override
            public void onListen() {
                listenForReport();
            }

            @Override
            public void onReport(ReportFlow.Trigger trigger, @Nullable String heard) {
                beginReport(trigger, heard);
            }

            @Override
            public void onReportFinished() {
                evacuationStatusShown.setValue(true);
                autoRoute(AutoRoute.AFTER_REPORT);
            }
        });
        videoMode.setValue(demoPrefs().getBoolean(KEY_VIDEO_MODE, false));
        speaker.setVideoMode(isVideoMode());
        voice.setQuietWhenIdle(isVideoMode());
        driving.addSource(drivingState.isDriving(), value -> refreshDriving());
        driving.addSource(videoMode, value -> refreshDriving());
        io.execute(this::countEventRecords);
        startVehicleSource();
    }

    private void startVehicleSource() {
        vehicleSource = new PleosTelemetrySource(getApplication());
        vehicleSource.start(reading -> {
            // Kept even while a simulation runs, so in video mode the simulated car stays where
            // the SDK says.
            GeoPoint position = GeoPoint.of(reading);
            if (position != null) lastSdkPosition = position;
            lastVehicleReading = reading;
            if (simulation == null) onReading(reading);
        });
    }

    /** Permissions take effect on a fresh connection. */
    public void onPermissionsChanged() {
        vehicleSource.stop();
        startVehicleSource();
        // Video mode asks for the TTS permission too; the Pleos voice can start once it is granted.
        if (isVideoMode()) speaker.retryPleos();
    }

    private void onReading(VehicleSnapshot reading) {
        snapshot.setValue(reading);
        eventRecorder.add(reading);
        sequencer.onSpeed(reading.speedKph);
        drivingBySpeed = VideoMode.isDrivingBySpeed(reading.speedKph);
        boolean video = isVideoMode();
        if (video) refreshDriving();
        // Home demo build-up: the values are staged, so no detection or reminder answers them.
        // The button asks once, after HomeDemo.BUILD_UP_MILLIS.
        if (isBuildUpRunning()) return;
        Detection detected = detector.evaluate(reading);
        ActiveEmergency current = emergency.getValue();

        if (current != null) {
            // Video mode: the screen stays until "상황 종료", even after the car stops.
            if (!video) checkBackToNormal(current, reading, detected);
            return;
        }
        // Video mode: no drowsiness or driver mistake reminders from the scripted data.
        if (!video) checkDrivingAlerts(reading);
        if (detected == null) {
            dismissed = null;
            return;
        }
        if (detected.type != dismissed) {
            askToConfirm(new ActiveEmergency(detected.type, detected.reason, true, false));
        }
    }

    // ---- Confirmation -------------------------------------------------------------------------

    /**
     * A situation button was pressed. Asks first, like a detection does. Outside video mode the
     * 급발진 and 화재 buttons first play the values building up (HomeDemo), then ask.
     */
    public void selectEmergency(EmergencyType type) {
        SimulatedTelemetrySource.Scenario buildUp = HomeDemo.buildUp(type, isVideoMode());
        if (buildUp != null) {
            startBuildUp(type, buildUp);
            return;
        }
        // The cruise goes on under the question; it pulls over once the guidance starts.
        if (!isCruising()) stopSimulation();
        askToConfirm(new ActiveEmergency(type, R.string.reason_manual, false, false));
    }

    private void startBuildUp(EmergencyType type, SimulatedTelemetrySource.Scenario scenario) {
        float speed = currentSpeed();
        stopAllSpeech(); // Also drops an earlier build-up still waiting to ask.
        sequencer.stop();
        startSimulation(scenario, 0L, speed);
        buildUpType = type;
        handler.postDelayed(buildUpQuestion, HomeDemo.BUILD_UP_MILLIS);
    }

    private void askAfterBuildUp() {
        EmergencyType type = buildUpType;
        buildUpType = null;
        if (type == null || emergency.getValue() != null) return;
        // The staged values keep going under the question and the guidance.
        askToConfirm(new ActiveEmergency(type, HomeDemo.reason(type), false, false));
    }

    private void askToConfirm(ActiveEmergency pending) {
        stopAllSpeech();
        sequencer.stop();
        if (VideoMode.skipsConfirmation(
                isVideoMode(), pending.detected, isVideoSkipConfirmation())) {
            // Video mode: "급발진이 의심됩니다." and straight into the guidance.
            clearSituation();
            saveEventRecord(pending);
            startGuidance(pending.asConfirmed(), getApplication().getString(
                    R.string.suspected_announcement, suspectedSubject(pending.type)));
            return;
        }
        emergency.setValue(pending);
        clearSituation();
        questionsAsked = 0;
        ask(pending.detected
                ? getApplication().getString(
                        R.string.confirm_prompt_detected, suspectedSubject(pending.type))
                // A situation button: "급발진이 감지되었습니다." The particle follows the name.
                : getApplication().getString(
                        R.string.confirm_prompt_manual, suspectedSubject(pending.type)));
        if (pending.detected) {
            saveEventRecord(pending);
            countdown.setValue(NO_ANSWER_SECONDS);
            handler.postDelayed(countdownTick, 1_000L);
        } else {
            countdown.setValue(-1);
        }
    }

    /** Clears what the previous situation left on screen. */
    private void clearSituation() {
        lastReport.setValue(null);
        dispatches.setValue(Collections.emptyMap());
        navigator.clearStatus();
        drowsinessAlert.setValue(0);
        normalSince = -1L;
        clearReport();
    }

    private void clearReport() {
        reportFlow.reset();
        reportReceipt.setValue(null);
        heardReport.setValue(null);
        reportVoiceStatus.setValue(0);
        evacuationStatusShown.setValue(false);
    }

    /** "급발진이", "차량 화재가": the name in "○○이(가) 의심됩니다". */
    private String suspectedSubject(EmergencyType type) {
        return KoreanParticle.subject(getApplication().getString(EmergencyPlan.title(type)));
    }

    private void ask(String question) {
        int id = ++questionId;
        questionsAsked++;
        voiceStatus.setValue(R.string.voice_asking);
        Runnable listen = () -> listenForAnswer(id);
        speaker.speak(question, () -> handler.post(listen));
        // Never opens the microphone over the question while it is still being read.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (id != questionId) return;
                if (speaker.isReading(MAX_READING_MILLIS)) {
                    handler.postDelayed(this, READING_CHECK_MILLIS);
                } else {
                    listen.run();
                }
            }
        }, MAX_QUESTION_MILLIS);
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
        if (!current.detected) saveEventRecord(current);
        startGuidance(current.asConfirmed(), null);
    }

    /**
     * Shows the situation screen and starts reading its steps.
     *
     * @param lead read just before the first step, in the same utterance
     */
    private void startGuidance(ActiveEmergency confirmed, @Nullable String lead) {
        emergency.setValue(confirmed);
        if (EmergencyPlan.reportsOnConfirm(confirmed.type)) {
            // Read out by the steps instead, so nothing is announced here.
            fileReport(false);
            reportFlow.markReported();
        }
        guidanceLead = lead;
        // Home demo: a cruising car pulls over once a situation is confirmed.
        if (isCruising()) simulation.startStopping();
        sequencer.start(EmergencyPlan.steps(confirmed.type));
        autoRoute(AutoRoute.ON_CONFIRM);
    }

    /** "No", by voice or touch. Returns to the home screen. */
    public void declineEmergency() {
        closeEmergency(R.string.confirm_declined);
    }

    // ---- Guidance -----------------------------------------------------------------------------

    private void speakStep(int index, GuidanceStep step) {
        handler.removeCallbacks(stepTimeout);
        stepIndex.setValue(index);
        String text = getApplication().getString(VideoWording.pick(step.text, isVideoMode()));
        if (guidanceLead != null) {
            text = guidanceLead + " " + text;
            guidanceLead = null;
        }
        speaker.speak(text, () -> {
            handler.removeCallbacks(stepTimeout);
            handler.postDelayed(stepTimeout, STEP_PAUSE_MILLIS);
        });
        handler.postDelayed(stepTimeout, MAX_STEP_MILLIS);
    }

    /** The step's time is up: the next step, unless the voice is still reading this one. */
    private void onStepTimeout() {
        if (waitWhileReading(stepTimeout)) return;
        advanceStep();
    }

    private void onReportPromptTimeout() {
        if (waitWhileReading(reportPromptTimeout)) return;
        onReportPromptSpoken();
    }

    private void onReportAnnounceTimeout() {
        if (waitWhileReading(reportAnnounceTimeout)) return;
        onReportAnnounced();
    }

    /** Checks again shortly, instead of cutting off a sentence still being read. */
    private boolean waitWhileReading(Runnable timer) {
        if (!speaker.isReading(MAX_READING_MILLIS)) return false;
        handler.removeCallbacks(timer);
        handler.postDelayed(timer, READING_CHECK_MILLIS);
        return true;
    }

    /** The step was read: the next one, or first wait for the car to stop. */
    private void advanceStep() {
        ActiveEmergency current = emergency.getValue();
        if (current == null || !current.confirmed) return;
        sequencer.onStepSpoken();
    }

    public void repeatGuidance() {
        ActiveEmergency current = emergency.getValue();
        if (current == null || !current.confirmed) return;
        speaker.stop();
        voice.stop();
        handler.removeCallbacks(reportPromptTimeout);
        reportVoiceStatus.setValue(0);
        reportFlow.onGuidanceRestarted();
        sequencer.start(EmergencyPlan.steps(current.type));
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
        sequencer.stop();
        stepIndex.setValue(0);
        clearReport();
        // Video mode: the next run starts with no destination left in the navigation app.
        if (isVideoMode()) navigator.endRoute();
        emergency.setValue(null);
        lastReport.setValue(null);
        dispatches.setValue(Collections.emptyMap());
        normalSince = -1L;
        float speed = currentSpeed();
        stopSimulation();
        continueAfterSimulation(speed);
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

    /** Drowsiness reminders and driver mistake warnings: voice and a small card, never a takeover. */
    private void checkDrivingAlerts(VehicleSnapshot reading) {
        int message = 0;
        DrivingWarningMonitor.Warning warning = drivingWarnings.update(reading);
        if (warning != null) {
            switch (warning) {
                case PEDAL_MISAPPLICATION:
                    message = R.string.warn_pedal_misapplication;
                    break;
                case FAST_REVERSE:
                    message = R.string.warn_fast_reverse;
                    break;
                case CURVE_OVERSPEED:
                default:
                    message = R.string.warn_curve_overspeed;
                    break;
            }
        }
        DrowsinessMonitor.Alert alert = drowsiness.update(reading);
        if (message == 0 && alert != null) {
            message = alert == DrowsinessMonitor.Alert.MONOTONOUS_DRIVING
                    ? R.string.drowsy_monotonous
                    : R.string.drowsy_long_driving;
        }
        if (message == 0) return;
        drowsinessAlert.setValue(message);
        speaker.speak(getApplication().getString(message), null);
    }

    // ---- Event records (simplified EDR) -------------------------------------------------------

    private void saveEventRecord(ActiveEmergency active) {
        long now = System.currentTimeMillis();
        String json = eventRecorder.toJson(
                active.type, active.detected, getApplication().getString(active.reason), now);
        File file = new File(eventRecordDir(), EventRecorder.fileName(active.type, now));
        io.execute(() -> {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException error) {
                Log.w("EventRecorder", "Could not save event record", error);
            }
            countEventRecords();
        });
    }

    private File eventRecordDir() {
        File dir = new File(getApplication().getFilesDir(), "event_records");
        if (!dir.exists() && !dir.mkdirs()) Log.w("EventRecorder", "Could not create " + dir);
        return dir;
    }

    private void countEventRecords() {
        File[] files = eventRecordDir().listFiles((dir, name) -> name.endsWith(".json"));
        eventRecordCount.postValue(files == null ? 0 : files.length);
    }

    public LiveData<Integer> getEventRecordCount() {
        return eventRecordCount;
    }

    public void dismissDrowsinessAlert() {
        drowsinessAlert.setValue(0);
    }

    // ---- Mock report and demos ----------------------------------------------------------------

    /** The SOS button: reports at once, without a confirmation popup. Nothing leaves the app. */
    public void onSosPressed() {
        ActiveEmergency current = emergency.getValue();
        if (current == null || !current.confirmed) return;
        reportFlow.onButton();
    }

    /**
     * Video mode stand-in for saying "신고해 줘" when the emulator microphone does not work: a
     * double tap while a report is awaited, with the menu option on.
     */
    public void onStagedVoiceGesture() {
        if (!VideoMode.allowsStagedVoiceReport(isVideoMode(), isVideoStagedVoiceReport())) return;
        reportFlow.onStagedVoice();
    }

    private void onReportPromptSpoken() {
        handler.removeCallbacks(reportPromptTimeout);
        reportFlow.onPromptSpoken();
    }

    private void listenForReport() {
        reportVoiceStatus.setValue(R.string.report_voice_listening);
        voice.listen(new VoiceAnswerListener.Callback() {
            @Override
            public void onHeard(String text) {
                if (!reportFlow.isAwaiting()) return;
                if (!reportFlow.onHeard(text)) {
                    reportVoiceStatus.setValue(R.string.report_voice_not_understood);
                }
            }

            @Override
            public void onUnavailable() {
                if (reportFlow.isAwaiting()) reportVoiceStatus.setValue(R.string.voice_unavailable);
            }
        });
    }

    /**
     * A report was asked for. The guidance holds so the announcement is heard in full. A spoken
     * request shows what was heard for a moment first.
     */
    private void beginReport(ReportFlow.Trigger trigger, @Nullable String heard) {
        handler.removeCallbacks(reportPromptTimeout);
        handler.removeCallbacks(stepTimeout);
        voice.stop();
        reportVoiceStatus.setValue(0);
        sequencer.pause();
        if (trigger == ReportFlow.Trigger.BUTTON) {
            fileReport(true);
            return;
        }
        heardReport.setValue(heard != null
                ? heard
                : getApplication().getString(R.string.report_staged_phrase));
        handler.postDelayed(announcedReport, HEARD_BUBBLE_MILLIS);
    }

    private void fileAnnouncedReport() {
        fileReport(true);
    }

    /**
     * TEST ONLY: records the report in memory and shows its receipt. Nothing leaves the app.
     *
     * @param announce also read "119와 112에 신고를 접수했습니다…" aloud
     */
    private void fileReport(boolean announce) {
        ActiveEmergency active = emergency.getValue();
        if (active == null) return;
        long now = System.currentTimeMillis();
        VehicleSnapshot reading = snapshot.getValue();
        Float speed = reading == null ? null : reading.speedKph;
        MockEmergencyReporter.Report report = reporter.submit(
                active.type,
                now,
                reading == null ? null : reading.latitude,
                reading == null ? null : reading.longitude,
                speed);
        // 112 and 119 send police, fire and ambulance at once; their countdowns start now.
        for (StatusPlace responder : responderStatus(active.type)) {
            if (responder.facility != null) dispatch(active.type, responder.facility);
        }
        lastReport.setValue(report);
        reportReceipt.setValue(new ReportReceipt(
                active.type, now, locationLabel(reading), eventRecorder.maxSpeedKph(), speed));
        if (!announce) return;
        speaker.speak(
                getApplication().getString(
                        VideoWording.pick(R.string.report_announcement, isVideoMode())),
                this::onReportAnnounced);
        handler.postDelayed(reportAnnounceTimeout, REPORT_ANNOUNCE_MAX_MILLIS);
    }

    /** The announcement was read: the guidance carries on where it stopped. */
    private void onReportAnnounced() {
        handler.removeCallbacks(reportAnnounceTimeout);
        reportFlow.onAnnounced();
        sequencer.resume();
    }

    public void startScenario(SimulatedTelemetrySource.Scenario scenario) {
        startSimulation(scenario, 0L);
    }

    /** Video mode: plays a scenario after the chosen lead-in of normal driving. */
    public void startVideoScenario(SimulatedTelemetrySource.Scenario scenario) {
        startSimulation(scenario, getVideoLeadInSeconds() * 1000L);
    }

    private void startSimulation(SimulatedTelemetrySource.Scenario scenario, long leadInMillis) {
        startSimulation(scenario, leadInMillis, 0f);
    }

    private void startSimulation(
            SimulatedTelemetrySource.Scenario scenario, long leadInMillis, float baseSpeedKph) {
        stopSimulation();
        detector.reset();
        dismissed = null;
        drivingWarnings.reset();
        if (scenario == SimulatedTelemetrySource.Scenario.MONOTONOUS_DRIVE) {
            drowsiness.setConfig(DrowsinessMonitor.DEMO);
        }
        simulation = new SimulatedTelemetrySource(scenario, leadInMillis,
                () -> VideoMode.simulationPosition(isVideoMode(), lastSdkPosition), baseSpeedKph);
        simulation.start(this::onReading);
    }

    /** Ends a running demo, for example the drowsiness one, which never ends by itself. */
    public void stopScenario() {
        endSimulation();
        drowsinessAlert.setValue(0);
    }

    /**
     * Ends a simulation. While "주행 중" is on outside video mode the cruise takes over from the
     * current speed; otherwise the car's own reading shows again instead of the last staged one.
     */
    private void endSimulation() {
        float speed = currentSpeed();
        stopSimulation();
        continueAfterSimulation(speed);
    }

    /** After a simulation stopped: the cruise from that speed, or the car's own reading. */
    private void continueAfterSimulation(float speed) {
        if (shouldCruise()) {
            startSimulation(SimulatedTelemetrySource.Scenario.DEMO_CRUISE, 0L, speed);
        } else {
            // Posted: this can run inside onReading, which should finish first.
            handler.post(this::showVehicleReading);
        }
    }

    private void showVehicleReading() {
        if (simulation == null && lastVehicleReading != null) onReading(lastVehicleReading);
    }

    /** Home demo: "주행으로 전환" is on, outside video mode. */
    private boolean shouldCruise() {
        return !isVideoMode() && Boolean.TRUE.equals(drivingState.isDriving().getValue());
    }

    private boolean isCruising() {
        return simulation != null
                && simulation.scenario() == SimulatedTelemetrySource.Scenario.DEMO_CRUISE;
    }

    private boolean isBuildUpRunning() {
        return simulation != null && HomeDemo.isBuildUp(simulation.scenario());
    }

    private float currentSpeed() {
        VehicleSnapshot reading = snapshot.getValue();
        return reading != null && reading.speedKph != null ? reading.speedKph : 0f;
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

    /** False when the demo location is on, so screens say they use the sample location. */
    public boolean hasRealLocation() {
        if (usesDemoLocation()) return false;
        VehicleSnapshot reading = snapshot.getValue();
        return reading != null && reading.hasLocation();
    }

    /** Whether facility distances use the fixed demo position. Never in video mode. */
    public boolean usesDemoLocation() {
        return VideoMode.usesDemoLocation(isVideoMode(), isDemoLocationEnabled());
    }

    /** Distances are measured from the SDK position, or from the fallback before it arrives. */
    private double latitude() {
        return hasRealLocation()
                ? snapshot.getValue().latitude
                : VideoMode.fallbackPosition(isVideoMode()).latitude;
    }

    private double longitude() {
        return hasRealLocation()
                ? snapshot.getValue().longitude
                : VideoMode.fallbackPosition(isVideoMode()).longitude;
    }

    /** Video mode uses the video's real places, otherwise the sample facilities. */
    private FacilityRepository facilities() {
        return isVideoMode() ? videoFacilities : sampleFacilities;
    }

    /** Where the car is, as the home screen shows it: a place name in video mode. */
    public String locationLabel(@Nullable VehicleSnapshot reading) {
        if (isVideoMode()) {
            return VideoConfig.FILMING.name != null
                    ? VideoConfig.FILMING.name
                    : getApplication().getString(R.string.video_screen_location_unset);
        }
        if (usesDemoLocation()) return getApplication().getString(R.string.demo_location_label);
        return reading != null && reading.hasLocation()
                ? String.format(Locale.US, "%.4f, %.4f", reading.latitude, reading.longitude)
                : getApplication().getString(R.string.value_unknown);
    }

    public List<Facility> nearbyFacilities() {
        return facilities().findNearby(latitude(), longitude());
    }

    /** Where to go: in video mode the situation's safe stop place, or null where it has none. */
    @Nullable
    public Facility evacuationTarget(EmergencyType type) {
        if (!isVideoMode()) {
            return facilities().nearest(evacuationType(type), latitude(), longitude());
        }
        VideoPlace place = VideoConfig.safeStopFor(type);
        if (place == null || place.type == null) return null;
        return VideoPlace.repositoryOf(Collections.singletonList(place))
                .nearest(place.type, latitude(), longitude());
    }

    /** The video has a safe stop place ("안전 정차 장소") for each scenario. */
    private Facility.Type evacuationType(EmergencyType type) {
        return isVideoMode() ? Facility.Type.SAFE_STOP : EmergencyPlan.evacuationTarget(type);
    }

    /** The evacuation line of the status screen, also when its place is not set. */
    public StatusPlace evacuationStatus(EmergencyType type) {
        return new StatusPlace(evacuationType(type), evacuationTarget(type));
    }

    /**
     * Who 112 and 119 send: police, fire station and, unless the video leaves it out, hospital
     * (ambulance). Places that are not set keep their line.
     */
    public List<StatusPlace> responderStatus(EmergencyType type) {
        List<Facility.Type> types = isVideoMode() && !VideoConfig.SHOW_HOSPITAL
                ? Arrays.asList(Facility.Type.POLICE, Facility.Type.FIRE_STATION)
                : Arrays.asList(
                        Facility.Type.POLICE, Facility.Type.FIRE_STATION, Facility.Type.HOSPITAL);
        return StatusPlace.listFor(facilities(), types, latitude(), longitude());
    }

    /** Facilities that can help, responders first and soonest arrival first. */
    public List<Facility> supportFacilities(EmergencyType type) {
        List<Facility> result = new ArrayList<>(
                facilities().nearestOfEach(EmergencyPlan.supportTypes(type), latitude(), longitude()));
        result.sort(Comparator.comparingLong(facility -> {
            Long arrival = ResponseEstimator.arrivalMillis(facility);
            return arrival == null ? Long.MAX_VALUE : arrival;
        }));
        return result;
    }

    /**
     * TEST ONLY: records a report addressed to one facility and starts its arrival countdown.
     * Nothing leaves the app.
     */
    public void requestDispatch(Facility facility) {
        ActiveEmergency active = emergency.getValue();
        if (active == null) return;
        MockEmergencyReporter.Report report = dispatch(active.type, facility);
        if (report != null) lastReport.setValue(report);
    }

    /**
     * TEST ONLY: records a report addressed to one facility and starts its arrival countdown
     * from now. Nothing leaves the app.
     *
     * @return the record, or null if the place sends nobody or was asked already
     */
    @Nullable
    private MockEmergencyReporter.Report dispatch(EmergencyType type, Facility facility) {
        Long arrival = ResponseEstimator.arrivalMillis(facility);
        if (arrival == null) return null;
        Map<String, Long> current = dispatches.getValue();
        if (current != null && current.containsKey(facility.name)) return null;
        VehicleSnapshot reading = snapshot.getValue();
        MockEmergencyReporter.Report report = reporter.submit(
                type,
                System.currentTimeMillis(),
                reading == null ? null : reading.latitude,
                reading == null ? null : reading.longitude,
                reading == null ? null : reading.speedKph,
                facility.name);
        Map<String, Long> updated = current == null ? new HashMap<>() : new HashMap<>(current);
        updated.put(facility.name, SystemClock.elapsedRealtime() + arrival);
        dispatches.setValue(updated);
        return report;
    }

    /** Shows the evacuation and dispatch status again, after a report. */
    public void openEvacuationStatus() {
        if (reportFlow.isReported()) evacuationStatusShown.setValue(true);
    }

    /** Back to the guidance from the status screen. */
    public void closeEvacuationStatus() {
        evacuationStatusShown.setValue(false);
    }

    /** Facility name to expected arrival, on the SystemClock.elapsedRealtime() clock. */
    public LiveData<Map<String, Long>> getDispatches() {
        return dispatches;
    }

    /** Sends a route to the navigation app, or shows the in-app demo route when that is on. */
    public void navigateTo(Facility facility) {
        if (isVideoMode()) {
            // Always the Pleos navigation app through NaviHelper. The in-app route can show on
            // top, for an emulator whose navigation app cannot draw maps.
            navigator.navigateTo(facility);
            if (VideoMode.showsFallbackRoute(true, isVideoFallbackRoute())) {
                demoRouteRequest.setValue(facility);
            }
            return;
        }
        if (isDemoRouteEnabled()) {
            demoRouteRequest.setValue(facility);
        } else {
            navigator.navigateTo(facility);
        }
    }

    /** Video mode: sends the evacuation route by itself at the moment chosen in the menu. */
    private void autoRoute(AutoRoute moment) {
        ActiveEmergency active = emergency.getValue();
        if (active == null
                || !VideoMode.autoRoutesAt(isVideoMode(), getVideoAutoRoute(), moment)) {
            return;
        }
        Facility target = evacuationTarget(active.type);
        if (target == null) {
            navigator.reportNoDestination();
        } else {
            navigateTo(target);
        }
    }

    /**
     * Police and fire stations to pin on the in-app route screen, in video mode only. The
     * destination itself is left out.
     */
    public List<Facility> routePins(Facility destination) {
        List<Facility> pins = new ArrayList<>();
        if (!isVideoMode()) return pins;
        for (Facility facility : facilities().nearestOfEach(
                Arrays.asList(Facility.Type.POLICE, Facility.Type.FIRE_STATION),
                latitude(), longitude())) {
            if (!facility.name.equals(destination.name)) pins.add(facility);
        }
        return pins;
    }

    // ---- Demo settings for PC emulator videos --------------------------------------------------

    private static final String DEMO_PREFS = "demo";
    private static final String KEY_DEMO_ROUTE = "demo_route";
    private static final String KEY_DEMO_LOCATION = "demo_location";

    private SharedPreferences demoPrefs() {
        return getApplication().getSharedPreferences(DEMO_PREFS, Context.MODE_PRIVATE);
    }

    /** In-app route playback instead of the vehicle's navigation app. On by default for demos. */
    public boolean isDemoRouteEnabled() {
        return demoPrefs().getBoolean(KEY_DEMO_ROUTE, true);
    }

    public void setDemoRouteEnabled(boolean enabled) {
        demoPrefs().edit().putBoolean(KEY_DEMO_ROUTE, enabled).apply();
    }

    /** A fixed sample location in Seoul instead of the emulator's default location abroad. */
    public boolean isDemoLocationEnabled() {
        return demoPrefs().getBoolean(KEY_DEMO_LOCATION, true);
    }

    public void setDemoLocationEnabled(boolean enabled) {
        demoPrefs().edit().putBoolean(KEY_DEMO_LOCATION, enabled).apply();
    }

    /** A facility to show in the demo route screen. Cleared once shown. */
    public LiveData<Facility> getDemoRouteRequest() {
        return demoRouteRequest;
    }

    public void onDemoRouteShown() {
        demoRouteRequest.setValue(null);
    }

    /**
     * Speaks a short message, for example demo route instructions. Video mode: the guidance and
     * report voice go first, and the message is not said while either is talking or listening, or
     * while another sentence is still being read; the route screen tries again a moment later.
     *
     * @return false when the message was held back
     */
    public boolean say(String text) {
        if (isVideoMode()
                && !VideoMode.routeVoiceFree(isGuidanceRunning(), reportFlow.isBusy(), speaker.isBusy())) {
            return false;
        }
        speaker.speak(text, null);
        return true;
    }

    /** The guidance steps of a confirmed situation are still being read or waited on. */
    private boolean isGuidanceRunning() {
        ActiveEmergency current = emergency.getValue();
        return current != null && current.confirmed && !sequencer.isFinished();
    }

    /** Video menu: reads a test sentence with whichever engine works. */
    public void testVoice() {
        speaker.speak(getApplication().getString(R.string.video_voice_test_phrase), null);
    }

    /** Which voice engine reads, for the video menu. */
    public GuidanceSpeaker.Engine getVoiceEngine() {
        return speaker.engine();
    }

    /** Whether the Pleos voice runs in hybrid mode, for the video menu. */
    public boolean isVoiceHybrid() {
        return speaker.isPleosHybrid();
    }

    /** What became of the last sentence read aloud, for the video menu. */
    public GuidanceSpeaker.Outcome getVoiceOutcome() {
        return speaker.lastOutcome();
    }

    // ---- Video mode for the demo video ---------------------------------------------------------

    private static final String KEY_VIDEO_MODE = "video_mode";
    private static final String KEY_VIDEO_LEAD_IN = "video_lead_in_seconds";
    private static final String KEY_VIDEO_SKIP_CONFIRMATION = "video_skip_confirmation";
    private static final String KEY_VIDEO_STAGED_VOICE = "video_staged_voice_report";
    private static final String KEY_VIDEO_AUTO_ROUTE = "video_auto_route";
    private static final String KEY_VIDEO_FALLBACK_ROUTE = "video_fallback_route";

    public LiveData<Boolean> getVideoMode() {
        return videoMode;
    }

    public boolean isVideoMode() {
        return Boolean.TRUE.equals(videoMode.getValue());
    }

    /** Saved, off by default. Switching either way stops a running demo. */
    public void setVideoMode(boolean on) {
        if (on == isVideoMode()) return;
        demoPrefs().edit().putBoolean(KEY_VIDEO_MODE, on).apply();
        stopScenario();
        videoMode.setValue(on);
        // Video mode plays its own scenarios; the home demo cruise does not carry over.
        if (on) stopSimulation();
        speaker.setVideoMode(on);
        voice.setQuietWhenIdle(on);
    }

    /** Normal driving before a video scenario, one of {@link VideoConfig#LEAD_IN_SECONDS}. */
    public int getVideoLeadInSeconds() {
        return VideoMode.leadInSeconds(
                demoPrefs().getInt(KEY_VIDEO_LEAD_IN, VideoConfig.DEFAULT_LEAD_IN_SECONDS));
    }

    public void setVideoLeadInSeconds(int seconds) {
        demoPrefs().edit().putInt(KEY_VIDEO_LEAD_IN, VideoMode.leadInSeconds(seconds)).apply();
    }

    /** Video mode: a detection goes straight to the guidance, without "네/아니요". On by default. */
    public boolean isVideoSkipConfirmation() {
        return demoPrefs().getBoolean(
                KEY_VIDEO_SKIP_CONFIRMATION, VideoConfig.DEFAULT_SKIP_CONFIRMATION);
    }

    public void setVideoSkipConfirmation(boolean skip) {
        demoPrefs().edit().putBoolean(KEY_VIDEO_SKIP_CONFIRMATION, skip).apply();
    }

    /** Video mode: a double tap stands in for saying "신고해 줘". Off by default. */
    public boolean isVideoStagedVoiceReport() {
        return demoPrefs().getBoolean(
                KEY_VIDEO_STAGED_VOICE, VideoConfig.DEFAULT_STAGED_VOICE_REPORT);
    }

    public void setVideoStagedVoiceReport(boolean on) {
        demoPrefs().edit().putBoolean(KEY_VIDEO_STAGED_VOICE, on).apply();
    }

    /** Video mode: when the evacuation route goes to the navigation app by itself. Off by default. */
    public AutoRoute getVideoAutoRoute() {
        return AutoRoute.fromStored(demoPrefs().getInt(
                KEY_VIDEO_AUTO_ROUTE, VideoConfig.DEFAULT_AUTO_ROUTE.ordinal()));
    }

    public void setVideoAutoRoute(AutoRoute moment) {
        demoPrefs().edit().putInt(KEY_VIDEO_AUTO_ROUTE, moment.ordinal()).apply();
    }

    /** Video mode: the in-app route screen also shows for each route. Off by default. */
    public boolean isVideoFallbackRoute() {
        return demoPrefs().getBoolean(
                KEY_VIDEO_FALLBACK_ROUTE, VideoConfig.DEFAULT_FALLBACK_ROUTE);
    }

    public void setVideoFallbackRoute(boolean on) {
        demoPrefs().edit().putBoolean(KEY_VIDEO_FALLBACK_ROUTE, on).apply();
    }

    private void refreshDriving() {
        boolean value = isVideoMode()
                ? drivingBySpeed
                : Boolean.TRUE.equals(drivingState.isDriving().getValue());
        if (!Boolean.valueOf(value).equals(driving.getValue())) driving.setValue(value);
    }

    // ---- Observables --------------------------------------------------------------------------

    /** The navigation app's answer to our route, as a string resource, or 0. */
    public LiveData<Integer> getRouteMessage() {
        return navigator.getMessage();
    }

    /** The next turn while the navigation app guides our route, or null. */
    public LiveData<TBTInfo> getNextTurn() {
        return navigator.getNextTurn();
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

    public LiveData<Boolean> getWaitingForStop() {
        return waitingForStop;
    }

    public LiveData<ReportReceipt> getReportReceipt() {
        return reportReceipt;
    }

    public LiveData<String> getHeardReport() {
        return heardReport;
    }

    public LiveData<Integer> getReportVoiceStatus() {
        return reportVoiceStatus;
    }

    public LiveData<Boolean> getEvacuationStatusShown() {
        return evacuationStatusShown;
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

    /** The manual toggle, or in video mode whether the (simulated) car is moving. */
    public LiveData<Boolean> isDriving() {
        return driving;
    }

    /**
     * The test toggle. Outside video mode it also plays a cruising speed while driving, and the
     * car's own reading again once parked. A running test scenario is left alone.
     */
    public void toggleDriving() {
        drivingState.toggle();
        if (isVideoMode()) return;
        if (shouldCruise()) {
            if (simulation == null) {
                startSimulation(SimulatedTelemetrySource.Scenario.DEMO_CRUISE, 0L, currentSpeed());
            }
        } else if (isCruising()) {
            stopSimulation();
            showVehicleReading();
        }
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
        io.shutdown();
        voice.release();
        navigator.release();
    }
}
