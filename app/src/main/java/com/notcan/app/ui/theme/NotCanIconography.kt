package com.notcan.app.ui.theme

import androidx.annotation.DrawableRes
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Source
import androidx.compose.ui.graphics.vector.ImageVector
import com.notcan.app.R

/**
 * Iconografía unificada de NotCan.
 *
 * Conservamos equivalentes Material para las áreas todavía no migradas y exponemos
 * VectorDrawable propios para los iconos aprobados de la interfaz académica.
 */
object NotCanIcons {
    val Home: ImageVector = Icons.Default.Home
    val Subjects: ImageVector = Icons.Default.School
    val Calendar: ImageVector = Icons.Default.CalendarMonth
    val TuNot: ImageVector = Icons.Default.SmartToy
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

/**
 * Selección vectorizada a partir de los iconos enviados para la interfaz de clases.
 * Úselos con painterResource y el tint de MaterialTheme para claro/oscuro.
 */
object NotCanDrawableIcons {
    @DrawableRes val Classes: Int = R.drawable.ic_notcan_classes
    @DrawableRes val Notes: Int = R.drawable.ic_notcan_notes
    @DrawableRes val Study: Int = R.drawable.ic_notcan_study
    @DrawableRes val Library: Int = R.drawable.ic_notcan_library
    @DrawableRes val Tasks: Int = R.drawable.ic_notcan_tasks
    @DrawableRes val Exam: Int = R.drawable.ic_notcan_exam
    @DrawableRes val Audio: Int = R.drawable.ic_notcan_mic
    @DrawableRes val TuNot: Int = R.drawable.ic_notcan_tunot
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
