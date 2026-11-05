package io.github.caizhixing.trail.okhttp

import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.HostnameVerifier
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Proxy
import okhttp3.ProxySelector
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

const val TRAIL_TEST_URL = "https://trail.example.test/api/list"

internal fun trailTestRequest(
    url: String = TRAIL_TEST_URL,
    method: String = "GET",
    body: RequestBody? = null,
): Request = Request.Builder().url(url).method(method, body).build()

internal fun trailTestResponse(
    code: Int,
    bodyText: String? = null,
    contentType: MediaType? = null,
    request: Request = trailTestRequest(),
): Response {
    val builder = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("trail_test")
    if (bodyText != null) {
        builder.body(bodyText.toResponseBody(contentType))
    }
    return builder.build()
}

/**
 * Hand-written fake chain (no mock library, no mockwebserver): the scripted
 * response is returned or the scripted error thrown on the first proceed
 * call. Only the request/proceed surface is implemented — the interceptor
 * contract never touches the client wiring members, which throw instead of
 * returning misleading fakes.
 */
internal class FakeChain(
    private val scriptedRequest: Request,
    private val scriptedResponse: Response? = null,
    private val scriptedError: Throwable? = null,
) : Interceptor.Chain {

    var proceedCalls: Int = 0
        private set

    override fun request(): Request = scriptedRequest

    override fun proceed(request: Request): Response {
        proceedCalls++
        scriptedError?.let { throw it }
        return requireNotNull(scriptedResponse) { "FakeChain requires a scripted response or error" }
    }

    override fun connection(): Connection? = null

    override fun call(): Call = unsupported()

    override fun connectTimeoutMillis(): Int = 10_000

    override fun readTimeoutMillis(): Int = 10_000

    override fun writeTimeoutMillis(): Int = 10_000

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override val followSslRedirects: Boolean
        get() = false

    override val followRedirects: Boolean
        get() = false

    override val dns: Dns
        get() = unsupported()

    override fun withDns(dns: Dns): Interceptor.Chain = this

    override val socketFactory: SocketFactory
        get() = unsupported()

    override fun withSocketFactory(socketFactory: SocketFactory): Interceptor.Chain = this

    override val retryOnConnectionFailure: Boolean
        get() = false

    override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain = this

    override val authenticator: Authenticator
        get() = unsupported()

    override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain = this

    override val cookieJar: CookieJar
        get() = unsupported()

    override fun withCookieJar(cookieJar: CookieJar): Interceptor.Chain = this

    override val cache: Cache?
        get() = null

    override fun withCache(cache: Cache?): Interceptor.Chain = this

    override val proxy: Proxy?
        get() = null

    override fun withProxy(proxy: Proxy?): Interceptor.Chain = this

    override val proxySelector: ProxySelector
        get() = unsupported()

    override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain = this

    override val proxyAuthenticator: Authenticator
        get() = unsupported()

    override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain = this

    override val sslSocketFactoryOrNull: SSLSocketFactory?
        get() = null

    override fun withSslSocketFactory(
        sslSocketFactory: SSLSocketFactory?,
        x509TrustManager: X509TrustManager?,
    ): Interceptor.Chain = this

    override val x509TrustManagerOrNull: X509TrustManager?
        get() = null

    override val hostnameVerifier: HostnameVerifier
        get() = unsupported()

    override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain = this

    override val certificatePinner: CertificatePinner
        get() = unsupported()

    override fun withCertificatePinner(certificatePinner: CertificatePinner): Interceptor.Chain = this

    override val connectionPool: ConnectionPool
        get() = unsupported()

    override fun withConnectionPool(connectionPool: ConnectionPool): Interceptor.Chain = this

    override val eventListener: EventListener
        get() = unsupported()

    private fun unsupported(): Nothing = error("FakeChain only implements the request/proceed surface")
}

internal fun trailTestBody(content: String, contentType: MediaType? = null): RequestBody =
    content.toRequestBody(contentType)
