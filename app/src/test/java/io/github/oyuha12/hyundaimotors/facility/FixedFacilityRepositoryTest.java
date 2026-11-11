package io.github.oyuha12.hyundaimotors.facility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Made-up names and coordinates only, offset from the equator so the numbers are easy. */
public class FixedFacilityRepositoryTest {
    private static final double KM = 1_000 / (GeoMath.EARTH_RADIUS_METERS * Math.PI / 180);

    private final FacilityRepository places = new FixedFacilityRepository()
            .add(Type.FIRE_STATION, "fire", 0, 3 * KM) // 3 km east
            .add(Type.POLICE, "police", -1 * KM, 0) // 1 km south
            .add(Type.EVACUATION_SITE, "shelter", 2 * KM, 0); // 2 km north

    @Test
    public void nearestFirst_withDistanceAndBearing() {
        List<Facility> found = places.findNearby(0, 0);
        assertEquals("police", found.get(0).name);
        assertEquals(1_000, found.get(0).distanceMeters);
        assertEquals(180, found.get(0).bearingDegrees);
        assertEquals("shelter", found.get(1).name);
        assertEquals(2_000, found.get(1).distanceMeters);
        assertEquals(0, found.get(1).bearingDegrees);
        assertEquals("fire", found.get(2).name);
        assertEquals(3_000, found.get(2).distanceMeters);
        assertEquals(90, found.get(2).bearingDegrees);
    }

    /** Distance and direction follow the car. */
    @Test
    public void measuredFromTheCurrentPosition() {
        Facility police = places.nearest(Type.POLICE, -2 * KM, 0);
        assertEquals(1_000, police.distanceMeters);
        assertEquals(0, police.bearingDegrees);
    }

    @Test
    public void nearestOfEach_skipsKindsWithNone() {
        List<Facility> found = places.nearestOfEach(
                Arrays.asList(Type.HOSPITAL, Type.POLICE, Type.FIRE_STATION), 0, 0);
        assertEquals(2, found.size());
        assertEquals(Type.POLICE, found.get(0).type);
        assertEquals(Type.FIRE_STATION, found.get(1).type);
        assertNull(places.nearest(Type.HOSPITAL, 0, 0));
    }

    /** Every kind keeps its line on the status screen, set or not. */
    @Test
    public void statusLines_keepUnsetKinds() {
        List<StatusPlace> lines = StatusPlace.listFor(
                places, Arrays.asList(Type.POLICE, Type.HOSPITAL, Type.FIRE_STATION), 0, 0);
        assertEquals(3, lines.size());
        assertEquals("police", lines.get(0).facility.name);
        assertSame(Type.HOSPITAL, lines.get(1).type);
        assertNull(lines.get(1).facility);
        assertEquals("fire", lines.get(2).facility.name);
    }

    /** The video places feed the same arrival estimate as the sample facilities. */
    @Test
    public void arrivalEstimate_fromTheComputedDistance() {
        Facility fire = places.nearest(Type.FIRE_STATION, 0, 0);
        // 60 s to leave, then 3 km × 1.3 at 40 km/h = 351 s.
        assertEquals(411_000L, (long) ResponseEstimator.arrivalMillis(fire));
    }
}
