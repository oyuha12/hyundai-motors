package io.github.oyuha12.hyundaimotors.diagnosis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep.Motion;
import io.github.oyuha12.hyundaimotors.guidance.KoreanParticle;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.BeforeClass;
import org.junit.Test;

/** The team's confirmed voice script, word for word, with its pictures and stop waits. */
public class EmergencyPlanScriptTest {
    private static final Map<String, String> STRINGS = new HashMap<>();

    @BeforeClass
    public static void readStrings() throws Exception {
        String xml = new String(
                Files.readAllBytes(new File("src/main/res/values/strings.xml").toPath()),
                StandardCharsets.UTF_8);
        Matcher match = Pattern.compile("<string name=\"(\\w+)\">([^<]*)</string>").matcher(xml);
        while (match.find()) STRINGS.put(match.group(1), match.group(2));
    }

    /** The text of a string resource, as written in strings.xml. */
    private static String text(int id) throws IllegalAccessException {
        for (Field field : R.string.class.getFields()) {
            if (field.getInt(null) == id) {
                String value = STRINGS.get(field.getName());
                assertNotNull(field.getName(), value);
                return value;
            }
        }
        throw new AssertionError("no string " + id);
    }

    private static void assertStep(
            GuidanceStep step, String symbol, int icon, Motion motion, boolean waits, String text)
            throws IllegalAccessException {
        assertEquals(text, symbol, step.symbol);
        assertEquals(text, icon, step.icon);
        assertEquals(text, motion, step.motion);
        assertEquals(text, waits, step.waitsForStop);
        assertEquals(text, text(step.text));
    }

    @Test
    public void acceleration_followsTheScript() throws Exception {
        List<GuidanceStep> steps = EmergencyPlan.steps(EmergencyType.UNINTENDED_ACCELERATION);
        assertEquals(4, steps.size());
        assertStep(steps.get(0), "N", 0, Motion.PRESS, false,
                "시동을 끄지 말고 기어를 중립으로 바꾸세요.");
        assertStep(steps.get(1), null, R.drawable.ic_footprint, Motion.PRESS, false,
                "브레이크를 꾹 밟으세요.");
        assertStep(steps.get(2), "P", 0, Motion.PULL, true,
                "사이드 브레이크를 지속적으로 당기세요.");
        assertStep(steps.get(3), null, R.drawable.ic_power_settings_new, Motion.PULSE, false,
                "시동 버튼을 길게 누르거나 빠르게 여러 번 눌러 시동을 끄세요.");
    }

    @Test
    public void fire_followsTheScript() throws Exception {
        List<GuidanceStep> steps = EmergencyPlan.steps(EmergencyType.FIRE);
        assertEquals(4, steps.size());
        assertStep(steps.get(0), null, R.drawable.ic_warning, Motion.BLINK, true,
                "비상등을 켜고 갓길로 이동하세요.");
        assertStep(steps.get(1), null, R.drawable.ic_directions_walk, Motion.MOVE_AWAY, false,
                "시동을 끈 후 즉시 대피하세요.");
        assertStep(steps.get(2), null, R.drawable.ic_do_not_touch, Motion.SHAKE, false,
                "절대로 차량 보닛을 열지 마세요.");
        assertStep(steps.get(3), null, R.drawable.ic_sos, Motion.PULSE, false,
                "대피 후 안전 거리를 확보한 후 신고하세요.");
    }

    /** Only the two script steps wait; every other situation reads on as before. */
    @Test
    public void otherSituations_neverWait() {
        for (EmergencyType type : EmergencyType.values()) {
            if (type == EmergencyType.UNINTENDED_ACCELERATION || type == EmergencyType.FIRE) {
                continue;
            }
            for (GuidanceStep step : EmergencyPlan.steps(type)) {
                assertFalse(type.name(), step.waitsForStop);
            }
        }
    }

    /** The screen title and the spoken name are the script's "차량 화재". */
    @Test
    public void fire_isCalledVehicleFire() throws Exception {
        assertEquals("차량 화재", text(EmergencyPlan.title(EmergencyType.FIRE)));
    }

    @Test
    public void suspected_readsNaturally() throws Exception {
        String sentence = text(R.string.suspected_announcement);
        assertEquals("급발진이 의심됩니다.", String.format(sentence, KoreanParticle.subject(
                text(EmergencyPlan.title(EmergencyType.UNINTENDED_ACCELERATION)))));
        assertEquals("차량 화재가 의심됩니다.", String.format(sentence, KoreanParticle.subject(
                text(EmergencyPlan.title(EmergencyType.FIRE)))));
        assertEquals("사고가 의심됩니다.", String.format(sentence, KoreanParticle.subject(
                text(EmergencyPlan.title(EmergencyType.ACCIDENT)))));
    }

    /** The yes/no question begins with the same sentence as the video announcement. */
    @Test
    public void confirmQuestion_startsWithTheSuspicion() throws Exception {
        String question = text(R.string.confirm_prompt_detected);
        assertTrue(question, question.startsWith(text(R.string.suspected_announcement) + " "));
    }

    @Test
    public void waitingNotice() throws Exception {
        assertEquals("차량이 멈추면 다음 안내를 시작합니다", text(R.string.waiting_for_stop));
    }
}
