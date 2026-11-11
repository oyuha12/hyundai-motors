package io.github.oyuha12.hyundaimotors.guidance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReportIntentClassifierTest {
    @Test
    public void requests() {
        for (String reply : new String[] {
                "신고", "신고해 줘", "신고해줘", "신고해 주세요", "신고 부탁해요",
                "119", "112", "119 불러 줘", "일일구", "일일이에 연락해",
                "도와줘", "도와주세요", "살려 주세요", "불러 줘", "구급차 불러 줘",
                "경찰 불러", "소방서에 알려 줘", "SOS", "Help!", "신고해 줘."}) {
            assertTrue(reply, ReportIntentClassifier.isReport(reply));
        }
    }

    @Test
    public void notRequests() {
        for (String reply : new String[] {
                "", "  ", "네", "응", "알겠어", "다시 말해 줘", "지금 몇 시야",
                "신고하지 마", "신고하지 말아 줘", "신고 안 해도 돼요", "신고 안 할래",
                "신고 필요 없어", "괜찮아요", "취소", "아니요", "됐어"}) {
            assertFalse(reply, ReportIntentClassifier.isReport(reply));
        }
        assertFalse(ReportIntentClassifier.isReport(null));
    }
}
