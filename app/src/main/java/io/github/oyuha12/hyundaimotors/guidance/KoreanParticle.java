package io.github.oyuha12.hyundaimotors.guidance;

/**
 * Korean particles whose form depends on whether the word ends in a final consonant (받침):
 * 급발진이, 차량 화재가.
 */
public final class KoreanParticle {
    private KoreanParticle() {}

    public enum Particle {
        /** 이/가 */
        SUBJECT("이", "가"),
        /** 은/는 */
        TOPIC("은", "는"),
        /** 을/를 */
        OBJECT("을", "를");

        final String afterConsonant;
        final String afterVowel;

        Particle(String afterConsonant, String afterVowel) {
            this.afterConsonant = afterConsonant;
            this.afterVowel = afterVowel;
        }
    }

    private static final char FIRST_SYLLABLE = '가';
    private static final char LAST_SYLLABLE = '힣';
    private static final int FINALS_PER_VOWEL = 28;

    /** The word with 이 or 가. */
    public static String subject(String word) {
        return attach(word, Particle.SUBJECT);
    }

    /**
     * The word with the particle that fits its last syllable. A word that does not end in a
     * Hangul syllable gets both forms, like "ABS이(가)", since its reading is unknown.
     */
    public static String attach(String word, Particle particle) {
        String trimmed = word.trim();
        if (trimmed.isEmpty()) return trimmed;
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last < FIRST_SYLLABLE || last > LAST_SYLLABLE) {
            return trimmed + particle.afterConsonant + "(" + particle.afterVowel + ")";
        }
        boolean hasFinal = (last - FIRST_SYLLABLE) % FINALS_PER_VOWEL != 0;
        return trimmed + (hasFinal ? particle.afterConsonant : particle.afterVowel);
    }
}
