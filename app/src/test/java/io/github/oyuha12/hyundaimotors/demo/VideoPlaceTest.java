package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import io.github.oyuha12.hyundaimotors.facility.FacilityRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Made-up names and coordinates only, to check how a filled-in entry is used. */
public class VideoPlaceTest {
    @Test
    public void set_onlyWithNameAndBothCoordinates() {
        assertFalse(new VideoPlace(Type.POLICE, null, null, null).isSet());
        assertFalse(new VideoPlace(Type.POLICE, "A", null, null).isSet());
        assertFalse(new VideoPlace(Type.POLICE, "A", 1.0, null).isSet());
        assertFalse(new VideoPlace(Type.POLICE, null, 1.0, 2.0).isSet());
        assertFalse(new VideoPlace(Type.POLICE, "  ", 1.0, 2.0).isSet());
        assertTrue(new VideoPlace(Type.POLICE, "A", 1.0, 2.0).isSet());
    }

    @Test
    public void position_onlyWithBothCoordinates() {
        assertNull(new VideoPlace(null, null, 1.0, null).position());
        VideoPlace place = new VideoPlace(null, null, 1.0, 2.0);
        assertEquals(1.0, place.position().latitude, 0);
        assertEquals(2.0, place.position().longitude, 0);
        assertSame(place.position(), place.position());
    }

    /** Filling in a name and coordinates is all it takes for a place to show. */
    @Test
    public void repository_usesSetPlacesOnly() {
        List<VideoPlace> places = Arrays.asList(
                new VideoPlace(Type.POLICE, "Police A", 0.0, 0.01),
                new VideoPlace(Type.FIRE_STATION, null, null, null),
                new VideoPlace(Type.EVACUATION_SITE, "Shelter A", 0.01, 0.0));
        FacilityRepository repository = VideoPlace.repositoryOf(places);
        List<Facility> found = repository.findNearby(0, 0);
        assertEquals(2, found.size());
        assertNull(repository.nearest(Type.FIRE_STATION, 0, 0));
        Facility police = repository.nearest(Type.POLICE, 0, 0);
        assertEquals("Police A", police.name);
        assertEquals(90, police.bearingDegrees);
        assertEquals(1_112, police.distanceMeters);
    }

    /** The filming location is not a facility, so it never shows in the lists. */
    @Test
    public void repository_skipsTheFilmingLocation() {
        FacilityRepository repository = VideoPlace.repositoryOf(
                Arrays.asList(new VideoPlace(null, "Here", 0.0, 0.0)));
        assertTrue(repository.findNearby(0, 0).isEmpty());
    }
}
