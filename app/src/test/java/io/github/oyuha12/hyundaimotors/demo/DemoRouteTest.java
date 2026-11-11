package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.demo.DemoRoute.Turn;
import org.junit.Test;

public class DemoRouteTest {
    @Test
    public void diagonalTarget_getsTwoTurnsAndEndsAtTheTarget() {
        // 1000 m to the north-east.
        DemoRoute route = DemoRoute.plan(1_000, 45);
        assertEquals(4, route.points().size());
        DemoRoute.Point end = route.positionAt(route.lengthMeters());
        assertEquals(707.1f, end.x, 0.5f);
        assertEquals(707.1f, end.y, 0.5f);
        // Grid route: east plus north legs.
        assertEquals(1_414.2f, route.lengthMeters(), 0.5f);
        assertEquals(2, route.maneuvers().size());
        assertEquals(Turn.RIGHT, route.maneuvers().get(0).turn); // north, then east
        assertEquals(Turn.LEFT, route.maneuvers().get(1).turn); // east, then north
    }

    @Test
    public void nearlyStraightTarget_isOneLegWithoutTurns() {
        DemoRoute route = DemoRoute.plan(800, 5);
        assertEquals(2, route.points().size());
        assertTrue(route.maneuvers().isEmpty());
    }

    @Test
    public void westernTarget_turnsLeftFirst() {
        DemoRoute route = DemoRoute.plan(1_000, 320);
        assertEquals(Turn.LEFT, route.maneuvers().get(0).turn); // north, then west
    }

    @Test
    public void position_andHeading_followTheRoute() {
        DemoRoute route = DemoRoute.plan(1_000, 45);
        DemoRoute.Point quarter = route.positionAt(200f);
        assertEquals(0f, quarter.x, 0.01f);
        assertEquals(200f, quarter.y, 0.01f);
        assertEquals(0f, route.headingAt(200f), 0.01f); // north
        assertEquals(90f, route.headingAt(500f), 0.01f); // east
    }

    @Test
    public void nextManeuver_counts_downTheTurns() {
        DemoRoute route = DemoRoute.plan(1_000, 45);
        assertEquals(353.6f, route.nextManeuver(0f).atMeters, 0.5f);
        assertEquals(Turn.LEFT, route.nextManeuver(400f).turn);
        assertNull(route.nextManeuver(route.lengthMeters() - 1f));
    }

    @Test
    public void positionBeyondTheEnds_isClamped() {
        DemoRoute route = DemoRoute.plan(500, 90);
        assertEquals(0f, route.positionAt(-50f).x, 0.01f);
        assertEquals(500f, route.positionAt(9_999f).x, 0.5f);
    }
}
