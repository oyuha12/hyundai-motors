package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Video mode: what the in-app route says, and when, like a car navigation. "안내를 시작합니다"
 * first, each turn twice (about 300 m before it, then just before it), and the arrival.
 *
 * <p>A call that is due stays due until it is spoken or overtaken, because the voice may be busy
 * with the situation guidance. Plain Java, so the timing can be checked in unit tests.
 */
public final class RouteVoice {
    public enum Kind { START, TURN_AHEAD, TURN_NOW, ARRIVED }

    /** The first call for a turn comes this far before it. */
    static final float AHEAD_METERS = 300f;
    /** The second call, "잠시 후 우회전입니다", comes this far before the turn. */
    static final float NOW_METERS = 60f;
    /** "안내를 시작합니다" is said only near the start; later a turn call is more useful. */
    static final float START_WITHIN_METERS = 80f;

    /** A call to make. */
    public static final class Call {
        public final Kind kind;
        /** The turn for turn calls, otherwise null. */
        @Nullable public final DemoRoute.Turn turn;
        /** Meters to the turn, rounded the way a navigation says them; 0 for other calls. */
        public final int meters;
        final float key;

        Call(Kind kind, @Nullable DemoRoute.Turn turn, int meters, float key) {
            this.kind = kind;
            this.turn = turn;
            this.meters = meters;
            this.key = key;
        }
    }

    private final Set<Float> ahead = new HashSet<>();
    private final Set<Float> now = new HashSet<>();
    private boolean started;
    private boolean arrived;

    /** From the start of the route again. */
    public void reset() {
        ahead.clear();
        now.clear();
        started = false;
        arrived = false;
    }

    /** The call due at this point of the route, or null when there is nothing to say. */
    @Nullable
    public Call due(DemoRoute route, float meters) {
        if (route.lengthMeters() - meters <= 1f) {
            return arrived ? null : new Call(Kind.ARRIVED, null, 0, route.lengthMeters());
        }
        if (!started && meters <= START_WITHIN_METERS) return new Call(Kind.START, null, 0, 0f);
        DemoRoute.Maneuver next = route.nextManeuver(meters);
        if (next == null) return null;
        float toTurn = next.atMeters - meters;
        if (toTurn <= NOW_METERS) {
            return now.contains(next.atMeters)
                    ? null
                    : new Call(Kind.TURN_NOW, next.turn, 0, next.atMeters);
        }
        if (toTurn <= AHEAD_METERS && !ahead.contains(next.atMeters)) {
            return new Call(Kind.TURN_AHEAD, next.turn, spokenMeters(toTurn), next.atMeters);
        }
        return null;
    }

    /** The call was said. */
    public void spoken(Call call) {
        switch (call.kind) {
            case START:
                started = true;
                break;
            case TURN_AHEAD:
                started = true;
                ahead.add(call.key);
                break;
            case TURN_NOW:
                started = true;
                ahead.add(call.key);
                now.add(call.key);
                break;
            case ARRIVED:
            default:
                arrived = true;
                break;
        }
    }

    /** 287 m → 300, 230 m → 250, 64 m → 60: steps of 50 m from 100 m up, else 10 m. */
    static int spokenMeters(float meters) {
        int step = meters >= 100f ? 50 : 10;
        return Math.round(meters / step) * step;
    }
}
