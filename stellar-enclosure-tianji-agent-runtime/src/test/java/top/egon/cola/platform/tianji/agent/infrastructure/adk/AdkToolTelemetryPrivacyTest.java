package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class AdkToolTelemetryPrivacyTest {
    @Test void literal_false_is_required_with_the_actual_fresh_adk_tracing_cache() throws Exception {
        probe("false", false, 0); probe("true", false, 7); probe(null, false, 7);
    }

    @Test void a_system_property_or_nonliteral_environment_value_cannot_replace_the_environment() throws Exception {
        probe(null, true, 7);
        for (var value : List.of("FALSE", " false", "garbage")) probe(value, false, 7);
    }

    @Test void cached_flag_shape_value_and_linkage_drift_fail_closed_without_exposing_a_cause() {
        for (var type : List.of(Missing.class, WrongType.class, Mutable.class, PublicFlag.class, Captured.class, BrokenInitialization.class)) {
            var failure = assertThrows(IllegalStateException.class, () -> AdkToolTelemetryPrivacy.requireCachedCaptureDisabled(type));
            assertEquals("ADK tool telemetry privacy is unavailable", failure.getMessage()); assertNull(failure.getCause());
        }
        assertThrows(IllegalStateException.class, () -> AdkToolTelemetryPrivacy.requireCachedCaptureDisabled(null));
    }

    private static void probe(String environment, boolean property, int expected) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Dotel.traces.exporter=none",
                "-Dotel.metrics.exporter=none", "-Dotel.logs.exporter=none"));
        if (property) command.add("-DADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false");
        command.addAll(List.of("-cp", classpath, ForkProbe.class.getName()));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().remove("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS");
        if (environment != null) builder.environment().put("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS", environment);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Bounded ADK privacy probe timed out");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(expected, process.exitValue(), output); assertTrue(output.contains(expected == 0 ? "SAFE" : "DENIED"), output);
        } finally { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS); }
    }

    public static final class ForkProbe {
        public static void main(String[] arguments) {
            try { AdkToolTelemetryPrivacy.requireSafe(); System.out.println("SAFE"); }
            catch (RuntimeException denied) { System.out.println("DENIED"); System.exit(7); }
        }
    }
    private static final class Missing { }
    private static final class WrongType { private static final String CAPTURE_MESSAGE_CONTENT_IN_SPANS = "false"; }
    private static final class Mutable { private static boolean CAPTURE_MESSAGE_CONTENT_IN_SPANS = false; }
    private static final class PublicFlag { public static final boolean CAPTURE_MESSAGE_CONTENT_IN_SPANS = false; }
    private static final class Captured { private static final boolean CAPTURE_MESSAGE_CONTENT_IN_SPANS = true; }
    private static final class BrokenInitialization {
        private static final boolean CAPTURE_MESSAGE_CONTENT_IN_SPANS = fail();
        private static boolean fail() { throw new IllegalStateException("private arguments"); }
    }
}
