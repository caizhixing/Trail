package io.github.caizhixing.trail.sample;

import io.github.caizhixing.trail.Trail;
import io.github.caizhixing.trail.TrailGate;
import io.github.caizhixing.trail.model.Audience;
import io.github.caizhixing.trail.model.Level;

import java.util.Collections;
import java.util.Map;

/**
 * Legacy Java integration template: Java call sites cannot use inline lambdas,
 * so they pass eager Maps and must guard with the constant gate themselves.
 * The guard is folded away by javac at compile time, so a production dex keeps
 * none of these references.
 */
public final class LegacyJava {

    /** Module-level tag constant per TRAIL_CONVENTIONS.md section 1. */
    private static final String TRAIL_TAG = "sample";

    private LegacyJava() {
    }

    public static void onScreenShown(String screen) {
        if (TrailGate.ENABLED) {
            Map<String, String> attrs = Collections.singletonMap("screen", screen);
            Trail.event(TRAIL_TAG, "trail_sample_java_screen_shown", attrs);
        }
    }

    public static void onDataLoaded(int itemCount) {
        if (TrailGate.ENABLED) {
            Map<String, String> attrs = Collections.singletonMap("count", String.valueOf(itemCount));
            Trail.event(TRAIL_TAG, "trail_sample_java_data_loaded", Audience.BOTH, Level.INFO, attrs);
        }
    }

    public static void onTraceBlockEntered() {
        if (TrailGate.ENABLED) {
            Trail.flow(TRAIL_TAG, "trail_sample_java_inside_trace", Collections.singletonMap("lang", "java"));
        }
    }

    /**
     * Callback-style bridge (TRAIL_CONVENTIONS.md 7.2 example 2): beginTrace
     * pushes onto the current thread's manual stack and the AutoCloseable must
     * be closed on the SAME thread (appendix A, boundary 3 — cross-thread close
     * is a silent no-op). The explicit-traceId overload mirrors what a real
     * cross-thread caller does after capturing currentTraceId at the call site.
     */
    public static void onCallbackInvoked(String source) {
        if (TrailGate.ENABLED) {
            try (AutoCloseable trace = Trail.beginTrace("trail_sample_java_callback_trace")) {
                Trail.event(TRAIL_TAG, "trail_sample_java_callback_event", Trail.currentTraceId(),
                        Audience.AI, Level.INFO, Collections.singletonMap("source", source));
            } catch (Exception e) {
                // AutoCloseable.close() declares `throws Exception`; Trail's
                // close implementation never throws in practice.
            }
        }
    }
}
