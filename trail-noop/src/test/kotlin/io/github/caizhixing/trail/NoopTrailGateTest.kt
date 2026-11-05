package io.github.caizhixing.trail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NoopTrailGateTest {

    @Test
    fun noopArtifactGateIsDisabled() {
        assertFalse(TrailGate.ENABLED)
    }

    @Test
    fun gateConstantIsFalseOnClassLevel() {
        val gate = Class.forName("io.github.caizhixing.trail.TrailGate")
        assertEquals(false, gate.getField("ENABLED").getBoolean(null))
    }
}
