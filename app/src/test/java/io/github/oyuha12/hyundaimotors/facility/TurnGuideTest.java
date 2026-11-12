package io.github.oyuha12.hyundaimotors.facility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import ai.pleos.playground.navi.constants.TurnType;
import ai.pleos.playground.navi.data.TBTInfo;
import io.github.oyuha12.hyundaimotors.R;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TurnGuideTest {
    @Test
    public void everyKnownTurn_hasWords() {
        for (TurnType type : TurnType.values()) {
            if (type == TurnType.TURN_UNKNOWN) continue;
            assertNotEquals(type.name(), 0, TurnGuide.label(type));
        }
        assertEquals(0, TurnGuide.label(TurnType.TURN_UNKNOWN));
    }

    @Test
    public void commonTurns() {
        assertEquals(R.string.turn_right, TurnGuide.label(TurnType.TURN_RIGHT));
        assertEquals(R.string.turn_left, TurnGuide.label(TurnType.TURN_LEFT));
        assertEquals(R.string.turn_straight, TurnGuide.label(TurnType.TURN_STRAIGHT));
        assertEquals(R.string.turn_uturn, TurnGuide.label(TurnType.TURN_UTURN));
        assertEquals(R.string.turn_arrive, TurnGuide.label(TurnType.TURN_ARRIVE));
        assertEquals(R.string.turn_enter_roundabout,
                TurnGuide.label(TurnType.TURN_ENTER_ROUNDABOUT_06));
    }

    @Test
    public void next_isTheNearestAhead() {
        TBTInfo far = new TBTInfo(TurnType.TURN_LEFT, 800, "좌회전");
        TBTInfo near = new TBTInfo(TurnType.TURN_RIGHT, 300, "우회전");
        TBTInfo passed = new TBTInfo(TurnType.TURN_STRAIGHT, -10, "직진");
        assertSame(near, TurnGuide.next(Arrays.asList(far, passed, near)));
    }

    @Test
    public void next_noneWithoutTurns() {
        assertNull(TurnGuide.next(null));
        assertNull(TurnGuide.next(Collections.emptyList()));
    }
}
