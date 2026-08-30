package com.notcan.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
 * La identidad visual principal toma como referencia UIcons/Flaticon. Las clases CSS
 * de Flaticon son referencias web y no se pueden dibujar directamente en Jetpack
 * Compose; hasta integrar los SVG individuales usamos equivalentes Material offline.
 */
object NotCanIcons {
    val Subjects: ImageVector = Icons.Default.School
    val Calendar: ImageVector = Icons.Default.CalendarMonth

    // TuNot debe verse como chatbot, no como una función genérica de “IA/magia”.
    // Referencia definitiva elegida: fi fi-rr-chatbot.
    val TuNot: ImageVector = Icons.Default.ChatBubbleOutline

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

data class FlaticonReference(
    val purpose: String,
    val cssClass: String,
    val note: String = "Referencia visual UIcons/Flaticon; integrar SVG individual y conservar atribución según licencia del recurso."
)

val NotCanFlaticonReferences = listOf(
    FlaticonReference("Clases", "fi fi-ts-workshop"),
    FlaticonReference("Estudio", "fi fi-ss-user-graduate"),
    FlaticonReference("Materias", "fi fi-ts-diary-bookmark-down"),
    FlaticonReference("TuNot", "fi fi-rr-chatbot"),
    FlaticonReference("Calendario", "fi fi-tr-calendar-clock"),
    FlaticonReference("Menú", "fi fi-tr-square-ellipsis-vertical"),
    FlaticonReference("Grabación", "fi fi-rr-microphone"),
    FlaticonReference("Transcripción", "fi fi-tr-transcription"),
    FlaticonReference("Apuntes", "fi fi-tr-pen-field"),
    FlaticonReference("Calificaciones", "fi fi-ts-notebook-alt"),
    FlaticonReference("Ajustes", "fi fi-tc-admin-alt")
)
