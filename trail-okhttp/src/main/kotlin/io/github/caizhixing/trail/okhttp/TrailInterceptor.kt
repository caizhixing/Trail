package io.github.caizhixing.trail.okhttp

import io.github.caizhixing.trail.Trail
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import java.io.IOException
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp auto-collection interceptor (PRD §2.6): one request emits
 * http_request + http_response (2xx=INFO / non-2xx=WARN) or http_error
 * (ERROR) on tag="network" for audience=AI. Events go through the Trail Map
 * overloads — this module compiles against the trail-spi stub (compileOnly)
 * and at runtime dispatches to the trail-debug / trail-noop facade on the app
 * classpath.
 *
 * `chain.proceed` runs exactly once; failures are recorded first and then
 * rethrown unchanged, so collection never swallows or alters host errors.
 */
public class TrailInterceptor(
    public val config: TrailInterceptorConfig = TrailInterceptorConfig(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val url = request.url.toString()
        val traceId = request.tag(TrailTraceTag::class.java)?.traceId

        Trail.event(
            NETWORK_TAG,
            EVENT_HTTP_REQUEST,
            traceId,
            Audience.AI,
            Level.INFO,
            buildMap {
                put(ATTR_METHOD, method)
                put(ATTR_URL, url)
                putHeaderAttrs(request, this)
            },
        )

        val startedAtNanos = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLIS
            emitResponseEvent(response, request, method, url, traceId, durationMs)
            return response
        } catch (t: Throwable) {
            Trail.event(
                NETWORK_TAG,
                EVENT_HTTP_ERROR,
                traceId,
                Audience.AI,
                Level.ERROR,
                buildMap {
                    put(ATTR_METHOD, method)
                    put(ATTR_URL, url)
                    put(ATTR_ERROR, formatError(t))
                    putHeaderAttrs(request, this)
                },
            )
            throw t
        }
    }

    private fun emitResponseEvent(
        response: Response,
        request: Request,
        method: String,
        url: String,
        traceId: String?,
        durationMs: Long,
    ) {
        val level = if (response.code in SUCCESS_CODE_MIN..SUCCESS_CODE_MAX) Level.INFO else Level.WARN
        Trail.event(
            NETWORK_TAG,
            EVENT_HTTP_RESPONSE,
            traceId,
            Audience.AI,
            level,
            buildMap {
                put(ATTR_METHOD, method)
                put(ATTR_URL, url)
                put(ATTR_STATUS, response.code.toString())
                put(ATTR_DURATION_MS, durationMs.toString())
                bodyAttr(response)?.let { put(ATTR_BODY, it) }
                putHeaderAttrs(request, this)
            },
        )
    }

    /**
     * Response body only (request bodies are not part of the §2.6 contract).
     * peekBody reads without consuming the streamed body. Opt-out, empty
     * content and peek failures omit the attr entirely; non-textual bodies
     * record <omitted>. okhttp 5.x models bodiless responses (204/304) as a
     * non-null EMPTY body, so emptiness is judged on peeked content.
     */
    private fun bodyAttr(response: Response): String? {
        if (!config.recordBodies) return null
        val body = response.body ?: return null
        if (!isTextual(body.contentType())) return OMITTED_BODY
        return try {
            val text = response.peekBody(config.maxBodyBytes.toLong()).string()
            if (text.isEmpty()) null else text
        } catch (e: IOException) {
            OMITTED_BODY
        }
    }

    /**
     * Missing content-type is treated as textual and recorded (PRD-外决策：
     * 二进制响应几乎总带显式 content-type，缺省多为文本；泄露面由引擎侧
     * 脱敏兜底). Type prefix text/ or marker subtypes count as textual.
     */
    private fun isTextual(mediaType: MediaType?): Boolean {
        if (mediaType == null) return true
        if (mediaType.type.equals("text", ignoreCase = true)) return true
        val subtype = mediaType.subtype.lowercase(Locale.ROOT)
        return TEXTUAL_SUBTYPE_MARKERS.any(subtype::contains)
    }

    /**
     * Behavior 7.6 extension point: opt-in request-header names copied into
     * the attrs of all three network events (http_request / http_response /
     * http_error). Masking happens at the source (P-4 key list,
     * case-insensitive full match) so sensitive values never leave the
     * interceptor; other values pass through with the P-6 512-char attr cap.
     */
    private fun putHeaderAttrs(request: Request, into: MutableMap<String, String>) {
        for (name in config.headerAttrs) {
            val value = request.header(name) ?: continue
            into[name] = if (name.lowercase(Locale.ROOT) in SENSITIVE_HEADER_NAMES) {
                MASKED_VALUE
            } else {
                value.take(ATTR_VALUE_MAX_CHARS)
            }
        }
    }

    private fun formatError(t: Throwable): String {
        val message = t.message
        val full = if (message == null) t.javaClass.name else "${t.javaClass.name}: $message"
        return full.take(MAX_ERROR_CHARS)
    }

    private companion object {
        const val NETWORK_TAG = "network"
        const val EVENT_HTTP_REQUEST = "http_request"
        const val EVENT_HTTP_RESPONSE = "http_response"
        const val EVENT_HTTP_ERROR = "http_error"
        const val ATTR_METHOD = "method"
        const val ATTR_URL = "url"
        const val ATTR_STATUS = "status"
        const val ATTR_DURATION_MS = "duration_ms"
        const val ATTR_BODY = "body"
        const val ATTR_ERROR = "error"
        const val OMITTED_BODY = "<omitted>"
        const val MASKED_VALUE = "***"
        const val MAX_ERROR_CHARS = 256
        const val ATTR_VALUE_MAX_CHARS = 512
        const val SUCCESS_CODE_MIN = 200
        const val SUCCESS_CODE_MAX = 299
        const val NANOS_PER_MILLIS = 1_000_000L

        val TEXTUAL_SUBTYPE_MARKERS = listOf(
            "json",
            "xml",
            "html",
            "xhtml",
            "javascript",
            "ecmascript",
            "yaml",
            "csv",
            "plain",
            "x-www-form-urlencoded",
            "graphql",
        )

        val SENSITIVE_HEADER_NAMES = setOf(
            "token",
            "password",
            "secret",
            "authorization",
            "cookie",
            "set-cookie",
            "api_key",
            "phone",
        )
    }
}
