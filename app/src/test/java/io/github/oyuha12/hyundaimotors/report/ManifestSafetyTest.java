package io.github.oyuha12.hyundaimotors.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/** The report feature is a mock. These checks stop it from gaining a way to reach anyone. */
public class ManifestSafetyTest {
    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void manifest_hasNoCallOrMessagePermission() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        assertFalse(manifest.contains("CALL_PHONE"));
        assertFalse(manifest.contains("CALL_PRIVILEGED"));
        assertFalse(manifest.contains("SEND_SMS"));
    }

    @Test
    public void reporter_hasNoOutboundCode() throws Exception {
        String source = read(
                "src/main/java/io/github/oyuha12/hyundaimotors/report/MockEmergencyReporter.java");
        String code = source.substring(source.indexOf("public final class"));
        for (String forbidden : new String[] {
                "startActivity", "Intent", "http", "Socket", "SmsManager", "TelephonyManager"}) {
            assertFalse(forbidden, code.contains(forbidden));
        }
    }

    @Test
    public void reports_stayInMemory() {
        MockEmergencyReporter reporter = new MockEmergencyReporter();
        reporter.submit(EmergencyType.COLLISION_SUSPECTED, 1L, 37.5, 127.0, 0f);
        assertEquals(1, reporter.getReports().size());
    }
}
