package io.github.oyuha12.hyundaimotors;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceCatalog;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.report.MockEmergencyReporter;
import java.util.List;

/** Full-window emergency screen: one instruction at a time, with one-touch actions. */
final class EmergencyOverlay {
    private final View root;
    private final MainViewModel viewModel;
    private final TextView title;
    private final TextView counter;
    private final TextView symbol;
    private final TextView stepText;
    private final TextView routeSummary;
    private final TextView reportStatus;
    @Nullable private EmergencyType shown;

    EmergencyOverlay(View root, MainViewModel viewModel) {
        this.root = root;
        this.viewModel = viewModel;
        title = root.findViewById(R.id.emergency_title);
        counter = root.findViewById(R.id.emergency_counter);
        symbol = root.findViewById(R.id.emergency_symbol);
        stepText = root.findViewById(R.id.emergency_step);
        routeSummary = root.findViewById(R.id.emergency_route_summary);
        reportStatus = root.findViewById(R.id.emergency_report_status);

        root.findViewById(R.id.emergency_report).setOnClickListener(v -> viewModel.submitMockReport());
        root.findViewById(R.id.emergency_repeat).setOnClickListener(v -> viewModel.repeatGuidance());
        root.findViewById(R.id.emergency_dismiss).setOnClickListener(v -> viewModel.dismissEmergency());
        root.findViewById(R.id.emergency_route).setOnClickListener(v -> {
            Facility facility = viewModel.recommendedFacility();
            if (facility != null) viewModel.navigateTo(facility);
        });
    }

    void showEmergency(@Nullable EmergencyType type) {
        shown = type;
        root.setVisibility(type == null ? View.GONE : View.VISIBLE);
        if (type == null) return;

        root.setBackgroundColor(ContextCompat.getColor(
                root.getContext(),
                type.critical ? R.color.emergency_critical : R.color.emergency_warning));
        title.setText(GuidanceCatalog.titleFor(type));

        Facility facility = viewModel.recommendedFacility();
        if (facility != null) {
            routeSummary.setText(root.getContext().getString(
                    R.string.route_summary,
                    facility.name,
                    Directions.describe(root.getContext(), facility)));
        }
    }

    void showStep(@Nullable Integer index) {
        if (shown == null || index == null) return;
        List<GuidanceStep> steps = GuidanceCatalog.stepsFor(shown);
        if (index >= steps.size()) return;
        GuidanceStep step = steps.get(index);
        counter.setText(root.getContext().getString(R.string.step_counter, index + 1, steps.size()));
        symbol.setText(step.symbol);
        stepText.setText(step.text);
    }

    void showRouteStatus(@StringRes int message) {
        TextView status = root.findViewById(R.id.emergency_route_status);
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) status.setText(message);
    }

    void showReport(@Nullable MockEmergencyReporter.Report report) {
        reportStatus.setVisibility(report == null ? View.GONE : View.VISIBLE);
    }
}
