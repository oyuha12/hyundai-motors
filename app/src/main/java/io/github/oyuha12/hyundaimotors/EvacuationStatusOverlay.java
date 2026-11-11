package io.github.oyuha12.hyundaimotors;

import android.content.Context;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import io.github.oyuha12.hyundaimotors.demo.VideoWording;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.StatusPlace;
import io.github.oyuha12.hyundaimotors.report.ReportReceipt;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evacuation and dispatch status, shown right after a report: where to evacuate, who 112 and
 * 119 sent and how soon they arrive, counting down every second from the report, and a mini
 * map of all of it around the car. Places that are not set yet show "장소 미설정".
 */
final class EvacuationStatusOverlay {
    private final View root;
    private final MainViewModel viewModel;
    private final TextView reported;
    private final TextView step;
    private final ResponseMapView map;
    private final ImageView evacuationIcon;
    private final TextView evacuationName;
    private final TextView evacuationDetail;
    private final MaterialButton evacuationRoute;
    private final TextView notice;
    private final ViewGroup responders;
    private Map<String, Long> dispatched = Collections.emptyMap();
    @Nullable private EmergencyType shown;
    private boolean videoMode;

    EvacuationStatusOverlay(View root, MainViewModel viewModel) {
        this.root = root;
        this.viewModel = viewModel;
        reported = root.findViewById(R.id.status_reported);
        step = root.findViewById(R.id.status_step);
        map = root.findViewById(R.id.status_map);
        evacuationIcon = root.findViewById(R.id.status_evacuation_icon);
        evacuationName = root.findViewById(R.id.status_evacuation_name);
        evacuationDetail = root.findViewById(R.id.status_evacuation_detail);
        evacuationRoute = root.findViewById(R.id.status_evacuation_route);
        notice = root.findViewById(R.id.status_notice);
        responders = root.findViewById(R.id.status_responder_list);
        root.findViewById(R.id.status_back).setOnClickListener(v -> viewModel.closeEvacuationStatus());
        root.findViewById(R.id.status_dismiss).setOnClickListener(v -> viewModel.dismissEmergency());
    }

    /** Shows the status of this situation, or hides it with null. */
    void show(@Nullable EmergencyType type) {
        root.removeCallbacks(tick);
        shown = type;
        root.setVisibility(type == null ? View.GONE : View.VISIBLE);
        if (type == null) return;
        fill(type);
        showStep(viewModel.getStepIndex().getValue());
        tick.run();
    }

    private void fill(EmergencyType type) {
        Context context = root.getContext();
        List<Facility> onMap = new ArrayList<>();

        StatusPlace evacuation = viewModel.evacuationStatus(type);
        evacuationIcon.setImageResource(FacilityUi.icon(evacuation.type));
        Facility target = evacuation.facility;
        evacuationRoute.setEnabled(target != null);
        if (target != null) {
            onMap.add(target);
            evacuationName.setText(target.name);
            evacuationDetail.setText(Directions.describe(context, target));
            // TODO(6단계): NaviHelper. Until then the existing route flow (demo route or Pleos navi).
            evacuationRoute.setOnClickListener(v -> viewModel.navigateTo(target));
        } else {
            evacuationName.setText(R.string.place_unset);
            evacuationDetail.setText(FacilityUi.label(evacuation.type));
            evacuationRoute.setOnClickListener(null);
        }

        responders.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (StatusPlace place : viewModel.responderStatus(type)) {
            View row = inflater.inflate(R.layout.item_status_place, responders, false);
            row.setTag(place);
            ((ImageView) row.findViewById(R.id.status_place_icon))
                    .setImageResource(FacilityUi.icon(place.type));
            TextView name = row.findViewById(R.id.status_place_name);
            TextView detail = row.findViewById(R.id.status_place_detail);
            if (place.facility != null) {
                onMap.add(place.facility);
                name.setText(place.facility.name);
                detail.setText(FacilityUi.detail(context, place.facility));
            } else {
                name.setText(R.string.place_unset);
                detail.setText(FacilityUi.label(place.type));
            }
            responders.addView(row);
        }
        map.setPlaces(onMap);
    }

    /** The report time in the header. */
    void showReceipt(@Nullable ReportReceipt receipt) {
        reported.setVisibility(receipt == null ? View.GONE : View.VISIBLE);
        if (receipt == null) return;
        String time = new SimpleDateFormat("HH:mm:ss", Locale.KOREA)
                .format(new Date(receipt.reportedAtMillis));
        reported.setText(root.getContext().getString(R.string.status_reported, time));
    }

    /** The guidance step still being read, so the status screen does not hide it. */
    void showStep(@Nullable Integer index) {
        if (shown == null || index == null) return;
        List<GuidanceStep> steps = EmergencyPlan.steps(shown);
        if (index < 0 || index >= steps.size()) return;
        Context context = root.getContext();
        step.setText(context.getString(R.string.status_step, index + 1, steps.size(),
                context.getString(VideoWording.pick(steps.get(index).text, videoMode))));
    }

    void showDispatches(@Nullable Map<String, Long> dispatched) {
        this.dispatched = dispatched == null ? Collections.emptyMap() : dispatched;
        updateArrivals();
    }

    void setVideoMode(boolean on) {
        videoMode = on;
        notice.setText(VideoWording.pick(R.string.responders_notice, on));
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateArrivals();
            if (root.getVisibility() == View.VISIBLE) root.postDelayed(this, 1_000L);
        }
    };

    private void updateArrivals() {
        long now = SystemClock.elapsedRealtime();
        for (int i = 0; i < responders.getChildCount(); i++) {
            View row = responders.getChildAt(i);
            StatusPlace place = (StatusPlace) row.getTag();
            TextView caption = row.findViewById(R.id.status_place_eta_caption);
            TextView eta = row.findViewById(R.id.status_place_eta);
            if (place.facility == null) {
                caption.setText(R.string.eta_caption_estimate);
                eta.setText(R.string.value_none);
            } else {
                FacilityUi.showArrival(
                        caption, eta, place.facility, dispatched.get(place.facility.name), now);
            }
        }
    }
}
