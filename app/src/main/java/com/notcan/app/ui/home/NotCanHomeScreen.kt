package com.notcan.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notcan.app.recording.RecordingState
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGraphite
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface

@Composable
fun NotCanHomeScreen(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val wide = maxWidth >= 800.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    StudySidebar()
                } else {
                    CompactNavigation()
                }

                ClassWorkspace(
                    modifier = Modifier.weight(1f),
                    recordingState = recordingState,
                    onStartRecording = onStartRecording,
                    onPauseRecording = onPauseRecording,
                    onResumeRecording = onResumeRecording,
                    onStopRecording = onStopRecording,
                    onMarkMoment = onMarkMoment
                )
            }
        }
    }
}

@Composable
private fun StudySidebar() {
    Surface(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight(),
        color = NotCanGraphite
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("NotCan", color = NotCanOffWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Tu espacio académico", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(28.dp))

            SidebarItem(Icons.Default.Book, "Materias", selected = true)
            SidebarItem(Icons.Default.School, "Clases")
            SidebarItem(Icons.Default.LibraryBooks, "Biblioteca")
            SidebarItem(Icons.Default.Description, "Apuntes")
            SidebarItem(Icons.Default.Star, "Estudio final")

            Spacer(Modifier.weight(1f))
            Text("Ciclo activo", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
            Text("2026 · Segundo semestre", color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)
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
private fun CompactNavigation() {
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
    }
}

@Composable
private fun ClassWorkspace(
    modifier: Modifier,
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text("Antropología Teológica", color = NotCanGray, style = MaterialTheme.typography.labelLarge)
            Text(
                "Clase 2 · El diseño eterno de Dios sobre el hombre",
                color = NotCanOffWhite,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(18.dp))
            WorkspaceTabs()
            Spacer(Modifier.height(18.dp))
            DemoClassContent()
        }

        RecordingControls(
            state = recordingState,
            onStart = onStartRecording,
            onPause = onPauseRecording,
            onResume = onResumeRecording,
            onStop = onStopRecording,
            onMark = onMarkMoment,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
        )
    }
}

@Composable
private fun WorkspaceTabs() {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Transcripción", "Apuntes", "PDF", "EPUB", "Mapa mental")

    TabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
        contentColor = NotCanBlue,
        divider = { }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selected == index,
                onClick = { selected = index },
                text = { Text(title, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun DemoClassContent() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NotCanSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Audio de la clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("La grabación local permanece como fuente segura.", color = NotCanGray)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(NotCanGraphite, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Forma de onda y reproductor", color = NotCanGray)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoCard("Todo conectado", "Audio, transcripción, documentos y notas comparten la misma clase.", Modifier.weight(1f))
            InfoCard("Estudio semestral", "Las clases de una materia se consultarán como un conjunto al cerrar el ciclo.", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NotCanGraphite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecordingControls(
    state: RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val active = state is RecordingState.Recording || state is RecordingState.Paused

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (active) {
            RoundControl(
                icon = Icons.Default.Star,
                contentDescription = "Marcar momento importante",
                tint = NotCanOffWhite,
                background = NotCanBlue,
                onClick = onMark
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = active && expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is RecordingState.Recording -> RoundControl(
                            icon = Icons.Default.Pause,
                            contentDescription = "Pausar grabación",
                            tint = NotCanOffWhite,
                            background = NotCanSurface,
                            onClick = onPause
                        )
                        is RecordingState.Paused -> RoundControl(
                            icon = Icons.Default.PlayArrow,
                            contentDescription = "Reanudar grabación",
                            tint = NotCanOffWhite,
                            background = NotCanSurface,
                            onClick = onResume
                        )
                        else -> Unit
                    }
                    RoundControl(
                        icon = Icons.Default.Stop,
                        contentDescription = "Detener grabación",
                        tint = NotCanOffWhite,
                        background = NotCanSurface,
                        onClick = onStop
                    )
                }
            }

            if (!active) {
                RoundControl(
                    icon = Icons.Default.RadioButtonChecked,
                    contentDescription = "Comenzar grabación",
                    tint = NotCanRed,
                    background = NotCanGraphite,
                    onClick = onStart
                )
            } else {
                RoundControl(
                    icon = Icons.Default.Circle,
                    contentDescription = "Controles de grabación",
                    tint = NotCanRed,
                    background = NotCanGraphite,
                    onClick = { expanded = !expanded }
                )
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = background,
        shadowElevation = 5.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}
