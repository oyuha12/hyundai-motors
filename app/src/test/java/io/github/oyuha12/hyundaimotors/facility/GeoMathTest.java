package io.github.oyuha12.hyundaimotors.facility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Made-up coordinates only: the checks need geometry, not real places. */
public class GeoMathTest {
    /** One degree along a great circle: R × π / 180. */
    private static final double ONE_DEGREE_METERS = GeoMath.EARTH_RADIUS_METERS * Math.PI / 180;

    @Test
    public void distance_oneDegreeAlongTheEquatorAndAMeridian() {
        assertEquals(ONE_DEGREE_METERS, GeoMath.distanceMeters(0, 0, 0, 1), 0.01);
        assertEquals(ONE_DEGREE_METERS, GeoMath.distanceMeters(0, 0, 1, 0), 0.01);
        assertEquals(111_195, GeoMath.distanceMeters(0, 0, 0, 1), 1);
    }

    @Test
    public void distance_samePointIsZero_andSymmetric() {
        assertEquals(0, GeoMath.distanceMeters(37.5, 127, 37.5, 127), 1e-9);
        assertEquals(
                GeoMath.distanceMeters(37.5, 127, 37.51, 127.02),
                GeoMath.distanceMeters(37.51, 127.02, 37.5, 127), 1e-6);
    }

    /** A degree of longitude shrinks with cos(latitude). */
    @Test
    public void distance_longitudeShrinksAwayFromTheEquator() {
        double atSixty = GeoMath.distanceMeters(60, 0, 60, 0.01);
        assertEquals(ONE_DEGREE_METERS * 0.01 * 0.5, atSixty, 0.5);
    }

    @Test
    public void distance_oneKilometerNorth() {
        double degrees = 1_000 / ONE_DEGREE_METERS;
        assertEquals(1_000, GeoMath.distanceMeters(37.5, 127, 37.5 + degrees, 127), 0.01);
        assertEquals(0, GeoMath.bearingDegrees(37.5, 127, 37.5 + degrees, 127), 1e-6);
    }

    @Test
    public void distance_acrossTheDateLine() {
        assertEquals(ONE_DEGREE_METERS * 0.2, GeoMath.distanceMeters(0, 179.9, 0, -179.9), 0.01);
        assertEquals(90, GeoMath.bearingDegrees(0, 179.9, 0, -179.9), 1e-6);
    }

    @Test
    public void bearing_compassPoints() {
        assertEquals(0, GeoMath.bearingDegrees(0, 0, 1, 0), 1e-9);
        assertEquals(90, GeoMath.bearingDegrees(0, 0, 0, 1), 1e-9);
        assertEquals(180, GeoMath.bearingDegrees(0, 0, -1, 0), 1e-9);
        assertEquals(270, GeoMath.bearingDegrees(0, 0, 0, -1), 1e-9);
    }

    @Test
    public void bearing_northEastAndNorthWest() {
        assertEquals(45, GeoMath.bearingDegrees(0, 0, 0.001, 0.001), 0.01);
        assertEquals(315, GeoMath.bearingDegrees(0, 0, 0.001, -0.001), 0.01);
    }

    @Test
    public void bearing_alwaysInRange() {
        for (int lat = -60; lat <= 60; lat += 30) {
            for (int lon = -170; lon <= 170; lon += 85) {
                double bearing = GeoMath.bearingDegrees(10, 20, lat, lon);
                assertTrue(bearing >= 0 && bearing < 360);
            }
        }
    }
}
