package io.github.oyuha12.hyundaimotors.demo;

/**
 * The view of a car navigation in driving mode: from above and behind the car, looking ahead
 * and down, turned so the direction of travel is up. Projects points on the ground (meters, x to
 * the east and y to the north, as in {@link DemoRoute}) onto the screen.
 *
 * <p>Plain Java so the geometry can be checked in unit tests. The look is set by where the
 * horizon and the car sit on the screen, so it is the same at any view size.
 */
public final class FirstPersonCamera {
    /** The camera looks this far below the horizontal. */
    static final float PITCH_DEGREES = 30f;
    /** Focal length, in view heights. */
    static final float FOCAL = 0.9f;
    /** The horizon line, as a share of the view height from the top. */
    static final float HORIZON = 0.08f;
    /** The car, as a share of the view height from the top. */
    static final float CAR = 0.8f;
    /** At the car, one view height across the screen covers this many meters. */
    static final float METERS_PER_HEIGHT_AT_CAR = 130f;
    /** Nothing closer than this, meters behind the car, is drawn; the bottom edge is nearer. */
    public static final float NEAR_METERS = -40f;
    /** Nothing beyond this, meters ahead of the car, is drawn; fog covers the cut. */
    public static final float FAR_METERS = 700f;
    /** {@link #clipAndProject} takes polygons with up to this many corners. */
    public static final int MAX_CORNERS = 8;

    // Scratch space for clipAndProject, which runs for every block on every frame.
    private final float[] local = new float[(MAX_CORNERS + 2) * 2];
    private final float[] cut = new float[(MAX_CORNERS + 2) * 2];

    private final float tanPitch = (float) Math.tan(Math.toRadians(PITCH_DEGREES));
    private final float sinPitch = (float) Math.sin(Math.toRadians(PITCH_DEGREES));
    private final float cosPitch = (float) Math.cos(Math.toRadians(PITCH_DEGREES));
    /** Camera height and distance behind the car, meters. */
    private final float height;
    private final float behind;

    private float width;
    private float focal;
    private float centerY;
    private float carX;
    private float carY;
    private float sinHeading;
    private float cosHeading = 1f;

    public FirstPersonCamera() {
        // The car is seen this far below the middle of the view.
        double belowAxis = Math.atan((CAR - HORIZON - FOCAL * tanPitch) / FOCAL);
        double belowHorizontal = Math.toRadians(PITCH_DEGREES) + belowAxis;
        // Depth at which one meter across is one view height / METERS_PER_HEIGHT_AT_CAR.
        double distance = FOCAL * METERS_PER_HEIGHT_AT_CAR / Math.cos(belowAxis);
        height = (float) (distance * Math.sin(belowHorizontal));
        behind = (float) (distance * Math.cos(belowHorizontal));
    }

    /** The view size in pixels. */
    public void setView(float widthPixels, float heightPixels) {
        width = widthPixels;
        focal = FOCAL * heightPixels;
        centerY = HORIZON * heightPixels + focal * tanPitch;
    }

    /** Puts the car at the given point, facing the given direction (degrees from north). */
    public void follow(float x, float y, float headingDegrees) {
        carX = x;
        carY = y;
        double radians = Math.toRadians(headingDegrees);
        sinHeading = (float) Math.sin(radians);
        cosHeading = (float) Math.cos(radians);
    }

    /** Meters ahead of the car along its direction; negative behind it. */
    public float ahead(float x, float y) {
        return (x - carX) * sinHeading + (y - carY) * cosHeading;
    }

    /** Meters to the right of the car's direction; negative to its left. */
    public float across(float x, float y) {
        return (x - carX) * cosHeading - (y - carY) * sinHeading;
    }

    /**
     * Screen position of a ground point into {@code out[offset]} and {@code out[offset + 1]}.
     * Only meaningful between {@link #NEAR_METERS} and {@link #FAR_METERS} ahead of the car.
     */
    public void project(float x, float y, float[] out, int offset) {
        projectLocal(across(x, y), ahead(x, y), out, offset);
    }

    private void projectLocal(float right, float forward, float[] out, int offset) {
        float z = forward + behind;
        float depth = z * cosPitch + height * sinPitch;
        float down = height * cosPitch - z * sinPitch;
        out[offset] = width / 2f + focal * right / depth;
        out[offset + 1] = centerY + focal * down / depth;
    }

    /** Screen height of the ground straight ahead at this distance, in pixels from the top. */
    public float screenYAhead(float forward) {
        float z = forward + behind;
        return centerY + focal * (height * cosPitch - z * sinPitch) / (z * cosPitch + height * sinPitch);
    }

    /**
     * A quick check before the exact one: whether anything within {@code margin} meters of the
     * point can be in view. False only when it surely is not.
     */
    public boolean mayBeVisible(float x, float y, float margin) {
        float forward = ahead(x, y);
        if (forward < NEAR_METERS - margin || forward > FAR_METERS + margin) return false;
        float halfWidth = width / 2f / pixelsPerMeter(Math.max(forward, NEAR_METERS));
        return Math.abs(across(x, y)) <= halfWidth + margin;
    }

    /** Pixels per meter, across the screen, at a point this far ahead of the car. */
    public float pixelsPerMeter(float forward) {
        float z = forward + behind;
        return focal / (z * cosPitch + height * sinPitch);
    }

    /** Screen height of the horizon, in pixels from the top. */
    public float horizonY() {
        return centerY - focal * tanPitch;
    }

    /**
     * Cuts a ground polygon to the part between {@link #NEAR_METERS} and {@link #FAR_METERS}
     * ahead of the car and projects it, so nothing behind the camera folds onto the screen.
     *
     * @param ground x, y pairs of a convex polygon's corners, in order, at most {@link #MAX_CORNERS}
     * @param out receives x, y screen pairs; needs room for {@code corners + 2} pairs
     * @return the number of corners written, 0 when nothing of the polygon is in view
     */
    public int clipAndProject(float[] ground, int corners, float[] out) {
        if (corners > MAX_CORNERS) throw new IllegalArgumentException("too many corners");
        for (int i = 0; i < corners; i++) {
            local[2 * i] = across(ground[2 * i], ground[2 * i + 1]);
            local[2 * i + 1] = ahead(ground[2 * i], ground[2 * i + 1]);
        }
        int n = clip(local, corners, cut, NEAR_METERS, true);
        n = clip(cut, n, local, FAR_METERS, false);
        for (int i = 0; i < n; i++) projectLocal(local[2 * i], local[2 * i + 1], out, 2 * i);
        return n;
    }

    /** One step of Sutherland–Hodgman: keeps the side of forward = limit given by keepAbove. */
    private static int clip(float[] in, int count, float[] out, float limit, boolean keepAbove) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            float ax = in[2 * i], ay = in[2 * i + 1];
            int j = (i + 1) % count;
            float bx = in[2 * j], by = in[2 * j + 1];
            boolean aIn = keepAbove ? ay >= limit : ay <= limit;
            boolean bIn = keepAbove ? by >= limit : by <= limit;
            if (aIn) {
                out[2 * n] = ax;
                out[2 * n + 1] = ay;
                n++;
            }
            if (aIn != bIn) {
                float t = (limit - ay) / (by - ay);
                out[2 * n] = ax + (bx - ax) * t;
                out[2 * n + 1] = limit;
                n++;
            }
        }
        return n;
    }
}
