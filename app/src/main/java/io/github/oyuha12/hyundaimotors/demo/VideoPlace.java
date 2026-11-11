package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.facility.FacilityRepository;
import io.github.oyuha12.hyundaimotors.facility.FixedFacilityRepository;
import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;
import java.util.List;

/**
 * A real place for the demo video. Name and coordinates stay null until the team gives them;
 * never make them up. Screens show "장소 미설정" for a place that is not set.
 */
public final class VideoPlace {
    /** The kind of place, or null for the filming location. */
    @Nullable public final Facility.Type type;
    @Nullable public final String name;
    @Nullable public final Double latitude;
    @Nullable public final Double longitude;
    @Nullable private final GeoPoint position;

    public VideoPlace(
            @Nullable Facility.Type type,
            @Nullable String name,
            @Nullable Double latitude,
            @Nullable Double longitude) {
        this.type = type;
        this.name = name == null || name.trim().isEmpty() ? null : name.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        position = latitude != null && longitude != null ? new GeoPoint(latitude, longitude) : null;
    }

    /** Name and both coordinates are filled in. */
    public boolean isSet() {
        return name != null && position != null;
    }

    /** The coordinates, or null while they are not set. */
    @Nullable
    public GeoPoint position() {
        return position;
    }

    /** The places that are set, as facilities. Unset ones are left out. */
    public static FacilityRepository repositoryOf(List<VideoPlace> places) {
        FixedFacilityRepository repository = new FixedFacilityRepository();
        for (VideoPlace place : places) {
            if (place.type != null && place.isSet()) {
                repository.add(place.type, place.name, place.latitude, place.longitude);
            }
        }
        return repository;
    }
}
