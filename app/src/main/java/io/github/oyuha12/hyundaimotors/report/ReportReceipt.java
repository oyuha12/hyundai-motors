package io.github.oyuha12.hyundaimotors.report;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;

/**
 * What the receipt screen says was passed on with a report. Shown only: the report itself stays
 * in {@link MockEmergencyReporter}.
 */
public final class ReportReceipt {
    public final EmergencyType type;
    /** Wall clock time of the report. */
    public final long reportedAtMillis;
    /** The place name in video mode, otherwise the label the home screen shows. */
    public final String location;
    /** Highest speed in the minute before the report, or null if unknown. */
    @Nullable public final Float topSpeedKph;
    /** Speed when reporting, or null if unknown. */
    @Nullable public final Float speedKph;

    public ReportReceipt(
            EmergencyType type,
            long reportedAtMillis,
            String location,
            @Nullable Float topSpeedKph,
            @Nullable Float speedKph) {
        this.type = type;
        this.reportedAtMillis = reportedAtMillis;
        this.location = location;
        this.topSpeedKph = topSpeedKph;
        this.speedKph = speedKph;
    }
}
