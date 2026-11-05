package io.github.caizhixing.trail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 2.9: the Trail facade must expose JVM signatures identical across
 * trail-debug / trail-noop / trail-spi. SPI_SHARED_SUBSET is asserted verbatim
 * in all three modules; the extra entries below are the impl-only surface
 * (inline lambda overloads, init, beginTrace).
 */
class DebugTrailJvmSignatureTest {

    @Test
    fun trailClassLoadsFromDebugArtifact() {
        assertNotNull(Class.forName("io.github.caizhixing.trail.Trail"))
    }

    @Test
    fun trailGateExposesEnabledConstantOnDebugArtifact() {
        val gate = Class.forName("io.github.caizhixing.trail.TrailGate")
        assertEquals(true, gate.getField("ENABLED").getBoolean(null))
    }

    @Test
    fun trailExposesSpiSharedJvmSignatures() {
        val signatures = signaturesOf(Trail::class.java)

        assertTrue(
            "missing shared signatures, found: $signatures",
            signatures.containsAll(SPI_SHARED_SUBSET),
        )
    }

    @Test
    fun trailExposesImplOnlyJvmSignatures() {
        val signatures = signaturesOf(Trail::class.java)

        val implOnly = setOf(
            "init(android.content.Context,io.github.caizhixing.trail.model.TrailConfig)",
            "event(java.lang.String,java.lang.String,kotlin.jvm.functions.Function0)",
            "event(java.lang.String,java.lang.String,io.github.caizhixing.trail.model.Audience,io.github.caizhixing.trail.model.Level,kotlin.jvm.functions.Function0)",
            "flow(java.lang.String,java.lang.String,kotlin.jvm.functions.Function0)",
            "beginTrace(java.lang.String)",
        )

        assertTrue(
            "missing impl-only signatures, found: $signatures",
            signatures.containsAll(implOnly),
        )
    }

    private fun signaturesOf(clazz: Class<*>): Set<String> =
        clazz.methods
            .filter { it.declaringClass == clazz }
            .map { method -> method.name + "(" + method.parameterTypes.joinToString(",") { type -> type.name } + ")" }
            .toSet()

    private companion object {
        val SPI_SHARED_SUBSET = setOf(
            "event(java.lang.String,java.lang.String,java.util.Map)",
            "event(java.lang.String,java.lang.String,io.github.caizhixing.trail.model.Audience,io.github.caizhixing.trail.model.Level,java.util.Map)",
            "event(java.lang.String,java.lang.String,java.lang.String,io.github.caizhixing.trail.model.Audience,io.github.caizhixing.trail.model.Level,java.util.Map)",
            "flow(java.lang.String,java.lang.String,java.util.Map)",
            "withTrace(java.lang.String,kotlin.jvm.functions.Function1,kotlin.coroutines.Continuation)",
            "currentTraceId()",
            "dumpRecent(int)",
        )
    }
}
