package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.Facility.Type;
import io.github.oyuha12.hyundaimotors.facility.FacilityRepository;
import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;
import java.util.Collections;
import org.junit.Test;

/**
 * The real places from the team (2026-11-12), measured the way video mode measures them: from
 * the filming location, by the haversine formula. Expected distances are the team's.
 */
public class VideoPlacesTest {
    private static final GeoPoint FROM = VideoMode.fallbackPosition(true);
    private static final FacilityRepository PLACES = VideoPlace.repositoryOf(VideoConfig.PLACES);

    /** Kilometers to one decimal, as the team gave them. */
    private static double km(Facility facility) {
        return Math.round(facility.distanceMeters / 100.0) / 10.0;
    }

    private static Facility nearest(Type type) {
        return PLACES.nearest(type, FROM.latitude, FROM.longitude);
    }

    /** As MainViewModel.evacuationTarget in video mode. */
    private static Facility safeStop(EmergencyType situation) {
        VideoPlace place = VideoConfig.safeStopFor(situation);
        return VideoPlace.repositoryOf(Collections.singletonList(place))
                .nearest(place.type, FROM.latitude, FROM.longitude);
    }

    @Test
    public void allPlaces_areSet() {
        assertTrue(VideoConfig.FILMING.isSet());
        for (VideoPlace place : VideoConfig.PLACES) assertTrue(place.name, place.isSet());
    }

    /** Distances are measured from the filming location, the old sample point. */
    @Test
    public void measuredFromTheFilmingLocation() {
        assertEquals(37.48544722, FROM.latitude, 0);
        assertEquals(127.03636666, FROM.longitude, 0);
        assertEquals("서울 강남구 도곡동 (양재역 부근)", VideoConfig.FILMING.name);
    }

    @Test
    public void responders_distances() {
        Facility police = nearest(Type.POLICE);
        assertEquals("서초경찰서 서초2파출소", police.name);
        assertEquals(0.7, km(police), 1e-9);

        Facility fire = nearest(Type.FIRE_STATION);
        assertEquals("서초소방서 양재119안전센터", fire.name);
        assertEquals(1.8, km(fire), 1e-9);
        assertEquals("서울 서초구 동산로 9", fire.address);

        Facility hospital = nearest(Type.HOSPITAL);
        assertEquals("강남세브란스병원 응급실", hospital.name);
        assertEquals(1.2, km(hospital), 1e-9);
        assertEquals("서울 강남구 언주로 211", hospital.address);
    }

    @Test
    public void safeStops_distancesPerScenario() {
        Facility acceleration = safeStop(EmergencyType.UNINTENDED_ACCELERATION);
        assertEquals("양재역 공영주차장", acceleration.name);
        assertEquals(0.9, km(acceleration), 1e-9);

        Facility fire = safeStop(EmergencyType.FIRE);
        assertEquals("매헌윤봉길의사기념관 주차장(지상)", fire.name);
        assertEquals(1.8, km(fire), 1e-9);

        Facility collision = safeStop(EmergencyType.ACCIDENT);
        assertEquals("양재역 공영주차장", collision.name);
        assertEquals(0.9, km(collision), 1e-9);
    }

    /** Fire goes to the above-ground lot; the collision shares the acceleration lot for now. */
    @Test
    public void safeStops_perScenario() {
        assertSame(VideoConfig.SAFE_STOP_ACCELERATION,
                VideoConfig.safeStopFor(EmergencyType.UNINTENDED_ACCELERATION));
        assertSame(VideoConfig.SAFE_STOP_FIRE, VideoConfig.safeStopFor(EmergencyType.FIRE));
        assertSame(VideoConfig.SAFE_STOP_ACCELERATION,
                VideoConfig.safeStopFor(EmergencyType.ACCIDENT));
        assertTrue(VideoConfig.SAFE_STOP_FIRE.name.contains("지상"));
    }

    /** Situations the video does not show have no safe stop place: "장소 미설정". */
    @Test
    public void otherSituations_haveNoSafeStop() {
        for (EmergencyType type : EmergencyType.values()) {
            if (type == EmergencyType.UNINTENDED_ACCELERATION
                    || type == EmergencyType.FIRE
                    || type == EmergencyType.ACCIDENT) {
                continue;
            }
            assertNull(type.name(), VideoConfig.safeStopFor(type));
        }
    }
}
