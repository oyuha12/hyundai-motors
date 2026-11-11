package io.github.oyuha12.hyundaimotors;

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
import io.github.oyuha12.hyundaimotors.demo.VideoConfig;
import io.github.oyuha12.hyundaimotors.demo.VideoMode;
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
import io.github.oyuha12.hyundaimotors.facility.SampleFacilityRepository;
import io.github.oyuha12.hyundaimotors.guidance.AnswerClassifier;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSequencer;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSpeaker;
import io.github.oyuha12.hyundaimotors.guidance.KoreanParticle;
import io.github.oyuha12.hyundaimotors.guidance.VoiceAnswerListener;
import io.github.oyuha12.hyundaimotors.report.EventRecorder;
import io.github.oyuha12.hyundaimotors.report.MockEmergencyReporter;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
    private final DrivingWarningMonitor drivingWarnings = new DrivingWarningMonitor();
    private final EventRecorder eventRecorder = new EventRecorder();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SampleFacilityRepository facilities = new SampleFacilityRepository();
    private final MockEmergencyReporter reporter = new MockEmergencyReporter();
    private final GuidanceSpeaker speaker;
    private final VoiceAnswerListener voice;
    private final RouteNavigator navigator;
    private final GuidanceSequencer sequencer;
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
    private final MutableLiveData<Boolean> videoMode = new MutableLiveData<>(false);
    /** Shown driving state: the manual toggle, or in video mode the (simulated) speed. */
    private final MediatorLiveData<Boolean> driving = new MediatorLiveData<>();
    private boolean drivingBySpeed;
    /** The location SDK's last fix. Simulations in video mode keep the car here. */
    @Nullable private GeoPoint lastSdkPosition;

    private TelemetrySource vehicleSource;
    @Nullable private SimulatedTelemetrySource simulation;
    @Nullable private EmergencyType dismissed;
    private final Runnable stepTimeout = this::advanceStep;
    private final Runnable countdownTick = this::tickCountdown;
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
        });
        videoMode.setValue(demoPrefs().getBoolean(KEY_VIDEO_MODE, false));
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
        eventRecorder.add(reading);
        sequencer.onSpeed(reading.speedKph);
        drivingBySpeed = VideoMode.isDrivingBySpeed(reading.speedKph);
        boolean video = isVideoMode();
        if (video) refreshDriving();
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

    /** A situation button was pressed. Asks first, like a detection does. */
    public void selectEmergency(EmergencyType type) {
        stopSimulation();
        askToConfirm(new ActiveEmergency(type, R.string.reason_manual, false, false));
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
                : getApplication().getString(R.string.confirm_prompt_manual,
                        getApplication().getString(EmergencyPlan.title(pending.type))));
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
    }

    /** "급발진이", "차량 화재가": the name in "○○이(가) 의심됩니다". */
    private String suspectedSubject(EmergencyType type) {
        return KoreanParticle.subject(
                getApplication().getString(EmergencyPlan.suspectedName(type)));
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
        if (EmergencyPlan.reportsOnConfirm(confirmed.type)) submitMockReport();
        guidanceLead = lead;
        sequencer.start(EmergencyPlan.steps(confirmed.type));
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
        emergency.setValue(null);
        lastReport.setValue(null);
        dispatches.setValue(Collections.emptyMap());
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
        startSimulation(scenario, 0L);
    }

    /** Video mode: plays a scenario after the chosen lead-in of normal driving. */
    public void startVideoScenario(SimulatedTelemetrySource.Scenario scenario) {
        startSimulation(scenario, getVideoLeadInSeconds() * 1000L);
    }

    private void startSimulation(SimulatedTelemetrySource.Scenario scenario, long leadInMillis) {
        stopSimulation();
        detector.reset();
        dismissed = null;
        drivingWarnings.reset();
        if (scenario == SimulatedTelemetrySource.Scenario.MONOTONOUS_DRIVE) {
            drowsiness.setConfig(DrowsinessMonitor.DEMO);
        }
        simulation = new SimulatedTelemetrySource(scenario, leadInMillis,
                () -> VideoMode.simulationPosition(isVideoMode(), lastSdkPosition));
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

    private double latitude() {
        return hasRealLocation()
                ? snapshot.getValue().latitude
                : VideoConfig.FALLBACK_POSITION.latitude;
    }

    private double longitude() {
        return hasRealLocation()
                ? snapshot.getValue().longitude
                : VideoConfig.FALLBACK_POSITION.longitude;
    }

    public List<Facility> nearbyFacilities() {
        return facilities.findNearby(latitude(), longitude());
    }

    @Nullable
    public Facility evacuationTarget(EmergencyType type) {
        return facilities.nearest(EmergencyPlan.evacuationTarget(type), latitude(), longitude());
    }

    /** Facilities that can help, responders first and soonest arrival first. */
    public List<Facility> supportFacilities(EmergencyType type) {
        List<Facility> result = new ArrayList<>(
                facilities.nearestOfEach(EmergencyPlan.supportTypes(type), latitude(), longitude()));
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
        Long arrival = ResponseEstimator.arrivalMillis(facility);
        if (active == null || arrival == null) return;
        Map<String, Long> current = dispatches.getValue();
        if (current != null && current.containsKey(facility.name)) return;
        VehicleSnapshot reading = snapshot.getValue();
        lastReport.setValue(reporter.submit(
                active.type,
                System.currentTimeMillis(),
                reading == null ? null : reading.latitude,
                reading == null ? null : reading.longitude,
                reading == null ? null : reading.speedKph,
                facility.name));
        Map<String, Long> updated = current == null ? new HashMap<>() : new HashMap<>(current);
        updated.put(facility.name, SystemClock.elapsedRealtime() + arrival);
        dispatches.setValue(updated);
    }

    /** Facility name to expected arrival, on the SystemClock.elapsedRealtime() clock. */
    public LiveData<Map<String, Long>> getDispatches() {
        return dispatches;
    }

    /** Sends a route to the navigation app, or shows the in-app demo route when that is on. */
    public void navigateTo(Facility facility) {
        if (isDemoRouteEnabled()) {
            demoRouteRequest.setValue(facility);
        } else {
            navigator.navigateTo(facility);
        }
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

    /** Speaks a short message, for example demo route instructions. */
    public void say(String text) {
        speaker.speak(text, null);
    }

    // ---- Video mode for the demo video ---------------------------------------------------------

    private static final String KEY_VIDEO_MODE = "video_mode";
    private static final String KEY_VIDEO_LEAD_IN = "video_lead_in_seconds";
    private static final String KEY_VIDEO_SKIP_CONFIRMATION = "video_skip_confirmation";

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

    private void refreshDriving() {
        boolean value = isVideoMode()
                ? drivingBySpeed
                : Boolean.TRUE.equals(drivingState.isDriving().getValue());
        if (!Boolean.valueOf(value).equals(driving.getValue())) driving.setValue(value);
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

    public LiveData<Boolean> getWaitingForStop() {
        return waitingForStop;
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
        io.shutdown();
        voice.release();
        navigator.release();
    }
}
