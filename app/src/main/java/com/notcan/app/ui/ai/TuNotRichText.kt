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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * Renderizador Markdown ligero para respuestas de TuNot.
 * Está orientado a apuntes: títulos, listas, citas, énfasis, enlaces y código inline,
 * evitando mostrar marcadores Markdown crudos en la conversación.
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
    var insideCodeFence = false

    lines.forEachIndexed { index, sourceLine ->
        val line = sourceLine.trimEnd()
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```")) {
            insideCodeFence = !insideCodeFence
            if (index != lines.lastIndex && !insideCodeFence) append('\n')
            return@forEachIndexed
        }

        if (trimmed.matches(Regex("^[-*_]{3,}$"))) {
            if (index != lines.lastIndex) append('\n')
            return@forEachIndexed
        }

        if (insideCodeFence) {
            val start = length
            append(line)
            addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, length)
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
            val checkbox = Regex("^[-*]\\s+\\[([ xX])]\s+").find(trimmed)
            when {
                checkbox != null -> {
                    append(if (checkbox.groupValues[1].isBlank()) "☐ " else "☑ ")
                    appendInlineMarkdown(trimmed.substring(checkbox.range.last + 1))
                }
                bullet != null -> {
                    append("• ")
                    appendInlineMarkdown(trimmed.substring(bullet.range.last + 1))
                }
                numbered != null -> {
                    append(numbered.groupValues[1])
                    append(". ")
                    appendInlineMarkdown(trimmed.substring(numbered.range.last + 1))
                }
                trimmed.startsWith("> ") -> {
                    val start = length
                    append("› ")
                    appendInlineMarkdown(trimmed.removePrefix("> "))
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }
                trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                    appendInlineMarkdown(
                        trimmed.trim('|')
                            .split('|')
                            .map { it.trim() }
                            .filterNot { it.matches(Regex(":?-{2,}:?")) }
                            .joinToString(" · ")
                    )
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
                    append(cleanInlineLinks(value.substring(cursor + 2, end)))
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    cursor = end + 2
                } else {
                    append(value[cursor])
                    cursor++
                }
            }
            value.startsWith("__", cursor) -> {
                val end = value.indexOf("__", cursor + 2)
                if (end > cursor + 2) {
                    val start = length
                    append(cleanInlineLinks(value.substring(cursor + 2, end)))
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
                    append('`')
                    cursor++
                }
            }
            value[cursor] == '*' || value[cursor] == '_' -> {
                val marker = value[cursor]
                val end = value.indexOf(marker, cursor + 1)
                if (end > cursor + 1) {
                    val start = length
                    append(cleanInlineLinks(value.substring(cursor + 1, end)))
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    cursor = end + 1
                } else {
                    append(marker)
                    cursor++
                }
            }
            value[cursor] == '[' -> {
                val closeLabel = value.indexOf(']', cursor + 1)
                val openUrl = closeLabel + 1
                if (closeLabel > cursor && openUrl < value.length && value[openUrl] == '(') {
                    val closeUrl = value.indexOf(')', openUrl + 1)
                    if (closeUrl > openUrl) {
                        append(value.substring(cursor + 1, closeLabel))
                        cursor = closeUrl + 1
                    } else {
                        append(value[cursor])
                        cursor++
                    }
                } else {
                    append(value[cursor])
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

private fun cleanInlineLinks(value: String): String = value.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
