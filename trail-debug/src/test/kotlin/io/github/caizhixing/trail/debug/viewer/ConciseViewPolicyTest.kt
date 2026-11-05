package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.Audience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 5.1 pure half: first-launch default is concise; an explicit
 * choice restores verbatim; concise restricts audiences to HUMAN + BOTH
 * while full view lifts the restriction (empty set = unrestricted).
 */
class ConciseViewPolicyTest {

    @Test
    fun firstLaunchDefaultsToConcise() {
        assertTrue(ConciseViewPolicy.DEFAULT_CONCISE)
        assertTrue(ConciseViewPolicy.initialConcise(remembered = null))
    }

    @Test
    fun rememberedChoiceRestoresVerbatim() {
        assertTrue(ConciseViewPolicy.initialConcise(remembered = true))
        assertEquals(false, ConciseViewPolicy.initialConcise(remembered = false))
    }

    @Test
    fun conciseViewRestrictsToHumanAndBoth() {
        assertEquals(setOf(Audience.HUMAN, Audience.BOTH), ConciseViewPolicy.audiencesFor(concise = true))
    }

    @Test
    fun fullViewIsUnrestrictedNotAllThree() {
        assertEquals(emptySet<Audience>(), ConciseViewPolicy.audiencesFor(concise = false))
    }
}
