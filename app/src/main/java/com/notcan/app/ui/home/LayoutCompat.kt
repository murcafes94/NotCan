package com.notcan.app.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

/**
 * Temporary v0.4 bridge used by the PDF annotator where a child modifier is
 * created outside the lexical RowScope. Row/Column scope-specific weight
 * extensions continue to take precedence where they are available.
 */
internal fun Modifier.weight(@Suppress("UNUSED_PARAMETER") value: Float): Modifier = fillMaxWidth()
