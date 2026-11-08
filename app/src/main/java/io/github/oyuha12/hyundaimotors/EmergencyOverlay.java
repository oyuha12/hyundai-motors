package io.github.oyuha12.hyundaimotors;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.report.MockEmergencyReporter;
import java.util.List;

/**
 * Situation screen: one instruction at a time, the evacuation route, nearby help and a mock
 * report button. Narrow windows stack the parts; layout-w840dp places them side by side.
 */
final class EmergencyOverlay {
    private final View root;
    private final MainViewModel viewModel;
    private final ImageView icon;
    private final TextView title;
    private final TextView reason;
    private final TextView counter;
    private final LinearProgressIndicator progress;
    private final TextView symbol;
    private final TextView stepText;
    private final TextView reportStatus;
    private final TextView routeStatus;
    private final ImageView evacuationIcon;
    private final TextView evacuationInstruction;
    private final TextView evacuationName;
    private final TextView evacuationDetail;
    private final View evacuationRoute;
    private final ViewGroup facilityList;
    private final ImageView stepIcon;
    private final StepAnimator stepAnimator;
    private boolean motionAllowed;
    @Nullable private EmergencyType shown;

    EmergencyOverlay(View root, MainViewModel viewModel) {
        this.root = root;
        this.viewModel = viewModel;
        icon = root.findViewById(R.id.emergency_icon);
        title = root.findViewById(R.id.emergency_title);
        reason = root.findViewById(R.id.emergency_reason);
        counter = root.findViewById(R.id.emergency_counter);
        progress = root.findViewById(R.id.emergency_progress);
        symbol = root.findViewById(R.id.emergency_symbol);
        stepText = root.findViewById(R.id.emergency_step);
        reportStatus = root.findViewById(R.id.emergency_report_status);
        routeStatus = root.findViewById(R.id.emergency_route_status);
        evacuationIcon = root.findViewById(R.id.evacuation_icon);
        evacuationInstruction = root.findViewById(R.id.evacuation_instruction);
        evacuationName = root.findViewById(R.id.evacuation_name);
        evacuationDetail = root.findViewById(R.id.evacuation_detail);
        evacuationRoute = root.findViewById(R.id.evacuation_route);
        facilityList = root.findViewById(R.id.support_facility_list);
        stepIcon = root.findViewById(R.id.emergency_step_icon);
        stepAnimator = new StepAnimator(root.findViewById(R.id.emergency_step_visual));

        root.findViewById(R.id.emergency_report).setOnClickListener(v -> viewModel.submitMockReport());
        root.findViewById(R.id.emergency_repeat).setOnClickListener(v -> viewModel.repeatGuidance());
        root.findViewById(R.id.emergency_dismiss).setOnClickListener(v -> viewModel.dismissEmergency());
    }

    void showEmergency(@Nullable MainViewModel.ActiveEmergency active) {
        shown = active == null ? null : active.type;
        root.setVisibility(active == null ? View.GONE : View.VISIBLE);
        stepAnimator.setAllowed(motionAllowed && active != null);
        if (active == null) return;

        icon.setImageResource(FacilityUi.situationIcon(active.type));
        title.setText(EmergencyPlan.title(active.type));
        reason.setText(active.reason);
        evacuationInstruction.setText(EmergencyPlan.evacuationInstruction(active.type));
        evacuationIcon.setImageResource(FacilityUi.icon(EmergencyPlan.evacuationTarget(active.type)));

        Facility target = viewModel.evacuationTarget(active.type);
        evacuationRoute.setEnabled(target != null);
        if (target != null) {
            evacuationName.setText(target.name);
            evacuationDetail.setText(Directions.describe(root.getContext(), target));
            evacuationRoute.setOnClickListener(v -> viewModel.navigateTo(target));
        }

        FacilityUi.fillList(facilityList, viewModel.supportFacilities(active.type), viewModel::navigateTo);
    }

    void showStep(@Nullable Integer index) {
        if (shown == null || index == null) return;
        List<GuidanceStep> steps = EmergencyPlan.steps(shown);
        if (index >= steps.size()) return;
        GuidanceStep step = steps.get(index);
        counter.setText(root.getContext().getString(R.string.step_counter, index + 1, steps.size()));
        progress.setMax(steps.size());
        progress.setProgressCompat(index + 1, false);
        stepIcon.setVisibility(step.hasIcon() ? View.VISIBLE : View.GONE);
        symbol.setVisibility(step.hasIcon() ? View.GONE : View.VISIBLE);
        if (step.hasIcon()) {
            stepIcon.setImageResource(step.icon);
        } else {
            symbol.setText(step.symbol);
        }
        stepText.setText(step.text);
        stepAnimator.show(step.motion);
    }

    /** Motion runs only while parked and while the screen is visible. */
    void setMotionAllowed(boolean allowed) {
        motionAllowed = allowed;
        stepAnimator.setAllowed(allowed && shown != null);
    }

    void showRouteStatus(@StringRes int message) {
        routeStatus.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) routeStatus.setText(message);
    }

    void showReport(@Nullable MockEmergencyReporter.Report report) {
        reportStatus.setVisibility(report == null ? View.GONE : View.VISIBLE);
    }
}
