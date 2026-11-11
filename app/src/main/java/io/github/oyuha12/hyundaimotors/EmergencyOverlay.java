package io.github.oyuha12.hyundaimotors;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import io.github.oyuha12.hyundaimotors.demo.VideoWording;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.report.MockEmergencyReporter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private Map<String, Long> dispatched = Collections.emptyMap();
    private final ImageView stepIcon;
    private final StepAnimator stepAnimator;
    private final View mockBanner;
    private final MaterialButton reportButton;
    private final TextView respondersNotice;
    private final View waitingForStop;
    private boolean motionAllowed;
    private boolean videoMode;
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
        mockBanner = root.findViewById(R.id.emergency_mock_banner);
        reportButton = root.findViewById(R.id.emergency_report);
        respondersNotice = root.findViewById(R.id.responders_notice);
        waitingForStop = root.findViewById(R.id.emergency_waiting_for_stop);

        reportButton.setOnClickListener(v -> viewModel.submitMockReport());
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

        FacilityUi.fillList(facilityList, viewModel.supportFacilities(active.type),
                viewModel::navigateTo, viewModel::requestDispatch, videoMode);
        showDispatches(viewModel.getDispatches().getValue());
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
        stepText.setText(VideoWording.pick(step.text, videoMode));
        stepAnimator.show(step.motion);
    }

    /** "차량이 멈추면 다음 안내를 시작합니다" while the next step waits for the car to stop. */
    void showWaitingForStop(@Nullable Boolean waiting) {
        waitingForStop.setVisibility(Boolean.TRUE.equals(waiting) ? View.VISIBLE : View.GONE);
    }

    /** Video mode: no test banner, and natural wording for the mock report. */
    void setVideoMode(boolean on) {
        videoMode = on;
        mockBanner.setVisibility(on ? View.GONE : View.VISIBLE);
        reportButton.setText(VideoWording.pick(R.string.emergency_report, on));
        respondersNotice.setText(VideoWording.pick(R.string.responders_notice, on));
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
        if (report == null) return;
        reportStatus.setText(report.facilityName == null
                ? root.getContext().getString(VideoWording.pick(R.string.mock_report_done, videoMode))
                : root.getContext().getString(
                        VideoWording.pick(R.string.mock_dispatch_done, videoMode), report.facilityName));
    }

    /** Refreshes arrival estimates and, once a request is made, counts down every second. */
    void showDispatches(@Nullable Map<String, Long> dispatched) {
        this.dispatched = dispatched == null ? Collections.emptyMap() : dispatched;
        root.removeCallbacks(countdownTick);
        countdownTick.run();
    }

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            FacilityUi.updateArrivals(facilityList, dispatched, videoMode);
            if (!dispatched.isEmpty() && root.getVisibility() == View.VISIBLE) {
                root.postDelayed(this, 1_000L);
            }
        }
    };
}
