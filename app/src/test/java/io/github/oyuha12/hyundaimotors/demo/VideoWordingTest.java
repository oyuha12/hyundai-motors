package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.R;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class VideoWordingTest {
    /** Words that tell viewers the app is a test. None may show in video mode. */
    private static final String[] TEST_WORDS = {"테스트", "샘플", "임시", "앱에만", "시연", "전송되지"};

    @Test
    public void outsideVideoMode_everythingStaysAsItWas() {
        for (int normal : VideoWording.replaced()) {
            assertEquals(normal, VideoWording.pick(normal, false));
        }
    }

    @Test
    public void inVideoMode_testWordingIsReplaced() {
        assertFalse(VideoWording.replaced().isEmpty());
        for (int normal : VideoWording.replaced()) {
            assertNotEquals(normal, VideoWording.pick(normal, true));
        }
    }

    @Test
    public void inVideoMode_otherTextIsUnchanged() {
        assertEquals(R.string.emergency_repeat, VideoWording.pick(R.string.emergency_repeat, true));
        assertEquals(R.string.step_fire_stop, VideoWording.pick(R.string.step_fire_stop, true));
    }

    @Test
    public void videoScreenStrings_haveNoTestWords() throws Exception {
        String strings = new String(
                Files.readAllBytes(new File("src/main/res/values/strings.xml").toPath()),
                StandardCharsets.UTF_8);
        Matcher match = Pattern.compile("<string name=\"(video_screen_\\w+)\">([^<]*)</string>")
                .matcher(strings);
        int found = 0;
        while (match.find()) {
            found++;
            for (String word : TEST_WORDS) {
                assertFalse(match.group(1) + " contains " + word, match.group(2).contains(word));
            }
        }
        // Several texts may share one replacement, such as "안전 정차 장소".
        Set<Integer> targets = new HashSet<>();
        for (int normal : VideoWording.replaced()) targets.add(VideoWording.pick(normal, true));
        assertTrue(found >= targets.size());
    }
}
