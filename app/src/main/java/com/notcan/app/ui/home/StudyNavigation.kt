package com.notcan.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGraphite
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class CreateDialog {
    Cycle,
    Subject,
    Class
}

@Composable
internal fun StudySidebar(
    cycles: List<StudyCycleEntity>,
    selectedCycleId: String?,
    onSelectCycle: (String) -> Unit,
    onAddCycle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(224.dp)
            .fillMaxHeight(),
        color = NotCanGraphite
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "NotCan",
                color = NotCanOffWhite,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Tu espacio académico", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(26.dp))

            SidebarItem(Icons.Default.Book, "Materias", selected = true)
            SidebarItem(Icons.Default.School, "Clases")
            SidebarItem(Icons.Default.LibraryBooks, "Biblioteca")
            SidebarItem(Icons.Default.Description, "Apuntes")
            SidebarItem(Icons.Default.Star, "Estudio final")

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CICLOS", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                FilledTonalIconButton(onClick = onAddCycle, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Crear ciclo")
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cycles, key = { it.id }) { cycle ->
                    val selected = cycle.id == selectedCycleId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCycle(cycle.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) NotCanBlue.copy(alpha = 0.17f) else Color.Transparent
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cycle.isActive) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .background(NotCanBlue, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                cycle.name,
                                color = if (selected) NotCanOffWhite else NotCanGray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean = false) {
    val background = if (selected) NotCanBlue.copy(alpha = 0.18f) else Color.Transparent
    val foreground = if (selected) NotCanOffWhite else NotCanGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) NotCanBlue else foreground)
        Spacer(Modifier.width(12.dp))
        Text(label, color = foreground)
    }
}

@Composable
internal fun CompactNavigation(
    hasCycle: Boolean,
    onAddCycle: () -> Unit
) {
    NavigationRail(containerColor = NotCanGraphite) {
        NavigationRailItem(
            selected = true,
            onClick = { },
            icon = { Icon(Icons.Default.Book, contentDescription = "Materias") },
            label = { Text("Materias") }
        )
        NavigationRailItem(
            selected = false,
            onClick = { },
            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Biblioteca") },
            label = { Text("Biblioteca") }
        )
        NavigationRailItem(
            selected = false,
            onClick = onAddCycle,
            icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo ciclo") },
            label = { Text(if (hasCycle) "Ciclo" else "Crear") }
        )
    }
}

@Composable
internal fun StudyNavigator(
    modifier: Modifier,
    subjects: List<SubjectEntity>,
    classes: List<ClassSessionEntity>,
    selectedSubjectId: String?,
    selectedClassId: String?,
    onSelectSubject: (String) -> Unit,
    onSelectClass: (String) -> Unit,
    onAddSubject: () -> Unit,
    onAddClass: () -> Unit
) {
    Surface(modifier = modifier.fillMaxHeight(), color = NotCanSurface.copy(alpha = 0.72f)) {
        Column(Modifier.padding(14.dp)) {
            SectionHeader("Materias", enabled = true, onAdd = onAddSubject)
            Spacer(Modifier.height(8.dp))

            if (subjects.isEmpty()) {
                SmallEmptyMessage("Crea una materia dentro del ciclo seleccionado.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(0.42f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(subjects, key = { it.id }) { subject ->
                        NavigatorItem(
                            title = subject.name,
                            selected = subject.id == selectedSubjectId,
                            onClick = { onSelectSubject(subject.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = NotCanGray.copy(alpha = 0.18f))
            Spacer(Modifier.height(12.dp))

            SectionHeader("Clases", enabled = selectedSubjectId != null, onAdd = onAddClass)
            Spacer(Modifier.height(8.dp))

            if (selectedSubjectId == null) {
                SmallEmptyMessage("Selecciona una materia para ver sus clases.")
            } else if (classes.isEmpty()) {
                SmallEmptyMessage("Aún no hay clases. Crea la primera con +.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(0.58f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(classes, key = { it.id }) { classSession ->
                        NavigatorItem(
                            title = classSession.title,
                            subtitle = formatClassDate(classSession.startedAtEpochMs),
                            selected = classSession.id == selectedClassId,
                            onClick = { onSelectClass(classSession.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, enabled: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onAdd, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Añadir $title")
        }
    }
}

@Composable
private fun NavigatorItem(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) NotCanBlue.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                color = if (selected) NotCanOffWhite else NotCanGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = NotCanGray.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SmallEmptyMessage(message: String) {
    Text(
        message,
        color = NotCanGray,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
internal fun NameEntryDialog(
    title: String,
    label: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(title) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!enabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Primero selecciona el nivel anterior.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = enabled && text.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun formatClassDate(epochMs: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMs))
