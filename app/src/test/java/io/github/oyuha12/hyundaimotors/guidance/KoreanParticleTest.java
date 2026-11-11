package io.github.oyuha12.hyundaimotors.guidance;

import static org.junit.Assert.assertEquals;

import io.github.oyuha12.hyundaimotors.guidance.KoreanParticle.Particle;
import org.junit.Test;

public class KoreanParticleTest {
    @Test
    public void subject_followsTheFinalConsonant() {
        assertEquals("급발진이", KoreanParticle.subject("급발진"));
        assertEquals("차량 화재가", KoreanParticle.subject("차량 화재"));
        assertEquals("사고가", KoreanParticle.subject("사고"));
        assertEquals("브레이크 고장이", KoreanParticle.subject("브레이크 고장"));
        assertEquals("타이어 이상이", KoreanParticle.subject("타이어 이상"));
        assertEquals("2차 사고 위험이", KoreanParticle.subject("2차 사고 위험"));
    }

    @Test
    public void otherParticles() {
        assertEquals("급발진은", KoreanParticle.attach("급발진", Particle.TOPIC));
        assertEquals("화재는", KoreanParticle.attach("화재", Particle.TOPIC));
        assertEquals("급발진을", KoreanParticle.attach("급발진", Particle.OBJECT));
        assertEquals("화재를", KoreanParticle.attach("화재", Particle.OBJECT));
    }

    @Test
    public void edgesOfTheHangulRange() {
        assertEquals("가가", KoreanParticle.subject("가"));
        assertEquals("힣이", KoreanParticle.subject("힣"));
    }

    @Test
    public void unknownReading_getsBothForms() {
        assertEquals("ABS이(가)", KoreanParticle.subject("ABS"));
        assertEquals("차로 2이(가)", KoreanParticle.subject("차로 2"));
    }

    @Test
    public void spacesAndEmpty() {
        assertEquals("급발진이", KoreanParticle.subject(" 급발진 "));
        assertEquals("", KoreanParticle.subject("  "));
    }
}
