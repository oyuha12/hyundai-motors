package io.github.oyuha12.hyundaimotors;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
    };

    private MainViewModel viewModel;
    private EmergencyOverlay overlay;
    private TextView drivingChip;
    private MaterialButton toggleDriving;
    private TextView coreCaption;
    private TextView coreValue;
    private TextView tileSpeed;
    private TextView tileGear;
    private TextView tileBrake;
    private TextView detailLocation;
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
        tileSpeed = findViewById(R.id.tile_speed);
        tileGear = findViewById(R.id.tile_gear);
        tileBrake = findViewById(R.id.tile_brake);
        detailPane = findViewById(R.id.detail_pane);
        detailLocation = findViewById(R.id.detail_location);
        detailReports = findViewById(R.id.detail_reports);
        homeContent = findViewById(R.id.home_content);
        nearbyContent = findViewById(R.id.nearby_content);
        testContent = findViewById(R.id.test_content);
        testControls = findViewById(R.id.test_controls);
        testNotice = findViewById(R.id.test_notice);
        facilityList = findViewById(R.id.facility_list);
        nearbyNotice = findViewById(R.id.nearby_notice);
        routeStatus = findViewById(R.id.route_status);
    }

    private void bindActions() {
        toggleDriving.setOnClickListener(view -> viewModel.toggleDriving());
        findViewById(R.id.open_nearby).setOnClickListener(view -> {
            NavigationBarView navigation = findViewById(R.id.navigation);
            navigation.setSelectedItemId(R.id.nav_nearby);
        });
        bindScenario(R.id.scenario_ua, Scenario.UNINTENDED_ACCELERATION);
        bindScenario(R.id.scenario_brake, Scenario.BRAKE_FAILURE);
        bindScenario(R.id.scenario_collision, Scenario.COLLISION);
        bindScenario(R.id.scenario_overheat, Scenario.OVERHEAT);
        bindScenario(R.id.scenario_powertrain, Scenario.POWERTRAIN_FAULT);
    }

    private void bindScenario(int buttonId, Scenario scenario) {
        findViewById(buttonId).setOnClickListener(view -> viewModel.startScenario(scenario));
    }

    private void observe() {
        viewModel.isDriving().observe(this, this::renderDrivingState);
        viewModel.getSnapshot().observe(this, this::renderSnapshot);
        viewModel.getEmergency().observe(this, this::renderEmergency);
        viewModel.getStepIndex().observe(this, overlay::showStep);
        viewModel.getLastReport().observe(this, report -> {
            overlay.showReport(report);
            detailReports.setText(getString(R.string.mock_report_count, viewModel.getMockReportCount()));
        });
        viewModel.getRouteStatus().observe(this, this::renderRouteStatus);
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
        facilityList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Facility facility : viewModel.nearbyFacilities()) {
            View row = inflater.inflate(R.layout.item_facility, facilityList, false);
            ((TextView) row.findViewById(R.id.facility_name)).setText(facility.name);
            ((TextView) row.findViewById(R.id.facility_detail))
                    .setText(Directions.describe(this, facility));
            row.findViewById(R.id.facility_route)
                    .setOnClickListener(view -> viewModel.navigateTo(facility));
            facilityList.addView(row);
        }
    }

    private void renderEmergency(EmergencyType type) {
        overlay.showEmergency(type);
        if (type != null) overlay.showStep(viewModel.getStepIndex().getValue());
        coreValue.setText(type == null ? R.string.status_normal : R.string.status_alert);
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

    private void renderDrivingState(boolean isDriving) {
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
