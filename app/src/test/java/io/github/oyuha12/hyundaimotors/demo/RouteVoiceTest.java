package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import io.github.oyuha12.hyundaimotors.demo.DemoRoute.Turn;
import io.github.oyuha12.hyundaimotors.demo.RouteVoice.Call;
import io.github.oyuha12.hyundaimotors.demo.RouteVoice.Kind;
import org.junit.Test;

public class RouteVoiceTest {
    // North 353.6 m, right turn, east 707.1 m, left turn, north 353.6 m.
    private final DemoRoute route = DemoRoute.plan(1_000, 45);
    private final RouteVoice voice = new RouteVoice();

    private Call sayDue(float meters) {
        Call call = voice.due(route, meters);
        if (call != null) voice.spoken(call);
        return call;
    }

    @Test
    public void startThenEachTurnTwice_thenArrival() {
        assertSame(Kind.START, sayDue(0f).kind);
        assertNull(sayDue(10f)); // The first turn is still more than 300 m away.

        Call ahead = sayDue(100f);
        assertSame(Kind.TURN_AHEAD, ahead.kind);
        assertSame(Turn.RIGHT, ahead.turn);
        assertEquals(250, ahead.meters); // 253.6 m, said in steps of 50 m
        assertNull(sayDue(150f));

        Call now = sayDue(300f);
        assertSame(Kind.TURN_NOW, now.kind);
        assertSame(Turn.RIGHT, now.turn);
        assertNull(sayDue(340f));

        assertSame(Turn.LEFT, sayDue(800f).turn);
        assertSame(Kind.TURN_NOW, sayDue(1_010f).kind);

        assertSame(Kind.ARRIVED, sayDue(route.lengthMeters()).kind);
        assertNull(sayDue(route.lengthMeters()));
    }

    @Test
    public void callNotSaid_staysDue_withItsDistanceUpToDate() {
        voice.spoken(voice.due(route, 0f));
        assertEquals(300, voice.due(route, 60f).meters); // 293.6 m
        // The voice was busy; a little later the same call comes with the new distance.
        assertEquals(250, voice.due(route, 110f).meters);
    }

    @Test
    public void startIsDropped_onceTheCarIsUnderway() {
        assertSame(Kind.START, voice.due(route, 50f).kind);
        // Held back past the start: the turn call is more useful now.
        assertSame(Kind.TURN_AHEAD, voice.due(route, 100f).kind);
    }

    @Test
    public void missedFirstCall_isNotSaidAfterTheSecond() {
        voice.spoken(voice.due(route, 0f));
        assertSame(Kind.TURN_NOW, sayDue(320f).kind);
        assertNull(sayDue(330f));
    }

    @Test
    public void reset_startsOver() {
        sayDue(0f);
        sayDue(100f);
        voice.reset();
        assertSame(Kind.START, voice.due(route, 0f).kind);
    }

    @Test
    public void spokenMeters_roundLikeANavigation() {
        assertEquals(300, RouteVoice.spokenMeters(287f));
        assertEquals(250, RouteVoice.spokenMeters(230f));
        assertEquals(100, RouteVoice.spokenMeters(100f));
        assertEquals(60, RouteVoice.spokenMeters(64f));
    }
}
