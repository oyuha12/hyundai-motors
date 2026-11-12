package io.github.oyuha12.hyundaimotors.facility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import org.junit.Test;

public class ResponseEstimatorTest {
    private static Facility at(Type type, int meters) {
        return new Facility(type, "샘플", 0, 0, meters, 0, null);
    }

    @Test
    public void fireEngine2km_arrivesInAboutFiveMinutes() {
        // 60 s preparation + 2 km x 1.3 = 2.6 km at 40 km/h (234 s) = 294 s.
        assertEquals(294_000L, (long) ResponseEstimator.arrivalMillis(at(Type.FIRE_STATION, 2_000)));
        assertEquals(5, (int) ResponseEstimator.arrivalMinutes(at(Type.FIRE_STATION, 2_000)));
    }

    @Test
    public void fartherMeansLater() {
        for (Type type : Type.values()) {
            if (!ResponseEstimator.dispatches(type)) continue;
            assertTrue(type.name(), ResponseEstimator.arrivalMillis(at(type, 5_000))
                    > ResponseEstimator.arrivalMillis(at(type, 1_000)));
        }
    }

    @Test
    public void veryClose_isAtLeastOneMinute() {
        assertEquals(2, (int) ResponseEstimator.arrivalMinutes(at(Type.POLICE, 10)));
        assertTrue(ResponseEstimator.arrivalMinutes(at(Type.POLICE, 0)) >= 1);
    }

    @Test
    public void placesThatDoNotDispatch_haveNoEstimate() {
        assertFalse(ResponseEstimator.dispatches(Type.SAFE_STOP));
        assertFalse(ResponseEstimator.dispatches(Type.EVACUATION_SITE));
        assertNull(ResponseEstimator.arrivalMinutes(at(Type.SAFE_STOP, 300)));
        assertNull(ResponseEstimator.arrivalMinutes(at(Type.EVACUATION_SITE, 300)));
    }

    @Test
    public void emergencyServices_beatATowTruckAtTheSameDistance() {
        long tow = ResponseEstimator.arrivalMillis(at(Type.REPAIR_SHOP, 3_000));
        for (Type type : new Type[] {Type.FIRE_STATION, Type.POLICE, Type.HOSPITAL}) {
            assertTrue(type.name(), ResponseEstimator.arrivalMillis(at(type, 3_000)) < tow);
        }
    }
}
