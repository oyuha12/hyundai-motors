package io.github.oyuha12.hyundaimotors.guidance;

import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Whether a spoken reply asks for a report: "신고해 줘", "119", "112", "도와줘", "구급차 불러 줘".
 * Spaces are ignored, since recognizers write "신고해 줘" and "신고해줘" either way.
 */
public final class ReportIntentClassifier {
    /** Checked first: a reply with any of these is not a request, as in "신고하지 마". */
    private static final String[] NO_PHRASES = {
        "하지마", "하지말", "안해", "안할", "필요없", "괜찮", "취소", "아니", "됐어", "no"
    };

    private static final String[] REPORT_PHRASES = {
        "신고", "119", "112", "일일구", "일일이", "도와", "살려", "불러", "구급차", "경찰", "소방",
        "help", "sos"
    };

    private ReportIntentClassifier() {}

    public static boolean isReport(@Nullable String reply) {
        if (reply == null) return false;
        String text = reply.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        if (text.isEmpty() || containsAny(text, NO_PHRASES)) return false;
        return containsAny(text, REPORT_PHRASES);
    }

    private static boolean containsAny(String text, String[] phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }
}
