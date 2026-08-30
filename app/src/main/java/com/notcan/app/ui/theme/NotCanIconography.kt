package com.notcan.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Iconografía unificada de NotCan.
 *
 * Dirección visual: iconos académicos simples, redondeados y de lectura rápida,
 * tomando como referencia principal familias gratuitas de Flaticon de estilo
 * Basic Rounded / Special Lineal. Mientras los assets binarios de Flaticon no se
 * integren directamente al repositorio, estos símbolos Material equivalentes
 * mantienen una silueta y peso coherentes y funcionan 100 % offline.
 *
 * Referencias Flaticon revisadas (licencia gratuita con atribución):
 * - Estudiar #566944 / #2825038
 * - Chatbot #9485982
 * - Micrófono #2097645
 * - Ajustes #535679
 *
 * Si se sustituyen por SVG/PNG originales de Flaticon, conservar la atribución
 * correspondiente en Ajustes → Créditos visuales.
 */
object NotCanIcons {
    val Subjects: ImageVector = Icons.Default.School
    val Calendar: ImageVector = Icons.Default.CalendarMonth
    val TuNot: ImageVector = Icons.Default.AutoAwesome
    val Grades: ImageVector = Icons.Default.Grade
    val Settings: ImageVector = Icons.Default.Settings

    val Audio: ImageVector = Icons.Default.MicNone
    val Transcript: ImageVector = Icons.Default.Description
    val Notes: ImageVector = Icons.Default.EditNote
    val Study: ImageVector = Icons.Default.MenuBook
    val Sources: ImageVector = Icons.Default.Source
    val Chat: ImageVector = Icons.Default.ChatBubbleOutline
    val Quiz: ImageVector = Icons.Default.Quiz
    val Tasks: ImageVector = Icons.Default.FactCheck

    val Schedule: ImageVector = Icons.Default.Schedule
    val Focus: ImageVector = Icons.Default.CenterFocusStrong
    val More: ImageVector = Icons.Default.MoreVert
    val Add: ImageVector = Icons.Default.Add
    val Back: ImageVector = Icons.Default.ArrowBack
    val Next: ImageVector = Icons.Default.ChevronRight
}

data class NotCanVisualCredit(
    val resource: String,
    val authorOrPack: String,
    val sourceUrl: String
)

val NotCanVisualCredits = listOf(
    NotCanVisualCredit("Estudiar", "Flaticon · Time management / University", "https://www.flaticon.es/icono-gratis/estudiar_566944"),
    NotCanVisualCredit("Chatbot", "Flaticon · Technology of the Future", "https://www.flaticon.es/icono-gratis/chatbot_9485982"),
    NotCanVisualCredit("Micrófono", "Flaticon · Freepik · Basic Rounded Lineal", "https://www.flaticon.es/icono-gratis/microfono_2097645"),
    NotCanVisualCredit("Ajustes", "Flaticon · Tomas Knop", "https://www.flaticon.es/icono-gratis/ajustes_535679")
)
