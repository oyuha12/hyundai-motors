package io.github.oyuha12.hyundaimotors.facility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyPlan;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

public class SampleFacilityRepositoryTest {
    private static final double LATITUDE = 37.5;
    private static final double LONGITUDE = 127.0;

    private final SampleFacilityRepository repository = new SampleFacilityRepository();

    @Test
    public void results_coverEveryTypeNearestFirstAndAreMarkedAsSamples() {
        List<Facility> facilities = repository.findNearby(LATITUDE, LONGITUDE);
        EnumSet<Type> types = EnumSet.noneOf(Type.class);
        int previous = 0;
        for (Facility facility : facilities) {
            types.add(facility.type);
            assertTrue(facility.distanceMeters >= previous);
            assertTrue(facility.name.startsWith("샘플"));
            previous = facility.distanceMeters;
        }
        assertEquals(EnumSet.allOf(Type.class), types);
    }

    @Test
    public void coordinates_matchTheStatedDistance() {
        for (Facility facility : repository.findNearby(LATITUDE, LONGITUDE)) {
            double north = (facility.latitude - LATITUDE) * 111_320d;
            double east = (facility.longitude - LONGITUDE)
                    * 111_320d * Math.cos(Math.toRadians(LATITUDE));
            assertEquals(facility.distanceMeters, Math.hypot(north, east), 1d);
        }
    }

    @Test
    public void everySituation_hasAnEvacuationTargetAndHelp() {
        for (EmergencyType type : EmergencyType.values()) {
            assertNotNull(type.name(), repository.nearest(
                    EmergencyPlan.evacuationTarget(type), LATITUDE, LONGITUDE));
            List<Facility> help = repository.nearestOfEach(
                    EmergencyPlan.supportTypes(type), LATITUDE, LONGITUDE);
            assertFalse(type.name(), help.isEmpty());
            assertFalse(EmergencyPlan.steps(type).isEmpty());
        }
    }

    @Test
    public void everyStep_hasAPictureAndAMotion() {
        for (EmergencyType type : EmergencyType.values()) {
            for (io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep step
                    : EmergencyPlan.steps(type)) {
                assertTrue(type.name(), step.hasIcon() || (step.symbol != null && !step.symbol.isEmpty()));
                assertNotNull(type.name(), step.motion);
            }
        }
    }

    @Test
    public void fireAndFlood_sendPeopleToAnEvacuationSite() {
        assertEquals(Type.EVACUATION_SITE, EmergencyPlan.evacuationTarget(EmergencyType.FIRE));
        assertEquals(Type.EVACUATION_SITE, EmergencyPlan.evacuationTarget(EmergencyType.FLOOD));
        assertEquals(Type.FIRE_STATION, EmergencyPlan.supportTypes(EmergencyType.FIRE).get(0));
        assertEquals(Type.HOSPITAL, EmergencyPlan.supportTypes(EmergencyType.ACCIDENT).get(0));
    }
}
