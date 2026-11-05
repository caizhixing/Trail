package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.Audience

/**
 * Pure policy behind the concise/full view switch (behavior 5.1, P-1):
 * first launch defaults to concise (human-readable audiences only) and the
 * user's explicit choice is remembered across viewer opens.
 */
object ConciseViewPolicy {

    const val DEFAULT_CONCISE: Boolean = true

    /** `remembered == null` means "never chosen" → first-launch default. */
    fun initialConcise(remembered: Boolean?): Boolean = remembered ?: DEFAULT_CONCISE

    /**
     * Concise view restricts audiences to HUMAN + BOTH; the full view lifts
     * the restriction entirely (empty set = unrestricted, not "all three").
     */
    fun audiencesFor(concise: Boolean): Set<Audience> =
        if (concise) setOf(Audience.HUMAN, Audience.BOTH) else emptySet()
}
