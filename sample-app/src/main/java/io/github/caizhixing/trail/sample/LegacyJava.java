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

    private LegacyJava() {
    }

    public static void onScreenShown(String screen) {
        if (TrailGate.ENABLED) {
            Map<String, String> attrs = Collections.singletonMap("screen", screen);
            Trail.event("sample", "trail_sample_java_screen_shown", attrs);
        }
    }

    public static void onDataLoaded(int itemCount) {
        if (TrailGate.ENABLED) {
            Map<String, String> attrs = Collections.singletonMap("count", String.valueOf(itemCount));
            Trail.event("sample", "trail_sample_java_data_loaded", Audience.BOTH, Level.INFO, attrs);
        }
    }

    public static void onTraceBlockEntered() {
        if (TrailGate.ENABLED) {
            Trail.flow("sample", "trail_sample_java_inside_trace", Collections.singletonMap("lang", "java"));
        }
    }
}
