package com.lifehackd.studycrammer.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiParsingTest {

    @Test fun sseChunkConcatenatesTextAndSkipsThoughts() {
        val c = GeminiParsing.parseSseChunk(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"secret","thought":true},{"text":"Hello "},{"text":"world"}]},"finishReason":"STOP"}]}""",
        )
        assertEquals("Hello world", c.text)
        assertEquals("STOP", c.finishReason)
        assertNull(c.blockReason)
        assertFalse(GeminiParsing.isBlockingFinish(c.finishReason))
    }

    @Test fun promptBlockIsSurfaced() {
        val c = GeminiParsing.parseSseChunk("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
        assertEquals("SAFETY", c.blockReason)
        assertTrue(GeminiParsing.isBlockingFinish("RECITATION"))
    }

    @Test fun inStreamErrorThrowsTypedException() {
        try {
            GeminiParsing.parseSseChunk("""{"error":{"code":429,"message":"Quota","status":"RESOURCE_EXHAUSTED"}}""")
            fail("expected exception")
        } catch (e: GeminiException) {
            assertEquals(GeminiException.Kind.RATE_LIMIT, e.kind)
        }
    }

    @Test fun httpErrorMapping() {
        fun k(code: Int, body: String = "", marker: String? = null) = GeminiParsing.httpError(code, body, marker).kind
        val badKey = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        assertEquals(GeminiException.Kind.INVALID_KEY, k(400, badKey))
        assertEquals(GeminiException.Kind.INVALID_KEY, k(403, """{"error":{"code":403,"status":"PERMISSION_DENIED"}}"""))
        assertEquals(GeminiException.Kind.RATE_LIMIT, k(429))
        assertEquals(GeminiException.Kind.REGION, k(400, """{"error":{"message":"User location is not supported for the API use."}}"""))
        assertEquals(GeminiException.Kind.BAD_REQUEST, k(400, """{"error":{"message":"Invalid JSON payload received."}}"""))
        assertEquals(GeminiException.Kind.OVERLOADED, k(503))
        assertEquals(GeminiException.Kind.INTEGRITY, k(409, marker = GeminiParsing.LOCAL_INTEGRITY))
        assertEquals(GeminiException.Kind.NO_KEY, k(401, marker = GeminiParsing.LOCAL_NO_KEY))
        assertTrue(GeminiParsing.httpError(429, "", null).friendlyMessage.startsWith("Free-tier rate limit hit"))
        assertTrue(GeminiParsing.httpError(403, "", null).friendlyMessage.startsWith("Invalid or restricted Gemini API key"))
    }

    @Test fun quizParsingStripsFencesAndDropsUngroundedItems() {
        val raw = """
            ```json
            {"questions":[
              {"question":"What makes ATP?","answer":"Mitochondria","source_snippet":"It produces ATP","difficulty":"easy"},
              {"question":"Who discovered it?","answer":"Not covered in your notes","source_snippet":"","difficulty":"hard"},
              {"question":"","answer":"x","source_snippet":"","difficulty":"easy"}
            ]}
            ```
        """.trimIndent()
        val qs = GeminiParsing.parseQuiz(raw)
        assertEquals(1, qs.size)
        assertEquals("Mitochondria", qs[0].answer)
    }

    @Test fun gradeAndTriageParsing() {
        val g = GeminiParsing.parseGrade("""{"verdict":"Partial","feedback":"Close!","recap":"ATP"}""")
        assertEquals(Verdict.PARTIAL, g.verdict)
        val t = GeminiParsing.parseTriage(
            """{"headline":"Win","items":[{"concept":"ATP","why_it_scores":"core","memory_hook":"battery","source_snippet":"produces ATP"}],"last_minute_tip":"Drill it"}""",
        )
        assertEquals(1, t.items.size)
        assertEquals("battery", t.items[0].memoryHook)
    }

    @Test fun requestBodyCarriesSystemInstructionAndSchema() {
        val body = GeminiParsing.buildRequestBody(
            GenerationRequest("SYS", "PROMPT", jsonSchema = Prompts.QUIZ_SCHEMA, thinkingBudget = 0),
        )
        assertEquals("SYS", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val gen = body.getJSONObject("generationConfig")
        assertEquals("application/json", gen.getString("responseMimeType"))
        assertEquals("OBJECT", gen.getJSONObject("responseSchema").getString("type"))
        assertEquals(0, gen.getJSONObject("thinkingConfig").getInt("thinkingBudget"))
        // every schema must be valid JSON
        listOf(Prompts.QUIZ_SCHEMA, Prompts.GRADE_SCHEMA, Prompts.TRIAGE_SCHEMA).forEach { JSONObject(it) }
    }

    @Test fun everyPromptWrapsNotesAndSystemPromptForbidsOutsideFacts() {
        val p = Prompts.translator("my notes", Urgency.CODE_RED)
        assertTrue(p.contains("<NOTES>\nmy notes\n</NOTES>"))
        assertTrue(Prompts.SYSTEM_GROUNDING.contains("Not covered in your notes"))
    }
}
