package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A made-up street route for demo videos on a PC emulator, where the vehicle's navigation app
 * cannot load maps. It follows a simple city grid from the car to the destination and turns at
 * right angles, so it is a picture of "a route", not a real one.
 *
 * <p>Coordinates are meters: x to the east, y to the north, with the car at (0, 0).
 */
public final class DemoRoute {
    public enum Turn { LEFT, RIGHT }

    public static final class Point {
        public final float x;
        public final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /** A turn at a corner of the route. */
    public static final class Maneuver {
        public final Turn turn;
        /** Distance from the start of the route to the corner, meters. */
        public final float atMeters;

        Maneuver(Turn turn, float atMeters) {
            this.turn = turn;
            this.atMeters = atMeters;
        }
    }

    /** A leg shorter than this share of the distance is dropped, to avoid tiny zig-zags. */
    static final float MIN_LEG_SHARE = 0.15f;

    private final List<Point> points;
    private final List<Maneuver> maneuvers;
    private final float[] cumulative;

    private DemoRoute(List<Point> points) {
        this.points = Collections.unmodifiableList(points);
        cumulative = new float[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + distance(points.get(i - 1), points.get(i));
        }
        List<Maneuver> turns = new ArrayList<>();
        for (int i = 1; i < points.size() - 1; i++) {
            Point a = points.get(i - 1), b = points.get(i), c = points.get(i + 1);
            float cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
            turns.add(new Maneuver(cross > 0 ? Turn.LEFT : Turn.RIGHT, cumulative[i]));
        }
        maneuvers = Collections.unmodifiableList(turns);
    }

    /**
     * Plans a grid route to a place at the given straight-line distance and compass bearing:
     * half the north-south leg, the whole east-west leg, then the rest of north-south.
     */
    public static DemoRoute plan(int distanceMeters, int bearingDegrees) {
        double radians = Math.toRadians(bearingDegrees);
        float east = (float) (distanceMeters * Math.sin(radians));
        float north = (float) (distanceMeters * Math.cos(radians));
        float minLeg = distanceMeters * MIN_LEG_SHARE;
        if (Math.abs(east) < minLeg) east = 0f;
        if (Math.abs(north) < minLeg) north = 0f;

        List<Point> points = new ArrayList<>();
        points.add(new Point(0f, 0f));
        if (east == 0f || north == 0f) {
            points.add(new Point(east, north));
        } else {
            points.add(new Point(0f, north / 2f));
            points.add(new Point(east, north / 2f));
            points.add(new Point(east, north));
        }
        return new DemoRoute(points);
    }

    public List<Point> points() {
        return points;
    }

    public List<Maneuver> maneuvers() {
        return maneuvers;
    }

    public float lengthMeters() {
        return cumulative[cumulative.length - 1];
    }

    /** Position after driving the given distance, clamped to the route. */
    public Point positionAt(float meters) {
        float along = Math.max(0f, Math.min(meters, lengthMeters()));
        for (int i = 1; i < points.size(); i++) {
            if (along <= cumulative[i] || i == points.size() - 1) {
                Point a = points.get(i - 1), b = points.get(i);
                float leg = cumulative[i] - cumulative[i - 1];
                float t = leg == 0f ? 1f : (along - cumulative[i - 1]) / leg;
                return new Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
            }
        }
        return points.get(0);
    }

    /** Direction of travel at the given distance, degrees clockwise from north. */
    public float headingAt(float meters) {
        float along = Math.max(0f, Math.min(meters, lengthMeters()));
        for (int i = 1; i < points.size(); i++) {
            if (along < cumulative[i] || i == points.size() - 1) {
                Point a = points.get(i - 1), b = points.get(i);
                return (float) ((Math.toDegrees(Math.atan2(b.x - a.x, b.y - a.y)) + 360.0) % 360.0);
            }
        }
        return 0f;
    }

    /**
     * Direction for a view that follows the car: the leg's direction, turning smoothly from one
     * leg to the next over {@code blendMeters} before and after each corner, so the view does not
     * jump a quarter turn at once.
     */
    public float smoothHeadingAt(float meters, float blendMeters) {
        float along = Math.max(0f, Math.min(meters, lengthMeters()));
        for (int i = 1; i < points.size() - 1; i++) {
            float corner = cumulative[i];
            if (Math.abs(along - corner) >= blendMeters) continue;
            float before = legHeading(i - 1);
            float after = legHeading(i);
            float t = (along - (corner - blendMeters)) / (2f * blendMeters);
            float eased = t * t * (3f - 2f * t);
            float turn = ((after - before + 540f) % 360f) - 180f;
            return (before + turn * eased + 360f) % 360f;
        }
        return headingAt(along);
    }

    private float legHeading(int leg) {
        Point a = points.get(leg), b = points.get(leg + 1);
        return (float) ((Math.toDegrees(Math.atan2(b.x - a.x, b.y - a.y)) + 360.0) % 360.0);
    }

    /** The next turn still ahead, or null if only the destination remains. */
    @Nullable
    public Maneuver nextManeuver(float meters) {
        for (Maneuver maneuver : maneuvers) {
            if (maneuver.atMeters > meters) return maneuver;
        }
        return null;
    }

    private static float distance(Point a, Point b) {
        return (float) Math.hypot(b.x - a.x, b.y - a.y);
    }
}
