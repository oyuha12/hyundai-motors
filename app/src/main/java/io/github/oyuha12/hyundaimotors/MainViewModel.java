package io.github.oyuha12.hyundaimotors;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.oyuha12.hyundaimotors.data.FakeDrivingStateSource;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceCatalog;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.RouteNavigator;
import io.github.oyuha12.hyundaimotors.facility.SampleFacilityRepository;
import io.github.oyuha12.hyundaimotors.guidance.GuidanceSpeaker;
import io.github.oyuha12.hyundaimotors.report.MockEmergencyReporter;
import io.github.oyuha12.hyundaimotors.telemetry.PleosTelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.TelemetrySource;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import java.util.List;

/** Runs diagnosis and guidance, and keeps state across activity recreation on window resize. */
public final class MainViewModel extends AndroidViewModel {
    /** Used only while no position is available, and shown as such on screen. */
    private static final double SAMPLE_LATITUDE = 37.48544722;
    private static final double SAMPLE_LONGITUDE = 127.03636666;

    private static final long MAX_STEP_MILLIS = 10_000L;
    private static final long STEP_PAUSE_MILLIS = 700L;

    private final FakeDrivingStateSource drivingState = new FakeDrivingStateSource();
    private final EmergencyDetector detector = new EmergencyDetector();
    private final SampleFacilityRepository facilities = new SampleFacilityRepository();
    private final MockEmergencyReporter reporter = new MockEmergencyReporter();
    private final GuidanceSpeaker speaker;
    private final RouteNavigator navigator;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<VehicleSnapshot> snapshot = new MutableLiveData<>();
    private final MutableLiveData<EmergencyType> emergency = new MutableLiveData<>();
    private final MutableLiveData<Integer> stepIndex = new MutableLiveData<>(0);
    private final MutableLiveData<MockEmergencyReporter.Report> lastReport = new MutableLiveData<>();
    private final MutableLiveData<Boolean> simulating = new MutableLiveData<>(false);

    private TelemetrySource vehicleSource;
    @Nullable private TelemetrySource simulation;
    @Nullable private EmergencyType dismissed;
    private final Runnable stepTimeout = this::advanceStep;
    private List<GuidanceStep> steps;

    @IdRes
    private int selectedDestination = R.id.nav_home;

    public MainViewModel(@NonNull Application application) {
        super(application);
        speaker = new GuidanceSpeaker(application);
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
        EmergencyType detected = detector.evaluate(reading);
        if (detected == null) {
            dismissed = null;
            return;
        }
        EmergencyType current = emergency.getValue();
        boolean isNew = current == null && detected != dismissed;
        boolean isEscalation = current != null && detected.critical && !current.critical;
        if (isNew || isEscalation) activate(detected);
    }

    private void activate(EmergencyType type) {
        emergency.setValue(type);
        lastReport.setValue(null);
        navigator.clearStatus();
        playGuidance(type);
    }

    private void playGuidance(EmergencyType type) {
        steps = GuidanceCatalog.stepsFor(type);
        speakStep(0);
    }

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
        if (steps == null || index == null || index + 1 >= steps.size()) return;
        speakStep(index + 1);
    }

    public void repeatGuidance() {
        EmergencyType type = emergency.getValue();
        if (type == null) return;
        speaker.stop();
        playGuidance(type);
    }

    public void dismissEmergency() {
        handler.removeCallbacks(stepTimeout);
        speaker.stop();
        dismissed = emergency.getValue();
        emergency.setValue(null);
        lastReport.setValue(null);
        stopSimulation();
    }

    /** TEST ONLY: records a report in memory. Nothing leaves the app. */
    public void submitMockReport() {
        EmergencyType type = emergency.getValue();
        VehicleSnapshot reading = snapshot.getValue();
        if (type == null) return;
        lastReport.setValue(reporter.submit(
                type,
                System.currentTimeMillis(),
                reading == null ? null : reading.latitude,
                reading == null ? null : reading.longitude,
                reading == null ? null : reading.speedKph));
    }

    public void startScenario(SimulatedTelemetrySource.Scenario scenario) {
        stopSimulation();
        detector.reset();
        dismissed = null;
        simulating.setValue(true);
        simulation = new SimulatedTelemetrySource(scenario);
        simulation.start(this::onReading);
    }

    private void stopSimulation() {
        if (simulation != null) {
            simulation.stop();
            simulation = null;
            detector.reset();
        }
        simulating.setValue(false);
    }

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
    public Facility recommendedFacility() {
        EmergencyType type = emergency.getValue();
        return type == null ? null : facilities.recommendedFor(type, latitude(), longitude());
    }

    public void navigateTo(Facility facility) {
        navigator.navigateTo(facility);
    }

    public LiveData<RouteNavigator.Status> getRouteStatus() {
        return navigator.getStatus();
    }

    public LiveData<VehicleSnapshot> getSnapshot() {
        return snapshot;
    }

    public LiveData<EmergencyType> getEmergency() {
        return emergency;
    }

    public LiveData<Integer> getStepIndex() {
        return stepIndex;
    }

    public LiveData<MockEmergencyReporter.Report> getLastReport() {
        return lastReport;
    }

    public LiveData<Boolean> isSimulating() {
        return simulating;
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
        navigator.release();
    }
}
