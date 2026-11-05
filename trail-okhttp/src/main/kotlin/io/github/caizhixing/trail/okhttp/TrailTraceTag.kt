package io.github.caizhixing.trail.okhttp

/**
 * Typed request-tag carrier for the trace id captured by [trailTraced].
 * Keyed by this class (not the legacy single-tag slot) so the tag never
 * collides with host apps' generic request tags.
 */
public class TrailTraceTag(public val traceId: String)
