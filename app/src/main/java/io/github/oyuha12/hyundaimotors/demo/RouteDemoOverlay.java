package io.github.oyuha12.hyundaimotors.demo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import java.util.HashSet;
import java.util.Set;

/**
 * Plays a demo route at {@link #SPEED_UP} times real speed, with turn instructions on screen and
 * spoken through the given callback. For demo videos only; real routes use the vehicle's
 * navigation app.
 */
public final class RouteDemoOverlay {
    public interface Speaker {
        void say(String text);
    }

    /** Simulated driving speed for the remaining-time estimate, km/h. */
    static final float CITY_SPEED_KPH = 40f;
    /** Playback runs this many times faster than real driving. */
    public static final int SPEED_UP = 10;
    /** Announce a turn when it is this close. */
    static final float ANNOUNCE_METERS = 150f;
    private static final long FRAME_MILLIS = 50L;

    private final View root;
    private final RouteMapView map;
    private final TextView destination;
    private final TextView turnIcon;
    private final TextView instruction;
    private final TextView remaining;
    private final MaterialButton pause;
    private final View notice;
    private final Speaker speaker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Float> announced = new HashSet<>();

    @Nullable private DemoRoute route;
    private String destinationName = "";
    private float meters;
    private long lastFrame;
    private boolean playing;
    private boolean arrived;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!playing || route == null) return;
            long now = SystemClock.elapsedRealtime();
            float metersPerMilli = CITY_SPEED_KPH / 3.6f / 1000f * SPEED_UP;
            meters = Math.min(route.lengthMeters(), meters + (now - lastFrame) * metersPerMilli);
            lastFrame = now;
            render();
            if (meters >= route.lengthMeters()) {
                playing = false;
                updatePauseButton();
                return;
            }
            handler.postDelayed(this, FRAME_MILLIS);
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
        pause.setOnClickListener(v -> setPlaying(!playing));
        root.findViewById(R.id.route_demo_restart).setOnClickListener(v -> restart());
        root.findViewById(R.id.route_demo_close).setOnClickListener(v -> close());
    }

    public void show(Facility facility) {
        route = DemoRoute.plan(facility.distanceMeters, facility.bearingDegrees);
        destinationName = facility.name;
        destination.setText(facility.name);
        map.setRoute(route);
        root.setVisibility(View.VISIBLE);
        restart();
        speaker.say(root.getContext().getString(R.string.route_demo_start, facility.name));
    }

    /** Video mode hides the "demo route" notice. */
    public void setVideoMode(boolean on) {
        notice.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    public boolean isShowing() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void close() {
        setPlaying(false);
        root.setVisibility(View.GONE);
    }

    private void restart() {
        meters = 0f;
        arrived = false;
        announced.clear();
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
        if (left <= 1f) {
            turnIcon.setText("●");
            instruction.setText(context.getString(R.string.route_demo_arrived, destinationName));
            if (!arrived) {
                arrived = true;
                speaker.say(instruction.getText().toString());
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
        if (toTurn <= ANNOUNCE_METERS && announced.add(next.atMeters)) speaker.say(text);
    }

    private static String formatDistance(Context context, float meters) {
        return meters >= 1000f
                ? context.getString(R.string.distance_km, meters / 1000f)
                : context.getString(R.string.distance_m, Math.round(meters / 10f) * 10);
    }
}
