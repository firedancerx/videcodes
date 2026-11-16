package com.lifehackd.studycrammer.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject

/** Typed, student-friendly failure. [friendlyMessage] is what the UI shows. */
class GeminiException(
    val kind: Kind,
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause) {

    enum class Kind {
        INVALID_KEY, RATE_LIMIT, OVERLOADED, REGION, TOO_LARGE, SAFETY_BLOCK,
        NETWORK, INTEGRITY, NO_KEY, THIN_NOTES, BAD_REQUEST, BAD_RESPONSE, UNKNOWN,
    }

    val friendlyMessage: String
        get() = when (kind) {
            Kind.INVALID_KEY -> "Invalid or restricted Gemini API key — tap here to check your key in Google AI Studio."
            Kind.RATE_LIMIT -> "Free-tier rate limit hit — wait 30 seconds and hit Retry (or switch to a smaller PDF chunk)."
            Kind.OVERLOADED -> "Gemini is slammed right now (HTTP ${httpCode ?: 503}). Give it ~10 seconds and hit Retry."
            Kind.REGION -> "Gemini's free tier isn't available in your region. A paid-tier key from Google AI Studio fixes this."
            Kind.TOO_LARGE -> "That chunk is too big for one request — pick a smaller chunk and retry."
            Kind.SAFETY_BLOCK -> "Gemini's safety filter blocked this one. Try a different chunk of your notes."
            Kind.NETWORK -> "No connection — check Wi-Fi or mobile data and hit Retry."
            Kind.INTEGRITY -> "Integrity Verification Failed (Error 409). Please install the official release."
            Kind.NO_KEY -> "No API key saved yet — add your free Gemini key in Settings."
            Kind.THIN_NOTES -> "Your notes don't have enough facts to work with. Paste a meatier chunk."
            Kind.BAD_REQUEST -> "Gemini rejected the request: ${message ?: "bad request"}"
            Kind.BAD_RESPONSE -> "Gemini sent back something we couldn't read. Hit Retry."
            Kind.UNKNOWN -> "Something went sideways (HTTP ${httpCode ?: "?"}). Hit Retry."
        }
}

/** Request-scoped key used only by the "Test & Save Key" ping (never logged: toString is masked). */
class KeyOverride(val key: String) {
    override fun toString(): String = "KeyOverride(****)"
}

data class GenerationRequest(
    val systemInstruction: String,
    val prompt: String,
    val jsonSchema: String? = null,
    val temperature: Double = 0.3,
    val maxOutputTokens: Int = 4096,
    /** Gemini 2.5 Flash "thinking" tokens; 0 = off (fastest). */
    val thinkingBudget: Int = 0,
)

/** Pure (Android-free) request building & response parsing — unit-tested on the JVM. */
object GeminiParsing {
    /** Header the auth interceptor puts on synthetic, never-sent-to-network responses. */
    const val LOCAL_MARKER_HEADER = "X-Crammer-Local"
    const val LOCAL_INTEGRITY = "integrity"
    const val LOCAL_NO_KEY = "no-key"

    private val BLOCKING_FINISH = setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY")

    fun buildRequestBody(req: GenerationRequest): JSONObject {
        fun textParts(text: String) = JSONArray().put(JSONObject().put("text", text))
        val gen = JSONObject()
            .put("temperature", req.temperature)
            .put("maxOutputTokens", req.maxOutputTokens)
            .put("thinkingConfig", JSONObject().put("thinkingBudget", req.thinkingBudget))
        if (req.jsonSchema != null) {
            gen.put("responseMimeType", "application/json")
            gen.put("responseSchema", JSONObject(req.jsonSchema))
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", textParts(req.systemInstruction)))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", textParts(req.prompt))))
            .put("generationConfig", gen)
    }

    data class SseChunk(val text: String, val finishReason: String?, val blockReason: String?)

    fun parseSseChunk(data: String): SseChunk {
        val o = JSONObject(data)
        o.optJSONObject("error")?.let { err ->
            throw httpError(err.optInt("code", 500), err.toString(), null)
        }
        val block = o.optJSONObject("promptFeedback")?.optString("blockReason", "")?.takeIf { it.isNotBlank() }
        val cand = o.optJSONArray("candidates")?.optJSONObject(0)
        val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue // never surface model "thoughts"
                sb.append(p.optString("text", ""))
            }
        }
        val finish = cand?.optString("finishReason", "")?.takeIf { it.isNotBlank() }
        return SseChunk(sb.toString(), finish, block)
    }

    fun isBlockingFinish(reason: String?): Boolean = reason != null && reason in BLOCKING_FINISH

    fun httpError(code: Int, body: String, localMarker: String?): GeminiException {
        val lower = body.lowercase()
        val serverMsg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message", "") }
            .getOrNull().orEmpty().take(200)
        val kind = when {
            localMarker == LOCAL_INTEGRITY -> GeminiException.Kind.INTEGRITY
            localMarker == LOCAL_NO_KEY -> GeminiException.Kind.NO_KEY
            code == 429 || "resource_exhausted" in lower -> GeminiException.Kind.RATE_LIMIT
            "location is not supported" in lower -> GeminiException.Kind.REGION
            code == 413 || "exceeds the maximum number of tokens" in lower -> GeminiException.Kind.TOO_LARGE
            code == 401 || code == 403 -> GeminiException.Kind.INVALID_KEY
            code == 400 && ("api key" in lower || "api_key" in lower || body.isBlank()) -> GeminiException.Kind.INVALID_KEY
            code == 400 -> GeminiException.Kind.BAD_REQUEST
            code in 500..599 -> GeminiException.Kind.OVERLOADED
            else -> GeminiException.Kind.UNKNOWN
        }
        return GeminiException(kind, serverMsg.ifBlank { "HTTP $code" }, code)
    }

    /** Strips ```json fences / stray prose around a JSON object. */
    fun extractJsonObject(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw GeminiException(GeminiException.Kind.BAD_RESPONSE, "no JSON object")
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            throw GeminiException(GeminiException.Kind.BAD_RESPONSE, "malformed JSON", cause = e)
        }
    }

    fun parseQuiz(text: String): List<QuizQuestion> {
        val arr = extractJsonObject(text).optJSONArray("questions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val q = QuizQuestion(
                question = o.optString("question", "").trim(),
                answer = o.optString("answer", "").trim(),
                sourceSnippet = o.optString("source_snippet", "").trim(),
                difficulty = o.optString("difficulty", "medium").trim(),
            )
            q.takeIf {
                it.question.isNotEmpty() && it.answer.isNotEmpty() &&
                    !it.answer.contains(GroundingVerifier.NOT_COVERED, ignoreCase = true)
            }
        }
    }

    fun parseGrade(text: String): GradeResult {
        val o = extractJsonObject(text)
        val verdict = when (o.optString("verdict", "").trim().lowercase()) {
            "correct" -> Verdict.CORRECT
            "partial" -> Verdict.PARTIAL
            else -> Verdict.INCORRECT
        }
        return GradeResult(verdict, o.optString("feedback", "").trim(), o.optString("recap", "").trim())
    }

    fun parseTriage(text: String): TriageCard {
        val o = extractJsonObject(text)
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val it = arr.optJSONObject(i) ?: return@mapNotNull null
            TriageItem(
                concept = it.optString("concept", "").trim(),
                whyItScores = it.optString("why_it_scores", "").trim(),
                memoryHook = it.optString("memory_hook", "").trim(),
                sourceSnippet = it.optString("source_snippet", "").trim(),
            ).takeIf { item -> item.concept.isNotEmpty() }
        }.take(6)
        return TriageCard(o.optString("headline", "").trim(), items, o.optString("last_minute_tip", "").trim())
    }
}

/**
 * Direct Gemini REST client. No backend: HTTPS straight to generativelanguage.googleapis.com.
 * The API key is NOT handled here — GeminiAuthInterceptor attaches `x-goog-api-key`
 * (header only, never the URL) and only when IntegrityGuard says the APK is genuine.
 */
class GeminiStreamingClient(
    private val http: OkHttpClient,
    private val model: String = DEFAULT_MODEL,
) {
    companion object {
        const val HOST = "generativelanguage.googleapis.com"
        const val BASE_URL = "https://$HOST/v1beta"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val API_KEY_HEADER = "x-goog-api-key"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val sseFactory = EventSources.createFactory(http)

    /** Emits text deltas as they stream in (SSE). Cancel the collector to abort the HTTP call. */
    fun stream(req: GenerationRequest): Flow<String> = callbackFlow {
        val httpReq = Request.Builder()
            .url("$BASE_URL/models/$model:streamGenerateContent?alt=sse")
            .header("Accept", "text/event-stream")
            .post(GeminiParsing.buildRequestBody(req).toString().toRequestBody(JSON_MEDIA))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val chunk = try {
                    GeminiParsing.parseSseChunk(data)
                } catch (e: GeminiException) {
                    close(e); eventSource.cancel(); return
                } catch (e: Exception) {
                    close(GeminiException(GeminiException.Kind.BAD_RESPONSE, "unparseable SSE chunk", cause = e))
                    eventSource.cancel(); return
                }
                if (chunk.blockReason != null) {
                    close(GeminiException(GeminiException.Kind.SAFETY_BLOCK, chunk.blockReason))
                    eventSource.cancel(); return
                }
                if (chunk.text.isNotEmpty()) trySend(chunk.text)
                if (GeminiParsing.isBlockingFinish(chunk.finishReason)) {
                    close(GeminiException(GeminiException.Kind.SAFETY_BLOCK, chunk.finishReason ?: "blocked"))
                    eventSource.cancel()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val err = when {
                    response != null && !response.isSuccessful -> GeminiParsing.httpError(
                        response.code,
                        runCatching { response.peekBody(16_384).string() }.getOrDefault(""),
                        response.header(GeminiParsing.LOCAL_MARKER_HEADER),
                    )
                    t is GeminiException -> t
                    response != null -> GeminiException(GeminiException.Kind.BAD_RESPONSE, "stream error", response.code, t)
                    else -> GeminiException(GeminiException.Kind.NETWORK, t?.javaClass?.simpleName ?: "network", cause = t)
                }
                close(err)
            }
        }
        val source = sseFactory.newEventSource(httpReq, listener)
        awaitClose { source.cancel() }
    }.buffer(Channel.UNLIMITED)

    /** Streams and concatenates the full response (used for JSON-mode calls). */
    suspend fun collect(req: GenerationRequest): String {
        val sb = StringBuilder()
        stream(req).collect { sb.append(it) }
        return sb.toString()
    }

    /**
     * "Test & Save Key": lightweight models.list call (pageSize=1). Costs no generation quota.
     * Throws [GeminiException] on any failure.
     */
    suspend fun verifyKey(candidateKey: String) {
        val req = Request.Builder()
            .url("$BASE_URL/models?pageSize=1")
            .get()
            .tag(KeyOverride::class.java, KeyOverride(candidateKey))
            .build()
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(GeminiException(GeminiException.Kind.NETWORK, "network", cause = e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!cont.isActive) return
                        if (it.isSuccessful) {
                            cont.resume(Unit)
                        } else {
                            val body = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
                            cont.resumeWithException(
                                GeminiParsing.httpError(it.code, body, it.header(GeminiParsing.LOCAL_MARKER_HEADER)),
                            )
                        }
                    }
                }
            })
        }
    }
}
