package com.lifehackd.studycrammer.data

/** Exam countdown chosen on the Panic Router. Drives prompt depth and quiz length. */
enum class Urgency(
    val label: String,
    val sublabel: String,
    val emoji: String,
    val quizSize: Int,
    val promptHint: String,
) {
    DAY(
        label = "24 Hours Left",
        sublabel = "Cover everything, keep it tight",
        emoji = "🕐",
        quizSize = 10,
        promptHint = "About 24 hours until the exam. Be thorough but compact; cover the full breadth of the notes.",
    ),
    HOURS(
        label = "3 Hours Left",
        sublabel = "Big ideas only",
        emoji = "⏳",
        quizSize = 7,
        promptHint = "About 3 hours until the exam. Prioritise the big ideas; skip minor details.",
    ),
    CODE_RED(
        label = "45 Minutes Left — Code Red",
        sublabel = "Ruthless triage mode",
        emoji = "🚨",
        quizSize = 5,
        promptHint = "45 minutes until the exam — CODE RED. Be ruthless: only what earns marks, shortest possible wording.",
    ),
}

/** The four study workflows. */
enum class Stage(val number: Int, val title: String, val tagline: String) {
    TRANSLATOR(1, "WTF Did I Just Read?", "5 bullets a 5th-grader gets + analogies that stick"),
    QUIZ(2, "Active-Recall Quizmaster", "One question at a time. No peeking."),
    TRAPS(3, "Read My Professor's Mind", "Trick questions, confusable terms, high-yield defs"),
    TRIAGE(4, "Code-Red 60-Minute Triage", "The 20% that scores 80% → lockscreen card"),
}

data class StudySession(
    val sourceName: String? = null,
    val chunks: List<String> = emptyList(),
    val activeChunk: Int = 0,
    val urgency: Urgency = Urgency.HOURS,
    val fingerprint: Int = 0,
) {
    val notes: String get() = chunks.getOrNull(activeChunk).orEmpty()
    val hasNotes: Boolean get() = notes.length >= MIN_NOTES_CHARS
    val totalChars: Int get() = chunks.sumOf { it.length }

    companion object {
        const val MIN_NOTES_CHARS = 80
    }
}

data class QuizQuestion(
    val question: String,
    val answer: String,
    val sourceSnippet: String,
    val difficulty: String,
)

enum class Verdict { CORRECT, PARTIAL, INCORRECT }

data class GradeResult(val verdict: Verdict, val feedback: String, val recap: String)

data class TriageItem(
    val concept: String,
    val whyItScores: String,
    val memoryHook: String,
    val sourceSnippet: String,
)

data class TriageCard(val headline: String, val items: List<TriageItem>, val lastMinuteTip: String)
