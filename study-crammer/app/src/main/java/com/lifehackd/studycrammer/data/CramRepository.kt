package com.lifehackd.studycrammer.data

import kotlinx.coroutines.flow.Flow

/** The four exam stages expressed as Gemini calls. Every call carries the grounding system prompt. */
class CramRepository(private val gemini: GeminiStreamingClient) {

    /** Stage 1 — streamed Markdown. */
    fun translator(notes: String, urgency: Urgency): Flow<String> =
        gemini.stream(GenerationRequest(Prompts.SYSTEM_GROUNDING, Prompts.translator(notes, urgency), temperature = 0.4))

    /** Stage 3 — streamed Markdown. */
    fun trapFinder(notes: String, urgency: Urgency): Flow<String> =
        gemini.stream(GenerationRequest(Prompts.SYSTEM_GROUNDING, Prompts.traps(notes, urgency), temperature = 0.2))

    /** Stage 2a — structured quiz. Answers stay in the ViewModel until the student commits a guess. */
    suspend fun quiz(notes: String, urgency: Urgency): List<QuizQuestion> {
        val raw = gemini.collect(
            GenerationRequest(
                Prompts.SYSTEM_GROUNDING, Prompts.quiz(notes, urgency),
                jsonSchema = Prompts.QUIZ_SCHEMA, temperature = 0.5,
                maxOutputTokens = 8192, thinkingBudget = 1024,
            ),
        )
        val questions = GeminiParsing.parseQuiz(raw)
        if (questions.isEmpty()) throw GeminiException(GeminiException.Kind.THIN_NOTES, "no questions")
        return questions
    }

    /** Stage 2b — grade one guess against the stored reference answer + snippet only. */
    suspend fun grade(question: QuizQuestion, guess: String): GradeResult =
        GeminiParsing.parseGrade(
            gemini.collect(
                GenerationRequest(
                    Prompts.SYSTEM_GROUNDING, Prompts.grade(question, guess),
                    jsonSchema = Prompts.GRADE_SCHEMA, temperature = 0.7, maxOutputTokens = 1024,
                ),
            ),
        )

    /** Stage 4 — 80/20 triage for the lockscreen card. */
    suspend fun triage(notes: String, urgency: Urgency): TriageCard {
        val card = GeminiParsing.parseTriage(
            gemini.collect(
                GenerationRequest(
                    Prompts.SYSTEM_GROUNDING, Prompts.triage(notes, urgency),
                    jsonSchema = Prompts.TRIAGE_SCHEMA, temperature = 0.3,
                    maxOutputTokens = 8192, thinkingBudget = 1024,
                ),
            ),
        )
        if (card.items.isEmpty()) throw GeminiException(GeminiException.Kind.THIN_NOTES, "no triage items")
        return card
    }
}
