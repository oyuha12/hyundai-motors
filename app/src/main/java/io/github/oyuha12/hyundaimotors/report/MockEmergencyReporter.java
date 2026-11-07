package io.github.oyuha12.hyundaimotors.report;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TEST ONLY. Keeps the report in memory and contacts no one.
 *
 * <p>This class must stay free of network, telephony, SMS and intent calls. The app also holds
 * no CALL_PHONE or SEND_SMS permission, and ManifestSafetyTest fails if one is added.
 */
public final class MockEmergencyReporter {
    public static final class Report {
        public final EmergencyType type;
        public final long createdAtMillis;
        @Nullable public final Double latitude;
        @Nullable public final Double longitude;
        @Nullable public final Float speedKph;

        Report(
                EmergencyType type,
                long createdAtMillis,
                @Nullable Double latitude,
                @Nullable Double longitude,
                @Nullable Float speedKph) {
            this.type = type;
            this.createdAtMillis = createdAtMillis;
            this.latitude = latitude;
            this.longitude = longitude;
            this.speedKph = speedKph;
        }
    }

    private final List<Report> reports = new ArrayList<>();

    public Report submit(
            EmergencyType type,
            long createdAtMillis,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable Float speedKph) {
        Report report = new Report(type, createdAtMillis, latitude, longitude, speedKph);
        reports.add(report);
        return report;
    }

    public List<Report> getReports() {
        return Collections.unmodifiableList(reports);
    }
}
