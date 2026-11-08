package io.github.oyuha12.hyundaimotors;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationBarView;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.RouteNavigator;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The layout follows the window size, not device orientation: on Pleos Connect the user can
 * move and resize the app window, and two apps can share the application area.
 * layout-w840dp holds the two-pane version.
 */
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST = 1;
    private static final String[] RUNTIME_PERMISSIONS = {
        "android.car.permission.CAR_SPEED",
        "pleos.car.permission.FUSED_LOCATION",
        // Only for the fallback recognizer when the Pleos one is unavailable.
        "android.permission.RECORD_AUDIO",
    };

    private MainViewModel viewModel;
    private EmergencyOverlay overlay;
    private boolean screenVisible;
    private boolean driving;
    private TextView drivingChip;
    private MaterialButton toggleDriving;
    private TextView coreCaption;
    private TextView coreValue;
    private ImageView statusIcon;
    private TextView tileSpeed;
    private TextView tileGear;
    private TextView tileBrake;
    private TextView detailLocation;
    private TextView detailCabin;
    private TextView detailReports;
    private View detailPane; // Only present in wide windows.
    private View homeContent;
    private View nearbyContent;
    private View testContent;
    private View testControls;
    private TextView testNotice;
    private LinearLayout facilityList;
    private TextView nearbyNotice;
    private TextView routeStatus;
    private ConfirmOverlay confirmOverlay;
    private View drowsyBanner;
    private TextView drowsyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        bindViews();

        View overlayView = LayoutInflater.from(this)
                .inflate(R.layout.view_emergency, (ViewGroup) findViewById(android.R.id.content), false);
        addContentView(overlayView, overlayView.getLayoutParams());
        overlay = new EmergencyOverlay(overlayView, viewModel);

        View confirmView = LayoutInflater.from(this)
                .inflate(R.layout.view_confirm, (ViewGroup) findViewById(android.R.id.content), false);
        addContentView(confirmView, confirmView.getLayoutParams());
        confirmOverlay = new ConfirmOverlay(confirmView, viewModel);

        bindActions();
        observe();
        requestMissingPermissions();

        NavigationBarView navigation = findViewById(R.id.navigation);
        navigation.setOnItemSelectedListener(this::onDestinationSelected);
        navigation.setSelectedItemId(viewModel.getSelectedDestination());
    }

    private void bindViews() {
        drivingChip = findViewById(R.id.driving_chip);
        toggleDriving = findViewById(R.id.toggle_driving);
        coreCaption = findViewById(R.id.core_caption);
        coreValue = findViewById(R.id.core_value);
        statusIcon = findViewById(R.id.status_icon);
        tileSpeed = findViewById(R.id.tile_speed);
        tileGear = findViewById(R.id.tile_gear);
        tileBrake = findViewById(R.id.tile_brake);
        detailPane = findViewById(R.id.detail_pane);
        detailLocation = findViewById(R.id.detail_location);
        detailCabin = findViewById(R.id.detail_cabin);
        detailReports = findViewById(R.id.detail_reports);
        homeContent = findViewById(R.id.home_content);
        nearbyContent = findViewById(R.id.nearby_content);
        testContent = findViewById(R.id.test_content);
        testControls = findViewById(R.id.test_controls);
        testNotice = findViewById(R.id.test_notice);
        facilityList = findViewById(R.id.facility_list);
        nearbyNotice = findViewById(R.id.nearby_notice);
        routeStatus = findViewById(R.id.route_status);
        drowsyBanner = findViewById(R.id.drowsy_banner);
        drowsyText = findViewById(R.id.drowsy_text);
    }

    private void bindActions() {
        toggleDriving.setOnClickListener(view -> viewModel.toggleDriving());
        View openNearby = findViewById(R.id.open_nearby); // Only present in wide windows.
        if (openNearby != null) {
            openNearby.setOnClickListener(view -> {
                NavigationBarView navigation = findViewById(R.id.navigation);
                navigation.setSelectedItemId(R.id.nav_nearby);
            });
        }
        bindSituation(R.id.select_ua, EmergencyType.UNINTENDED_ACCELERATION);
        bindSituation(R.id.select_accident, EmergencyType.ACCIDENT);
        bindSituation(R.id.select_fire, EmergencyType.FIRE);
        bindSituation(R.id.select_flood, EmergencyType.FLOOD);
        bindScenario(R.id.scenario_ua, Scenario.UNINTENDED_ACCELERATION);
        bindScenario(R.id.scenario_collision, Scenario.COLLISION);
        bindScenario(R.id.scenario_airbag, Scenario.AIRBAG_DEPLOYED);
        bindScenario(R.id.scenario_fire, Scenario.CABIN_FIRE);
        bindScenario(R.id.scenario_drowsy, Scenario.MONOTONOUS_DRIVE);
        findViewById(R.id.scenario_stop).setOnClickListener(view -> viewModel.stopScenario());
        findViewById(R.id.drowsy_close).setOnClickListener(view -> viewModel.dismissDrowsinessAlert());
    }

    private void bindSituation(int buttonId, EmergencyType type) {
        findViewById(buttonId).setOnClickListener(view -> viewModel.selectEmergency(type));
    }

    private void bindScenario(int buttonId, Scenario scenario) {
        findViewById(buttonId).setOnClickListener(view -> viewModel.startScenario(scenario));
    }

    private void observe() {
        viewModel.isDriving().observe(this, this::renderDrivingState);
        viewModel.getSnapshot().observe(this, this::renderSnapshot);
        viewModel.getEmergency().observe(this, this::renderEmergency);
        viewModel.getStepIndex().observe(this, overlay::showStep);
        detailReports.setText(getString(R.string.mock_report_count, viewModel.getMockReportCount()));
        viewModel.getLastReport().observe(this, report -> {
            overlay.showReport(report);
            detailReports.setText(getString(R.string.mock_report_count, viewModel.getMockReportCount()));
        });
        viewModel.getRouteStatus().observe(this, this::renderRouteStatus);
        viewModel.getVoiceStatus().observe(this, confirmOverlay::showVoiceStatus);
        viewModel.getCountdown().observe(this, confirmOverlay::showCountdown);
        viewModel.getDrowsinessAlert().observe(this, this::renderDrowsinessAlert);
    }

    private void renderDrowsinessAlert(Integer message) {
        boolean visible = message != null && message != 0;
        drowsyBanner.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) drowsyText.setText(message);
    }

    private void requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        for (String permission : RUNTIME_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, missing.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) viewModel.onPermissionsChanged();
    }

    private boolean onDestinationSelected(MenuItem item) {
        int id = item.getItemId();
        viewModel.setSelectedDestination(id);
        homeContent.setVisibility(id == R.id.nav_home ? View.VISIBLE : View.GONE);
        nearbyContent.setVisibility(id == R.id.nav_nearby ? View.VISIBLE : View.GONE);
        testContent.setVisibility(id == R.id.nav_test ? View.VISIBLE : View.GONE);
        if (id == R.id.nav_nearby) renderFacilities();
        return true;
    }

    private void renderFacilities() {
        nearbyNotice.setText(viewModel.hasRealLocation()
                ? R.string.nearby_notice_real_location
                : R.string.nearby_notice_sample_location);
        FacilityUi.fillList(facilityList, viewModel.nearbyFacilities(), viewModel::navigateTo);
    }

    private void renderEmergency(MainViewModel.ActiveEmergency active) {
        // Unconfirmed: ask first. Confirmed: the situation screen.
        confirmOverlay.show(active);
        MainViewModel.ActiveEmergency confirmed = active != null && active.confirmed ? active : null;
        overlay.showEmergency(confirmed);
        if (confirmed != null) overlay.showStep(viewModel.getStepIndex().getValue());
        boolean normal = active == null;
        coreValue.setText(normal ? R.string.status_normal : R.string.status_alert);
        coreValue.setTextColor(ContextCompat.getColor(
                this, normal ? R.color.on_surface : R.color.emergency_critical));
        statusIcon.setImageResource(normal ? R.drawable.ic_check_circle : R.drawable.ic_warning);
        statusIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this, normal ? R.color.success : R.color.emergency_critical)));
    }

    private void renderSnapshot(VehicleSnapshot snapshot) {
        tileSpeed.setText(snapshot.speedKph == null
                ? getString(R.string.value_unknown)
                : getString(R.string.speed_kph, Math.round(snapshot.speedKph)));
        tileGear.setText(gearLabel(snapshot.gear));
        tileBrake.setText(snapshot.brakePressed == null
                ? getString(R.string.value_unknown)
                : getString(snapshot.brakePressed ? R.string.brake_on : R.string.brake_off));
        detailLocation.setText(snapshot.hasLocation()
                ? String.format(Locale.US, "%.4f, %.4f", snapshot.latitude, snapshot.longitude)
                : getString(R.string.value_unknown));
        detailCabin.setText(snapshot.cabinTemperatureC == null
                ? getString(R.string.value_unknown)
                : getString(R.string.temperature_c, Math.round(snapshot.cabinTemperatureC)));
    }

    private static String gearLabel(VehicleSnapshot.Gear gear) {
        switch (gear) {
            case PARK:
                return "P";
            case REVERSE:
                return "R";
            case NEUTRAL:
                return "N";
            case DRIVE:
                return "D";
            default:
                return "-";
        }
    }

    private void renderRouteStatus(RouteNavigator.Status status) {
        int message = routeMessage(status);
        routeStatus.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) routeStatus.setText(message);
        overlay.showRouteStatus(message);
    }

    private static int routeMessage(RouteNavigator.Status status) {
        switch (status) {
            case REQUESTED:
                return R.string.route_requested;
            case STARTED:
                return R.string.route_started;
            case FAILED:
                return R.string.route_failed;
            default:
                return 0;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        screenVisible = true;
        updateMotion();
    }

    @Override
    protected void onStop() {
        screenVisible = false;
        updateMotion();
        super.onStop();
    }

    /** Step animations run only while parked and visible (Pleos driver distraction guidance). */
    private void updateMotion() {
        if (overlay != null) overlay.setMotionAllowed(screenVisible && !driving);
    }

    private void renderDrivingState(boolean isDriving) {
        driving = isDriving;
        updateMotion();
        drivingChip.setText(isDriving ? R.string.state_driving : R.string.state_parked);
        drivingChip.setTextColor(ContextCompat.getColor(
                this, isDriving ? R.color.on_primary : R.color.on_surface_variant));
        drivingChip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this, isDriving ? R.color.primary : R.color.surface_variant)));

        toggleDriving.setText(
                isDriving ? R.string.dev_toggle_to_parked : R.string.dev_toggle_to_driving);
        coreCaption.setText(isDriving ? R.string.restricted_notice : R.string.core_caption);
        testControls.setVisibility(isDriving ? View.GONE : View.VISIBLE);
        testNotice.setVisibility(isDriving ? View.VISIBLE : View.GONE);

        if (detailPane != null) {
            detailPane.setVisibility(isDriving ? View.GONE : View.VISIBLE);
        }
    }
}
