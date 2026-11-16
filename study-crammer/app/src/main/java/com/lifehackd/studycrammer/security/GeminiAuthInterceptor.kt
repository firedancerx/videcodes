package com.lifehackd.studycrammer.security

import com.lifehackd.studycrammer.data.GeminiParsing
import com.lifehackd.studycrammer.data.GeminiStreamingClient
import com.lifehackd.studycrammer.data.KeyOverride
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * The ONLY place the API key meets the network.
 *  - Gemini host only, HTTPS only, header only (`x-goog-api-key`; never a `?key=` query param).
 *  - If IntegrityGuard.isVerified() is false the key is wiped from memory and the request is
 *    short-circuited with a local 409 — it never leaves the device.
 */
class GeminiAuthInterceptor(private val vault: ApiKeyVault) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != GeminiStreamingClient.HOST) return chain.proceed(request)
        if (!request.isHttps) throw IOException("Refusing non-HTTPS request to Gemini")

        if (!IntegrityGuard.isVerified()) {
            vault.wipeMemory()
            return local(request, 409, GeminiParsing.LOCAL_INTEGRITY)
        }
        val key = request.tag(KeyOverride::class.java)?.key
            ?: vault.getKey()
            ?: return local(request, 401, GeminiParsing.LOCAL_NO_KEY)

        val authed = request.newBuilder()
            .header(GeminiStreamingClient.API_KEY_HEADER, key)
            .build()
        return chain.proceed(authed)
    }

    private fun local(request: Request, code: Int, marker: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(marker)
            .header(GeminiParsing.LOCAL_MARKER_HEADER, marker)
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()
}
