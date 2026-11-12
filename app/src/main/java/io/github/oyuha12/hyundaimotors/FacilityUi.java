package io.github.oyuha12.hyundaimotors;

import android.content.Context;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.button.MaterialButton;
import io.github.oyuha12.hyundaimotors.demo.VideoWording;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.ResponseEstimator;
import java.util.Collections;
import java.util.Map;

/** Icons, labels and facility cards shared by the facility list and the situation screens. */
final class FacilityUi {
    interface FacilityAction {
        void run(Facility facility);
    }

    private FacilityUi() {}

    /**
     * Fills a list with facility cards.
     *
     * @param dispatch null hides the mock dispatch button, as on the plain facility list
     * @param videoMode natural wording instead of test wording
     */
    static void fillList(
            ViewGroup list,
            Iterable<Facility> facilities,
            FacilityAction route,
            @Nullable FacilityAction dispatch,
            boolean videoMode) {
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(list.getContext());
        if (!facilities.iterator().hasNext()) {
            // Video mode before the real places are set (VideoConfig TODO).
            TextView unset = new TextView(list.getContext());
            unset.setTextAppearance(R.style.TextAppearance_App_BodySecondary);
            unset.setText(R.string.place_unset);
            list.addView(unset);
            return;
        }
        for (Facility facility : facilities) {
            View row = inflater.inflate(R.layout.item_facility, list, false);
            row.setTag(facility);
            ((ImageView) row.findViewById(R.id.facility_icon)).setImageResource(icon(facility.type));
            ((TextView) row.findViewById(R.id.facility_name)).setText(facility.name);
            ((TextView) row.findViewById(R.id.facility_detail))
                    .setText(detail(list.getContext(), facility, videoMode));
            row.findViewById(R.id.facility_route).setOnClickListener(v -> route.run(facility));

            MaterialButton dispatchButton = row.findViewById(R.id.facility_dispatch);
            boolean canDispatch = dispatch != null && ResponseEstimator.dispatches(facility.type);
            dispatchButton.setVisibility(canDispatch ? View.VISIBLE : View.GONE);
            if (canDispatch) dispatchButton.setOnClickListener(v -> dispatch.run(facility));
            list.addView(row);
        }
        updateArrivals(list, Collections.emptyMap(), videoMode);
    }

    /**
     * Shows each card's arrival: the estimate before a request, a countdown after one.
     *
     * @param dispatched facility name to expected arrival on the elapsedRealtime clock
     * @param videoMode natural wording instead of test wording
     */
    static void updateArrivals(ViewGroup list, Map<String, Long> dispatched, boolean videoMode) {
        long now = SystemClock.elapsedRealtime();
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (!(row.getTag() instanceof Facility)) continue;
            Facility facility = (Facility) row.getTag();
            Integer minutes = ResponseEstimator.arrivalMinutes(facility);
            View box = row.findViewById(R.id.facility_eta_box);
            box.setVisibility(minutes == null ? View.GONE : View.VISIBLE);
            if (minutes == null) continue;

            TextView caption = row.findViewById(R.id.facility_eta_caption);
            TextView eta = row.findViewById(R.id.facility_eta);
            MaterialButton dispatchButton = row.findViewById(R.id.facility_dispatch);
            Long arrivalAt = dispatched.get(facility.name);
            showArrival(caption, eta, facility, arrivalAt, now);
            dispatchButton.setEnabled(arrivalAt == null);
            dispatchButton.setText(VideoWording.pick(arrivalAt == null
                    ? R.string.dispatch_request
                    : R.string.dispatch_requested, videoMode));
        }
    }

    /**
     * One arrival: the estimate before a request, then a countdown that drops every second.
     *
     * @param arrivalAt expected arrival on the elapsedRealtime clock, or null before a request
     */
    static void showArrival(
            TextView caption, TextView eta, Facility facility, @Nullable Long arrivalAt, long now) {
        Context context = caption.getContext();
        if (arrivalAt == null) {
            Integer minutes = ResponseEstimator.arrivalMinutes(facility);
            caption.setText(R.string.eta_caption_estimate);
            eta.setText(minutes == null
                    ? context.getString(R.string.value_none)
                    : context.getString(R.string.eta_minutes, minutes));
            return;
        }
        long left = Math.max(0, arrivalAt - now);
        caption.setText(left == 0 ? R.string.eta_caption_arrived : R.string.eta_caption_countdown);
        eta.setText(left == 0
                ? context.getString(R.string.eta_arrived)
                : context.getString(R.string.eta_countdown, left / 60_000, (left / 1000) % 60));
    }

    /** Example: "병원 · 1.8km · 북서쪽". */
    static String detail(Context context, Facility facility) {
        return detail(context, facility, false);
    }

    /** As {@link #detail(Context, Facility)}, with video mode wording for the kind of place. */
    static String detail(Context context, Facility facility, boolean videoMode) {
        return context.getString(
                R.string.facility_detail,
                context.getString(label(facility.type, videoMode)),
                Directions.describe(context, facility));
    }

    /** The kind of place; in video mode a safe stop reads "안전 정차 장소". */
    @StringRes
    static int label(Facility.Type type, boolean videoMode) {
        return VideoWording.pick(label(type), videoMode);
    }

    @DrawableRes
    static int icon(Facility.Type type) {
        switch (type) {
            case SAFE_STOP:
                return R.drawable.ic_local_parking;
            case EVACUATION_SITE:
                return R.drawable.ic_emergency_home;
            case FIRE_STATION:
                return R.drawable.ic_local_fire_department;
            case POLICE:
                return R.drawable.ic_local_police;
            case HOSPITAL:
                return R.drawable.ic_local_hospital;
            case ROAD_AUTHORITY:
                return R.drawable.ic_add_road;
            case REPAIR_SHOP:
            default:
                return R.drawable.ic_build;
        }
    }

    @StringRes
    static int label(Facility.Type type) {
        switch (type) {
            case SAFE_STOP:
                return R.string.facility_safe_stop;
            case EVACUATION_SITE:
                return R.string.facility_evacuation_site;
            case FIRE_STATION:
                return R.string.facility_fire_station;
            case POLICE:
                return R.string.facility_police;
            case HOSPITAL:
                return R.string.facility_hospital;
            case ROAD_AUTHORITY:
                return R.string.facility_road_authority;
            case REPAIR_SHOP:
            default:
                return R.string.facility_repair_shop;
        }
    }

    @DrawableRes
    static int situationIcon(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return R.drawable.ic_speed;
            case ACCIDENT:
                return R.drawable.ic_car_crash;
            case FIRE:
                return R.drawable.ic_local_fire_department;
            case FLOOD:
                return R.drawable.ic_flood;
            case BRAKE_FAILURE:
                return R.drawable.ic_taxi_alert;
            case TIRE_FAILURE:
                return R.drawable.ic_tire_repair;
            case DRIVER_HEALTH:
                return R.drawable.ic_ecg_heart;
            case SECONDARY_ACCIDENT:
            default:
                return R.drawable.ic_minor_crash;
        }
    }
}
