package io.github.oyuha12.hyundaimotors.demo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import java.util.HashSet;
import java.util.Set;

/**
 * Plays a demo route at {@link #SPEED_UP} times real speed, with turn instructions on screen and
 * spoken through the given callback. For demo videos only; real routes use the vehicle's
 * navigation app.
 *
 * <p>Video mode turns it into a car navigation's driving screen: a first-person map, larger and
 * stronger instruction text, playback at {@link #VIDEO_SPEED_UP} times, and calls timed like a
 * navigation's ({@link RouteVoice}), held back while the situation guidance is speaking.
 */
public final class RouteDemoOverlay {
    public interface Speaker {
        /** Speaks text. Returns false when the voice is busy and the text was not said. */
        boolean say(String text);
    }

    /** Simulated driving speed for the remaining-time estimate, km/h. */
    static final float CITY_SPEED_KPH = 40f;
    /** Playback runs this many times faster than real driving. */
    public static final int SPEED_UP = 10;
    /** Video mode plays slower, so the first-person view reads as driving and calls fit in. */
    public static final int VIDEO_SPEED_UP = 4;
    /** Announce a turn when it is this close. */
    static final float ANNOUNCE_METERS = 150f;
    private static final long FRAME_MILLIS = 50L;
    /** Video mode: how often, and how long, a call waits for the voice after playback ends. */
    private static final long RETRY_MILLIS = 300L;
    private static final long RETRY_FOR_MILLIS = 15_000L;

    private final View root;
    private final RouteMapView map;
    private final TextView destination;
    private final TextView turnIcon;
    private final TextView instruction;
    private final TextView remaining;
    private final MaterialButton pause;
    private final View notice;
    private final MaterialCardView panel;
    private final int turnIconSize;
    private final Speaker speaker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Float> announced = new HashSet<>();
    private final RouteVoice voice = new RouteVoice();

    @Nullable private DemoRoute route;
    private String destinationName = "";
    private float meters;
    private long lastFrame;
    private boolean playing;
    private boolean arrived;
    private boolean videoMode;
    private long retryUntil;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!playing || route == null) return;
            long now = SystemClock.elapsedRealtime();
            int speedUp = videoMode ? VIDEO_SPEED_UP : SPEED_UP;
            float metersPerMilli = CITY_SPEED_KPH / 3.6f / 1000f * speedUp;
            meters = Math.min(route.lengthMeters(), meters + (now - lastFrame) * metersPerMilli);
            lastFrame = now;
            render();
            if (meters >= route.lengthMeters()) {
                playing = false;
                updatePauseButton();
                if (videoMode) startRetrying();
                return;
            }
            handler.postDelayed(this, FRAME_MILLIS);
        }
    };

    /** Video mode: keeps trying a call that came due while the voice was busy, such as the arrival. */
    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            if (!isShowing() || playing) return;
            if (!speakDue() && SystemClock.elapsedRealtime() < retryUntil) {
                handler.postDelayed(this, RETRY_MILLIS);
            }
        }
    };

    public RouteDemoOverlay(View root, Speaker speaker) {
        this.root = root;
        this.speaker = speaker;
        map = root.findViewById(R.id.route_demo_map);
        destination = root.findViewById(R.id.route_demo_destination);
        turnIcon = root.findViewById(R.id.route_demo_turn_icon);
        instruction = root.findViewById(R.id.route_demo_instruction);
        remaining = root.findViewById(R.id.route_demo_remaining);
        pause = root.findViewById(R.id.route_demo_pause);
        notice = root.findViewById(R.id.route_demo_notice);
        panel = root.findViewById(R.id.route_demo_panel);
        turnIconSize = turnIcon.getLayoutParams().width;
        pause.setOnClickListener(v -> setPlaying(!playing));
        root.findViewById(R.id.route_demo_restart).setOnClickListener(v -> restart());
        root.findViewById(R.id.route_demo_close).setOnClickListener(v -> close());
    }

    public void show(Facility facility) {
        show(facility, java.util.Collections.emptyList());
    }

    /** @param pins places to mark on the map, such as police and fire stations */
    public void show(Facility facility, java.util.List<RouteMapView.Pin> pins) {
        map.setPins(pins);
        route = DemoRoute.plan(facility.distanceMeters, facility.bearingDegrees);
        destinationName = facility.name;
        destination.setText(facility.name);
        map.setRoute(route);
        root.setVisibility(View.VISIBLE);
        restart();
        // Video mode says this through RouteVoice, once the voice is free.
        if (!videoMode) speaker.say(root.getContext().getString(R.string.route_demo_start, facility.name));
    }

    /**
     * Video mode hides the "demo route" notice and shows a car navigation's driving screen:
     * first-person map, white on blue instructions in larger type, navigation-style calls.
     */
    public void setVideoMode(boolean on) {
        videoMode = on;
        notice.setVisibility(on ? View.GONE : View.VISIBLE);
        map.setFirstPerson(on);
        Context context = root.getContext();
        panel.setCardBackgroundColor(color(on ? R.color.primary : R.color.primary_container));
        ViewGroup.LayoutParams size = turnIcon.getLayoutParams();
        size.width = on ? context.getResources().getDimensionPixelSize(R.dimen.route_video_turn_icon) : turnIconSize;
        size.height = size.width;
        turnIcon.setLayoutParams(size);
        turnIcon.setBackgroundTintList(ColorStateList.valueOf(color(on ? R.color.on_primary : R.color.primary)));
        style(turnIcon, on ? R.style.TextAppearance_App_StepSymbol : R.style.TextAppearance_App_Headline,
                on ? R.color.primary : R.color.on_primary);
        style(instruction, on ? R.style.TextAppearance_App_Headline : R.style.TextAppearance_App_TitleLarge,
                on ? R.color.on_primary : R.color.on_primary_container);
        style(remaining, on ? R.style.TextAppearance_App_Title : R.style.TextAppearance_App_Body,
                on ? R.color.on_primary : R.color.on_primary_container);
    }

    private void style(TextView view, @StyleRes int appearance, @ColorRes int textColor) {
        view.setTextAppearance(appearance);
        view.setTextColor(color(textColor));
    }

    private int color(@ColorRes int id) {
        return ContextCompat.getColor(root.getContext(), id);
    }

    public boolean isShowing() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void close() {
        setPlaying(false);
        handler.removeCallbacks(retry);
        root.setVisibility(View.GONE);
    }

    private void restart() {
        meters = 0f;
        arrived = false;
        announced.clear();
        voice.reset();
        handler.removeCallbacks(retry);
        render();
        setPlaying(true);
    }

    private void setPlaying(boolean play) {
        playing = play && route != null && meters < route.lengthMeters();
        handler.removeCallbacks(frame);
        if (playing) {
            lastFrame = SystemClock.elapsedRealtime();
            handler.post(frame);
        }
        updatePauseButton();
    }

    private void updatePauseButton() {
        pause.setText(playing ? R.string.route_demo_pause : R.string.route_demo_play);
    }

    private void render() {
        if (route == null) return;
        Context context = root.getContext();
        map.setProgress(meters);

        float left = route.lengthMeters() - meters;
        int minutes = (int) Math.ceil(left / 1000f / CITY_SPEED_KPH * 60f);
        remaining.setText(context.getString(
                R.string.route_demo_remaining, formatDistance(context, left), Math.max(minutes, 0)));

        DemoRoute.Maneuver next = route.nextManeuver(meters);
        if (videoMode) speakDue();
        if (left <= 1f) {
            turnIcon.setText("●");
            instruction.setText(context.getString(R.string.route_demo_arrived, destinationName));
            if (!arrived) {
                arrived = true;
                if (!videoMode) speaker.say(instruction.getText().toString());
            }
            return;
        }
        if (next == null) {
            turnIcon.setText("↑");
            instruction.setText(context.getString(
                    R.string.route_demo_destination_ahead, formatDistance(context, left)));
            return;
        }
        float toTurn = next.atMeters - meters;
        boolean leftTurn = next.turn == DemoRoute.Turn.LEFT;
        turnIcon.setText(leftTurn ? "↰" : "↱");
        String text = context.getString(
                leftTurn ? R.string.route_demo_turn_left : R.string.route_demo_turn_right,
                formatDistance(context, toTurn));
        instruction.setText(text);
        if (!videoMode && toTurn <= ANNOUNCE_METERS && announced.add(next.atMeters)) speaker.say(text);
    }

    /**
     * Video mode: says the call due now if the voice is free. A call it could not say stays due
     * and is tried again on the next frame, with its distance brought up to date.
     *
     * @return true when nothing is left waiting
     */
    private boolean speakDue() {
        if (route == null) return true;
        RouteVoice.Call call = voice.due(route, meters);
        if (call == null) return true;
        if (!speaker.say(callText(call))) return false;
        voice.spoken(call);
        return voice.due(route, meters) == null;
    }

    private void startRetrying() {
        retryUntil = SystemClock.elapsedRealtime() + RETRY_FOR_MILLIS;
        handler.removeCallbacks(retry);
        handler.postDelayed(retry, RETRY_MILLIS);
    }

    private String callText(RouteVoice.Call call) {
        Context context = root.getContext();
        boolean leftTurn = call.turn == DemoRoute.Turn.LEFT;
        switch (call.kind) {
            case START:
                return context.getString(R.string.route_demo_start, destinationName);
            case TURN_AHEAD:
                return context.getString(
                        leftTurn ? R.string.route_voice_left_ahead : R.string.route_voice_right_ahead,
                        call.meters);
            case TURN_NOW:
                return context.getString(
                        leftTurn ? R.string.route_voice_left_now : R.string.route_voice_right_now);
            case ARRIVED:
            default:
                return context.getString(R.string.route_demo_arrived, destinationName);
        }
    }

    private static String formatDistance(Context context, float meters) {
        return meters >= 1000f
                ? context.getString(R.string.distance_km, meters / 1000f)
                : context.getString(R.string.distance_m, Math.round(meters / 10f) * 10);
    }
}
