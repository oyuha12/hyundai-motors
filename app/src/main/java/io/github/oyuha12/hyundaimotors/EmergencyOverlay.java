package io.github.oyuha12.hyundaimotors;

import android.content.Context;
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
import io.github.oyuha12.hyundaimotors.report.ReportReceipt;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Situation screen: one instruction at a time, the SOS report button and its receipt, the
 * evacuation route and nearby help. Narrow windows stack the parts; layout-w840dp places them
 * side by side. Reports are mock records only.
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
    private final TextView reportVoiceStatus;
    private final TextView heard;
    private final View receipt;
    private final TextView receiptLocation;
    private final TextView receiptSituation;
    private final TextView receiptTime;
    private final TextView receiptSpeed;
    private final View receiptTestNotice;
    private boolean reported;
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
        reportVoiceStatus = root.findViewById(R.id.report_voice_status);
        heard = root.findViewById(R.id.report_heard);
        receipt = root.findViewById(R.id.report_receipt);
        receiptLocation = root.findViewById(R.id.receipt_location);
        receiptSituation = root.findViewById(R.id.receipt_situation);
        receiptTime = root.findViewById(R.id.receipt_time);
        receiptSpeed = root.findViewById(R.id.receipt_speed);
        receiptTestNotice = root.findViewById(R.id.receipt_test_notice);

        reportButton.setOnClickListener(v -> viewModel.onSosPressed());
        root.findViewById(R.id.receipt_open_status)
                .setOnClickListener(v -> viewModel.openEvacuationStatus());
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
        evacuationInstruction.setText(
                VideoWording.pick(EmergencyPlan.evacuationInstruction(active.type), videoMode));
        evacuationIcon.setImageResource(FacilityUi.icon(EmergencyPlan.evacuationTarget(active.type)));

        Facility target = viewModel.evacuationTarget(active.type);
        evacuationRoute.setEnabled(target != null);
        if (target != null) {
            evacuationName.setText(target.name);
            evacuationDetail.setText(Directions.describe(root.getContext(), target));
            evacuationRoute.setOnClickListener(v -> viewModel.navigateTo(target));
        } else {
            // Video mode before the real places are set (VideoConfig TODO).
            evacuationName.setText(R.string.place_unset);
            evacuationDetail.setText(R.string.value_none);
            evacuationRoute.setOnClickListener(null);
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

    /** Video mode: no test banner or notice, and natural wording for the mock report. */
    void setVideoMode(boolean on) {
        videoMode = on;
        mockBanner.setVisibility(on ? View.GONE : View.VISIBLE);
        receiptTestNotice.setVisibility(on ? View.GONE : View.VISIBLE);
        ((TextView) root.findViewById(R.id.evacuation_title))
                .setText(VideoWording.pick(R.string.evacuation_title, on));
        updateReportButton();
        respondersNotice.setText(VideoWording.pick(R.string.responders_notice, on));
    }

    /** The receipt once a report is recorded: 112 and 119, and what was passed on. */
    void showReceipt(@Nullable ReportReceipt shownReceipt) {
        reported = shownReceipt != null;
        receipt.setVisibility(reported ? View.VISIBLE : View.GONE);
        updateReportButton();
        if (shownReceipt == null) return;
        Context context = root.getContext();
        receiptLocation.setText(shownReceipt.location);
        receiptSituation.setText(EmergencyPlan.title(shownReceipt.type));
        receiptTime.setText(new SimpleDateFormat("HH:mm:ss", Locale.KOREA)
                .format(new Date(shownReceipt.reportedAtMillis)));
        receiptSpeed.setText(context.getString(R.string.report_receipt_speed_value,
                speed(context, shownReceipt.topSpeedKph), speed(context, shownReceipt.speedKph)));
    }

    private static String speed(Context context, @Nullable Float kph) {
        return kph == null
                ? context.getString(R.string.value_unknown)
                : context.getString(R.string.speed_kph, Math.round(kph));
    }

    private void updateReportButton() {
        reportButton.setEnabled(!reported);
        reportButton.setText(reported
                ? R.string.report_done_button
                : VideoWording.pick(R.string.emergency_report, videoMode));
    }

    /** The recognized request, like a speech bubble: "음성 인식: '신고해 줘'". */
    void showHeard(@Nullable String text) {
        heard.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text != null) heard.setText(root.getContext().getString(R.string.report_heard, text));
    }

    /** "듣고 있습니다 …" while listening for a spoken report, or why it stopped. */
    void showReportVoiceStatus(@Nullable Integer message) {
        boolean visible = message != null && message != 0;
        reportVoiceStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) reportVoiceStatus.setText(message);
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

    /** "다음 안내: 300m 앞 우회전" while the navigation app guides the route, or null. */
    void showNextTurn(@Nullable String text) {
        TextView view = root.findViewById(R.id.emergency_next_turn);
        view.setVisibility(text == null ? View.GONE : View.VISIBLE);
        view.setText(text);
    }

    /** A mock dispatch request to one facility. SOS reports show in the receipt instead. */
    void showReport(@Nullable MockEmergencyReporter.Report report) {
        boolean dispatch = report != null && report.facilityName != null;
        reportStatus.setVisibility(dispatch ? View.VISIBLE : View.GONE);
        if (!dispatch) return;
        reportStatus.setText(root.getContext().getString(
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
