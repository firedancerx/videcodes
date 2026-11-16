package com.lifehackd.studycrammer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lifehackd.studycrammer.data.GroundingStatus
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.theme.Mono
import com.lifehackd.studycrammer.ui.theme.TextMid

private val BULLET = Regex("^\\s*[-*•]\\s+(.*)$")
private val NUMBERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")

/**
 * Minimal Markdown renderer for streamed model output: #/##/### headings, bullets,
 * numbered items, "> " receipts (each with a live grounding check) and **bold** / `code`.
 * [verify] is null while streaming (the last line may be incomplete).
 */
@Composable
fun MarkdownText(markdown: String, verify: ((String) -> GroundingStatus)?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            val bullet = BULLET.matchEntire(line)
            val numbered = NUMBERED.matchEntire(line)
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("#") -> Text(
                    line.trimStart('#').trim(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Lime,
                    modifier = Modifier.padding(top = 10.dp),
                )
                line.trimStart().startsWith(">") -> Quote(line.trimStart().removePrefix(">").trim(), verify)
                bullet != null -> Row {
                    Text("▸ ", color = Lime, style = MaterialTheme.typography.bodyLarge)
                    Text(inlineSpans(bullet.groupValues[1]), style = MaterialTheme.typography.bodyLarge)
                }
                numbered != null -> Row {
                    Text("${numbered.groupValues[1]}. ", color = Lime, style = MaterialTheme.typography.bodyLarge)
                    Text(inlineSpans(numbered.groupValues[2]), style = MaterialTheme.typography.bodyLarge)
                }
                else -> Text(inlineSpans(line), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Quote(text: String, verify: ((String) -> GroundingStatus)?) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Lime.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "“${text.trim('"', '“', '”')}”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = TextMid,
            )
            if (verify != null && text.isNotBlank()) GroundingChip(verify(text))
        }
    }
}

/** **bold** and `code` spans. Unclosed markers (mid-stream) are rendered literally. */
fun inlineSpans(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) && s.indexOf("**", i + 2) > i + 2 -> {
                val end = s.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Lime)) { append(s.substring(i + 2, end)) }
                i = end + 2
            }
            s[i] == '`' && s.indexOf('`', i + 1) > i + 1 -> {
                val end = s.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = Mono)) { append(s.substring(i + 1, end)) }
                i = end + 1
            }
            else -> {
                append(s[i]); i++
            }
        }
    }
}
