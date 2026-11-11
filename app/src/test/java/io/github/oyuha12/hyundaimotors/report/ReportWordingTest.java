package io.github.oyuha12.hyundaimotors.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.demo.VideoWording;
import io.github.oyuha12.hyundaimotors.guidance.ReportIntentClassifier;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.BeforeClass;
import org.junit.Test;

/** The report wording the team asked for, word for word. */
public class ReportWordingTest {
    private static final Map<String, String> STRINGS = new HashMap<>();

    @BeforeClass
    public static void readStrings() throws Exception {
        String xml = new String(
                Files.readAllBytes(new File("src/main/res/values/strings.xml").toPath()),
                StandardCharsets.UTF_8);
        Matcher match = Pattern.compile("<string name=\"(\\w+)\">([^<]*)</string>").matcher(xml);
        while (match.find()) STRINGS.put(match.group(1), match.group(2).replace("\\'", "'"));
    }

    private static String text(String name) {
        String value = STRINGS.get(name);
        assertNotNull(name, value);
        return value;
    }

    @Test
    public void offerAfterTheLastStep() {
        assertEquals("신고가 필요하면 '신고해 줘'라고 말씀하시거나 신고 버튼을 누르세요.",
                text("report_prompt"));
    }

    @Test
    public void announcement_inVideoMode() {
        assertEquals(R.string.video_screen_report_announcement,
                VideoWording.pick(R.string.report_announcement, true));
        assertEquals("119와 112에 신고를 접수했습니다. 현재 위치를 전달했습니다.",
                text("video_screen_report_announcement"));
    }

    /** Outside video mode the same words, then a reminder that nothing was sent. */
    @Test
    public void announcement_outsideVideoMode() {
        assertEquals(R.string.report_announcement,
                VideoWording.pick(R.string.report_announcement, false));
        String normal = text("report_announcement");
        assertTrue(normal, normal.startsWith(text("video_screen_report_announcement") + " "));
        assertTrue(normal, normal.contains("실제로 전송되지 않았습니다"));
    }

    @Test
    public void receipt() {
        assertEquals("신고 접수 완료", text("report_receipt_title"));
        assertEquals("112 · 119 접수", text("report_receipt_agencies"));
        assertEquals("음성 인식: '%1$s'", text("report_heard"));
    }

    /** The video stand-in shows a phrase the classifier itself accepts. */
    @Test
    public void standInPhrase_isARequest() {
        assertEquals("신고해 줘", text("report_staged_phrase"));
        assertTrue(ReportIntentClassifier.isReport(text("report_staged_phrase")));
    }
}
