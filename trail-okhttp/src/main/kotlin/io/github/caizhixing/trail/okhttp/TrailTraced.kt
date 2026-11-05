package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import okhttp3.Request

/**
 * Captures [Trail.currentTraceId] at the call site into the request tag
 * (PRD §2.6): the interceptor later runs on an OkHttp worker thread where the
 * calling coroutine's trace context is no longer reachable, so the value has
 * to travel on the request itself. Outside a trace this is a no-op and the
 * interceptor emits its events with traceId=null.
 */
public fun Request.Builder.trailTraced(): Request.Builder {
    val traceId = Trail.currentTraceId() ?: return this
    return tag(TrailTraceTag::class.java, TrailTraceTag(traceId))
}
