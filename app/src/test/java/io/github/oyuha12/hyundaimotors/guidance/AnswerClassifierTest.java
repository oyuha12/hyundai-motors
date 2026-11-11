package io.github.oyuha12.hyundaimotors.guidance;

import static org.junit.Assert.assertEquals;

import io.github.oyuha12.hyundaimotors.guidance.AnswerClassifier.Answer;
import org.junit.Test;

public class AnswerClassifierTest {
    private static void check(Answer expected, String... replies) {
        for (String reply : replies) {
            assertEquals(reply, expected, AnswerClassifier.classify(reply));
        }
    }

    @Test
    public void yesReplies() {
        check(Answer.YES, "네", "네.", "예", "응", "네요", "맞아", "맞아요", "네 맞아요",
                "그래", "도와주세요", "살려주세요", "급해요", "비상이에요", "Yes", "OK");
    }

    @Test
    public void noReplies() {
        check(Answer.NO, "아니요", "아니", "아뇨", "아닌 것 같아요", "괜찮아요",
                "취소", "정상이에요", "잘못 눌렀어요", "오작동이야", "No");
    }

    @Test
    public void unclearReplies() {
        check(Answer.UNCLEAR, "", "   ", "음", "뭐라고요", "네비게이션 켜줘", "어디야");
    }

    @Test
    public void mixedReplies_areUnclear() {
        check(Answer.UNCLEAR, "아니 맞아", "네 아니요");
    }

    @Test
    public void nullReply_isUnclear() {
        assertEquals(Answer.UNCLEAR, AnswerClassifier.classify(null));
    }
}
