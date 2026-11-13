package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class FirstPersonCameraTest {
    private static final float WIDTH = 1600f;
    private static final float HEIGHT = 900f;

    private final FirstPersonCamera camera = new FirstPersonCamera();
    private final float[] out = new float[2 * (FirstPersonCamera.MAX_CORNERS + 2)];

    @Before
    public void setUp() {
        camera.setView(WIDTH, HEIGHT);
    }

    @Test
    public void car_sitsLowInTheMiddle_withTheHorizonNearTheTop() {
        camera.follow(120f, -40f, 73f);
        camera.project(120f, -40f, out, 0);
        assertEquals(WIDTH / 2f, out[0], 0.01f);
        assertEquals(FirstPersonCamera.CAR * HEIGHT, out[1], 0.01f);
        assertEquals(FirstPersonCamera.HORIZON * HEIGHT, camera.horizonY(), 0.01f);
        // One view height across, at the car, covers the set number of meters.
        assertEquals(HEIGHT / FirstPersonCamera.METERS_PER_HEIGHT_AT_CAR, camera.pixelsPerMeter(0f), 0.001f);
    }

    @Test
    public void directionOfTravel_isUp_andRightIsRight() {
        camera.follow(0f, 0f, 90f); // Heading east
        camera.project(100f, 0f, out, 0); // 100 m ahead
        assertEquals(WIDTH / 2f, out[0], 0.01f);
        assertTrue(out[1] < FirstPersonCamera.CAR * HEIGHT);
        assertTrue(out[1] > camera.horizonY());
        camera.project(0f, -20f, out, 0); // 20 m south: on the right when heading east
        assertTrue(out[0] > WIDTH / 2f);
        assertEquals(100f, camera.ahead(100f, 0f), 0.01f);
        assertEquals(20f, camera.across(0f, -20f), 0.01f);
    }

    @Test
    public void fartherAhead_isHigherAndSmaller_towardTheHorizon() {
        camera.follow(0f, 0f, 0f);
        float previousY = Float.MAX_VALUE;
        float previousScale = Float.MAX_VALUE;
        for (float forward = 0f; forward <= FirstPersonCamera.FAR_METERS; forward += 100f) {
            camera.project(0f, forward, out, 0);
            assertTrue(out[1] < previousY);
            assertTrue(out[1] > camera.horizonY());
            assertTrue(camera.pixelsPerMeter(forward) < previousScale);
            assertEquals(out[1], camera.screenYAhead(forward), 0.01f);
            previousY = out[1];
            previousScale = camera.pixelsPerMeter(forward);
        }
    }

    @Test
    public void nearLimit_isBelowTheBottomEdge() {
        camera.follow(0f, 0f, 0f);
        assertTrue(camera.screenYAhead(FirstPersonCamera.NEAR_METERS) > HEIGHT);
    }

    @Test
    public void polygon_behindTheCamera_isCutAtTheNearLimit() {
        camera.follow(0f, 0f, 0f);
        // A road from 500 m behind to 100 m ahead: only the part from the near limit is drawn.
        float[] road = {-5f, -500f, 5f, -500f, 5f, 100f, -5f, 100f};
        int n = camera.clipAndProject(road, 4, out);
        assertEquals(4, n);
        float lowest = 0f;
        for (int i = 0; i < n; i++) lowest = Math.max(lowest, out[2 * i + 1]);
        assertEquals(camera.screenYAhead(FirstPersonCamera.NEAR_METERS), lowest, 0.01f);
    }

    @Test
    public void polygon_crossingBothLimits_getsBothCuts() {
        camera.follow(0f, 0f, 0f);
        float[] road = {-5f, -900f, 5f, -900f, 5f, 2_000f, -5f, 2_000f};
        assertEquals(4, camera.clipAndProject(road, 4, out));
        float highest = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) highest = Math.min(highest, out[2 * i + 1]);
        assertEquals(camera.screenYAhead(FirstPersonCamera.FAR_METERS), highest, 0.01f);
    }

    @Test
    public void polygon_withACornerCut_gainsACorner() {
        camera.follow(0f, 0f, 0f);
        // A triangle with one corner behind the near limit becomes a four-sided shape.
        float[] triangle = {-10f, -100f, 10f, 50f, -10f, 50f};
        assertEquals(4, camera.clipAndProject(triangle, 3, out));
    }

    @Test
    public void polygon_entirelyBehindOrBeyond_isNotDrawn() {
        camera.follow(0f, 0f, 0f);
        float[] behind = {-5f, -300f, 5f, -300f, 5f, -100f, -5f, -100f};
        assertEquals(0, camera.clipAndProject(behind, 4, out));
        float[] beyond = {-5f, 800f, 5f, 800f, 5f, 900f, -5f, 900f};
        assertEquals(0, camera.clipAndProject(beyond, 4, out));
    }

    @Test
    public void quickCheck_rulesOutOnlyWhatIsSurelyOutOfView() {
        camera.follow(0f, 0f, 0f);
        assertTrue(camera.mayBeVisible(0f, 200f, 0f));
        assertFalse(camera.mayBeVisible(0f, -400f, 100f)); // Far behind
        assertFalse(camera.mayBeVisible(0f, 1_000f, 100f)); // Beyond the far limit
        assertFalse(camera.mayBeVisible(3_000f, 50f, 100f)); // Far to the side
        // Just off the side edge but within the margin: kept.
        float edge = WIDTH / 2f / camera.pixelsPerMeter(100f);
        assertTrue(camera.mayBeVisible(edge + 50f, 100f, 100f));
    }
}
