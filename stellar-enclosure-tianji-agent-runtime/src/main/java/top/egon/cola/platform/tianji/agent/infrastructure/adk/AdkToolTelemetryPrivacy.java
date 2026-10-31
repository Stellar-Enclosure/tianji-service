package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.telemetry.Tracing;
import java.lang.reflect.Modifier;

/** Read-only gate for the ADK flag cached on first Tracing initialization; it never changes telemetry globals. */
public final class AdkToolTelemetryPrivacy {
    private AdkToolTelemetryPrivacy() { }
    public static void requireSafe() {
        if (!"false".equals(System.getenv("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS"))) throw unavailable();
        try { requireCachedCaptureDisabled(Tracing.class); }
        catch (LinkageError drift) { throw unavailable(); }
    }
    static void requireCachedCaptureDisabled(Class<?> tracing) {
        try {
            if (tracing == null) throw unavailable();
            var field = tracing.getDeclaredField("CAPTURE_MESSAGE_CONTENT_IN_SPANS");
            if (field.getType() != boolean.class || field.getModifiers() != (Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL)
                    || !field.trySetAccessible() || field.getBoolean(null)) throw unavailable();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError drift) { throw unavailable(); }
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("ADK tool telemetry privacy is unavailable"); }
}
