package io.github.oyuha12.hyundaimotors.facility;

/**
 * Geometry for the mini map on the status screen: the car in the middle, places around it by
 * direction and distance, north up. No map data is involved.
 */
public final class MiniMapScale {
    /** Ring sizes follow 1, 2, 5 steps: 250 m, 500 m, 1 km, 2 km, 5 km, 10 km and so on. */
    private static final int[] STEPS = {1, 2, 5};
    private static final int SMALLEST_RING_METERS = 250;

    private MiniMapScale() {}

    /** The outer ring's radius: the smallest step that still holds the farthest place. */
    public static int ringMeters(int farthestMeters) {
        if (farthestMeters <= SMALLEST_RING_METERS) return SMALLEST_RING_METERS;
        if (farthestMeters <= 500) return 500;
        long decade = 1_000;
        while (true) {
            for (int step : STEPS) {
                long ring = step * decade;
                if (farthestMeters <= ring) return (int) ring;
            }
            decade *= 10;
        }
    }

    /**
     * Where a place appears relative to the car, in pixels: x grows to the east and y to the
     * south, as on screen.
     */
    public static float[] offset(
            double bearingDegrees, double distanceMeters, double pixelsPerMeter) {
        double radians = Math.toRadians(bearingDegrees);
        double pixels = distanceMeters * pixelsPerMeter;
        float east = (float) (Math.sin(radians) * pixels);
        float south = (float) (-Math.cos(radians) * pixels);
        return new float[] {east, south};
    }
}
