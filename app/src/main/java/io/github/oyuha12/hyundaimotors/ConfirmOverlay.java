package io.github.oyuha12.hyundaimotors;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;

/** "Is this an emergency?" screen shown before the situation screen. */
final class ConfirmOverlay {
    private final View root;
    private final ImageView icon;
    private final TextView title;
    private final TextView reason;
    private final TextView voiceStatus;
    private final TextView countdown;
    private final View mockBanner;

    ConfirmOverlay(View root, MainViewModel viewModel) {
        this.root = root;
        mockBanner = root.findViewById(R.id.confirm_mock_banner);
        icon = root.findViewById(R.id.confirm_icon);
        title = root.findViewById(R.id.confirm_title);
        reason = root.findViewById(R.id.confirm_reason);
        voiceStatus = root.findViewById(R.id.confirm_voice_status);
        countdown = root.findViewById(R.id.confirm_countdown);
        root.findViewById(R.id.confirm_yes).setOnClickListener(v -> viewModel.confirmEmergency());
        root.findViewById(R.id.confirm_no).setOnClickListener(v -> viewModel.declineEmergency());
    }

    /** Shows the question for an unconfirmed emergency; hides the screen otherwise. */
    void show(@Nullable MainViewModel.ActiveEmergency pending) {
        boolean visible = pending != null && !pending.confirmed;
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        icon.setImageResource(FacilityUi.situationIcon(pending.type));
        title.setText(root.getContext().getString(
                R.string.confirm_title, root.getContext().getString(EmergencyPlan.title(pending.type))));
        reason.setText(pending.reason);
    }

    /** Video mode hides the test banner. */
    void setVideoMode(boolean on) {
        mockBanner.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    void showVoiceStatus(@Nullable Integer message) {
        boolean visible = message != null && message != 0;
        voiceStatus.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        if (visible) voiceStatus.setText(message);
    }

    void showCountdown(@Nullable Integer seconds) {
        boolean visible = seconds != null && seconds > 0;
        countdown.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) countdown.setText(root.getContext().getString(R.string.confirm_countdown, seconds));
    }
}
