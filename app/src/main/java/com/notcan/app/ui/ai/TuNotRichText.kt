package com.notcan.app.ui.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * Renderizador Markdown ligero para respuestas de TuNot.
 * Soporta títulos, listas, negrita y código inline sin mostrar los marcadores crudos.
 */
@Composable
internal fun TuNotRichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val annotated = remember(text) { tuNotMarkdown(text) }
    Text(text = annotated, modifier = modifier, color = color, style = style)
}

private fun tuNotMarkdown(value: String): AnnotatedString = buildAnnotatedString {
    val lines = value.replace("\r\n", "\n").split('\n')
    lines.forEachIndexed { index, sourceLine ->
        val line = sourceLine.trimEnd()
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```")) {
            if (index != lines.lastIndex) append('\n')
            return@forEachIndexed
        }

        val headingLevel = when {
            trimmed.startsWith("### ") -> 3
            trimmed.startsWith("## ") -> 2
            trimmed.startsWith("# ") -> 1
            else -> 0
        }
        val headingText = when (headingLevel) {
            3 -> trimmed.removePrefix("### ")
            2 -> trimmed.removePrefix("## ")
            1 -> trimmed.removePrefix("# ")
            else -> trimmed
        }

        if (headingLevel > 0) {
            val start = length
            appendInlineMarkdown(headingText)
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = when (headingLevel) {
                        1 -> 1.18.em
                        2 -> 1.12.em
                        else -> 1.06.em
                    }
                ),
                start,
                length
            )
        } else {
            val bullet = Regex("^[-*•]\\s+").find(trimmed)
            val numbered = Regex("^(\\d+)[.)]\\s+").find(trimmed)
            when {
                bullet != null -> {
                    append("• ")
                    appendInlineMarkdown(trimmed.substring(bullet.range.last + 1))
                }
                numbered != null -> {
                    append(numbered.groupValues[1])
                    append(". ")
                    appendInlineMarkdown(trimmed.substring(numbered.range.last + 1))
                }
                else -> appendInlineMarkdown(trimmed)
            }
        }
        if (index != lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(value: String) {
    var cursor = 0
    while (cursor < value.length) {
        when {
            value.startsWith("**", cursor) -> {
                val end = value.indexOf("**", cursor + 2)
                if (end > cursor + 2) {
                    val start = length
                    append(value.substring(cursor + 2, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    cursor = end + 2
                } else {
                    append(value[cursor])
                    cursor++
                }
            }
            value[cursor] == '`' -> {
                val end = value.indexOf('`', cursor + 1)
                if (end > cursor + 1) {
                    val start = length
                    append(value.substring(cursor + 1, end))
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, length)
                    cursor = end + 1
                } else {
                    cursor++
                }
            }
            value[cursor] == '*' || value[cursor] == '_' -> {
                val marker = value[cursor]
                val end = value.indexOf(marker, cursor + 1)
                if (end > cursor + 1) {
                    val start = length
                    append(value.substring(cursor + 1, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, length)
                    cursor = end + 1
                } else {
                    append(marker)
                    cursor++
                }
            }
            else -> {
                append(value[cursor])
                cursor++
            }
        }
    }
}
