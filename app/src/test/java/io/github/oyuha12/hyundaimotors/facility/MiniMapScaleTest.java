package io.github.oyuha12.hyundaimotors.facility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiniMapScaleTest {
    @Test
    public void ring_holdsTheFarthestPlace() {
        assertEquals(250, MiniMapScale.ringMeters(0));
        assertEquals(250, MiniMapScale.ringMeters(250));
        assertEquals(500, MiniMapScale.ringMeters(251));
        assertEquals(1_000, MiniMapScale.ringMeters(650));
        assertEquals(2_000, MiniMapScale.ringMeters(1_400));
        assertEquals(5_000, MiniMapScale.ringMeters(2_300));
        assertEquals(10_000, MiniMapScale.ringMeters(5_001));
        assertEquals(20_000, MiniMapScale.ringMeters(12_000));
        assertEquals(50_000, MiniMapScale.ringMeters(40_000));
        assertEquals(100_000, MiniMapScale.ringMeters(99_000));
    }

    /** North is up and east is right; screen y grows downward. */
    @Test
    public void offset_northUp() {
        assertOffset(0, -100, MiniMapScale.offset(0, 1_000, 0.1));
        assertOffset(100, 0, MiniMapScale.offset(90, 1_000, 0.1));
        assertOffset(0, 100, MiniMapScale.offset(180, 1_000, 0.1));
        assertOffset(-100, 0, MiniMapScale.offset(270, 1_000, 0.1));
        assertOffset(70.71f, -70.71f, MiniMapScale.offset(45, 1_000, 0.1));
    }

    private static void assertOffset(float x, float y, float[] offset) {
        assertEquals(x, offset[0], 0.01f);
        assertEquals(y, offset[1], 0.01f);
    }
}
