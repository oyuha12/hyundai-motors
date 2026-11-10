package io.github.oyuha12.hyundaimotors.guidance;

import androidx.annotation.Nullable;
import java.util.Locale;

/** Turns a spoken reply to "is this an emergency?" into yes, no or unclear. */
public final class AnswerClassifier {
    public enum Answer { YES, NO, UNCLEAR }

    /** Checked first: a reply that contains any of these is not a confirmation. */
    private static final String[] NO_PHRASES = {
        "아니", "아뇨", "아냐", "아닌", "괜찮", "취소", "정상", "잘못", "오작동", "됐어", "no"
    };

    /** Long phrases match anywhere in the reply. */
    private static final String[] YES_PHRASES = {
        "맞아", "맞습니다", "맞네", "그래", "그렇", "도와", "살려", "급해", "위험", "비상", "yes"
    };

    /** Short words match only as a whole word, so "네비" or "어디" do not count. */
    private static final String[] YES_WORDS = {"네", "넵", "예", "응", "어", "웅", "ok"};

    private AnswerClassifier() {}

    public static Answer classify(@Nullable String reply) {
        if (reply == null) return Answer.UNCLEAR;
        String text = reply.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim();
        if (text.isEmpty()) return Answer.UNCLEAR;

        boolean no = containsAny(text, NO_PHRASES);
        boolean yes = containsAny(text, YES_PHRASES) || hasWord(text, YES_WORDS);
        if (no && yes) return Answer.UNCLEAR;
        if (no) return Answer.NO;
        if (yes) return Answer.YES;
        return Answer.UNCLEAR;
    }

    private static boolean containsAny(String text, String[] phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }

    private static boolean hasWord(String text, String[] words) {
        for (String token : text.split("\\s+")) {
            // Allow polite endings such as "네요" or "예요".
            String stem = token.endsWith("요") && token.length() > 1
                    ? token.substring(0, token.length() - 1)
                    : token;
            for (String word : words) {
                if (token.equals(word) || stem.equals(word)) return true;
            }
        }
        return false;
    }
}
