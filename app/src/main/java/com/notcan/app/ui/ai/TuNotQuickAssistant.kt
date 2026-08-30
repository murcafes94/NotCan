package com.notcan.app.ui.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notcan.app.ai.OfflineTuNotEngine
import com.notcan.app.ui.maps.ParsedStudyMapArtifact
import com.notcan.app.ui.maps.StudyMapArtifactParser
import com.notcan.app.ui.maps.StudyMapScreen
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanGreen
import com.notcan.app.ui.theme.NotCanIcons
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurfaceHigh
import java.text.Normalizer

/** Material local que TuNot puede localizar sin Internet. */
data class TuNotOfflineEntry(
    val title: String,
    val subtitle: String,
    val text: String
)

private data class TuNotOfflineMatch(
    val entry: TuNotOfflineEntry,
    val snippet: String,
    val score: Int
)

@Composable
fun TuNotQuickAssistant(
    contextTitle: String,
    offlineEntries: List<TuNotOfflineEntry>,
    onlineConfigured: Boolean,
    onlineBusy: Boolean,
    onlineResult: String,
    suggestions: List<String>,
    onAskOnline: (String) -> Unit,
    onOpenFullChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var question by remember(contextTitle) { mutableStateOf("") }
    var localMatches by remember(contextTitle) { mutableStateOf<List<TuNotOfflineMatch>>(emptyList()) }
    var localAnswer by remember(contextTitle) { mutableStateOf("") }
    var localMap by remember(contextTitle) { mutableStateOf<ParsedStudyMapArtifact?>(null) }
    var lastOnlinePrompt by remember { mutableStateOf<String?>(null) }
    var online by remember(expanded) { mutableStateOf(isOnline(context)) }

    LaunchedEffect(expanded) {
        if (expanded) online = isOnline(context)
    }

    val onlineMap = remember(onlineResult) { StudyMapArtifactParser.parse(onlineResult) }
    val visibleOnlineText = remember(onlineResult) {
        if (onlineMap != null) StudyMapArtifactParser.stripArtifact(onlineResult) else onlineResult
    }

    fun submit() {
        val clean = question.trim()
        if (clean.isBlank() || onlineBusy) return
        localAnswer = ""
        localMap = null
        localMatches = emptyList()

        if (online && onlineConfigured) {
            lastOnlinePrompt = clean
            onAskOnline(
                "CONSULTA RÁPIDA DESDE NOTCAN\n" +
                    "Contexto visible: $contextTitle\n" +
                    "Responde de forma breve y útil para una ventana compacta.\n" +
                    clean
            )
        } else {
            lastOnlinePrompt = null
            if (OfflineTuNotEngine.isMapRequest(clean)) {
                val result = OfflineTuNotEngine.answerEntries(contextTitle, offlineEntries, clean)
                localMap = StudyMapArtifactParser.parse(result)
                localAnswer = if (localMap != null) {
                    "Mapa generado localmente con el material guardado."
                } else result
            } else {
                localMatches = searchOffline(clean, offlineEntries)
                if (localMatches.isEmpty()) {
                    localAnswer = OfflineTuNotEngine.answerEntries(contextTitle, offlineEntries, clean)
                }
            }
        }
    }

    Box(modifier) {
        if (expanded) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .widthIn(min = 310.dp, max = 430.dp)
                    .heightIn(max = 620.dp)
                    .padding(bottom = 62.dp),
                colors = CardDefaults.cardColors(containerColor = NotCanSurfaceHigh),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = NotCanBlue.copy(alpha = 0.16f), shape = CircleShape) {
                            Icon(
                                NotCanIcons.TuNot,
                                contentDescription = null,
                                tint = NotCanBlue,
                                modifier = Modifier.padding(9.dp).size(22.dp)
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("TuNot", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Text(
                                contextTitle,
                                color = NotCanGray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = (if (online && onlineConfigured) NotCanGreen else NotCanGray).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                if (online && onlineConfigured) "Online" else "Local",
                                color = if (online && onlineConfigured) NotCanGreen else NotCanGray,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(onClick = { expanded = false }) {
                            Text("×", color = NotCanGray, style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    Text(
                        if (online && onlineConfigured)
                            "Pregunta, resume o crea mapas. Si pierdes conexión, TuNot cambia al material local."
                        else
                            "Modo local: busca en tu material y genera mapas mentales o conceptuales sin Internet.",
                        color = NotCanGray,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        suggestions.take(4).forEach { suggestion ->
                            OutlinedButton(onClick = { question = suggestion }) { Text(suggestion) }
                        }
                    }

                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Pregunta o pide un mapa…") },
                        minLines = 1,
                        maxLines = 4,
                        trailingIcon = {
                            IconButton(enabled = question.isNotBlank() && !onlineBusy, onClick = ::submit) {
                                Icon(NotCanIcons.Next, contentDescription = "Enviar", tint = NotCanBlue)
                            }
                        }
                    )

                    when {
                        onlineBusy && lastOnlinePrompt != null -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("  TuNot está pensando…", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        lastOnlinePrompt != null && onlineMap != null -> {
                            HorizontalDivider()
                            Text(visibleOnlineText, color = NotCanOffWhite, style = MaterialTheme.typography.bodySmall)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                StudyMapScreen(
                                    map = onlineMap!!.map,
                                    initialLayout = onlineMap!!.preferredLayout,
                                    modifier = Modifier.fillMaxWidth().height(300.dp)
                                )
                            }
                        }
                        lastOnlinePrompt != null && visibleOnlineText.isNotBlank() -> {
                            HorizontalDivider()
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 230.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(visibleOnlineText, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        localMap != null -> {
                            HorizontalDivider()
                            Text(localAnswer, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                StudyMapScreen(
                                    map = localMap!!.map,
                                    initialLayout = localMap!!.preferredLayout,
                                    modifier = Modifier.fillMaxWidth().height(300.dp)
                                )
                            }
                        }
                        localMatches.isNotEmpty() -> {
                            HorizontalDivider()
                            Text("Encontré esto en tu material", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 245.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                localMatches.take(5).forEach { match ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(13.dp)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text(match.entry.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                                            Text(match.entry.subtitle, color = NotCanBlue, style = MaterialTheme.typography.labelSmall)
                                            Text(match.snippet, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        localAnswer.isNotBlank() -> {
                            HorizontalDivider()
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState())
                            ) {
                                Text(localAnswer, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onOpenFullChat) { Text("Abrir TuNot completo") }
                    }
                }
            }
        }

        FilledIconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.BottomEnd).size(54.dp),
            shape = CircleShape
        ) {
            Icon(
                NotCanIcons.TuNot,
                contentDescription = if (expanded) "Cerrar TuNot" else "Abrir TuNot",
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

private fun searchOffline(query: String, entries: List<TuNotOfflineEntry>): List<TuNotOfflineMatch> {
    val normalizedQuery = normalize(query)
    val tokens = normalizedQuery.split(Regex("\\s+"))
        .filter { it.length >= 2 }
        .distinct()
    if (tokens.isEmpty()) return emptyList()

    return entries.mapNotNull { entry ->
        val searchable = normalize("${entry.title} ${entry.subtitle} ${entry.text}")
        val tokenHits = tokens.count { searchable.contains(it) }
        if (tokenHits == 0) return@mapNotNull null
        val phraseBonus = if (normalizedQuery.length >= 4 && searchable.contains(normalizedQuery)) 6 else 0
        val titleBonus = tokens.count { normalize(entry.title).contains(it) } * 2
        val score = tokenHits * 3 + phraseBonus + titleBonus
        TuNotOfflineMatch(entry, makeSnippet(entry.text, tokens), score)
    }.sortedByDescending { it.score }
}

private fun makeSnippet(text: String, tokens: List<String>): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.isBlank()) return "Coincidencia en el título o nombre del recurso."
    val normalized = normalize(clean)
    val hit = tokens.map { normalized.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
    val start = (hit - 90).coerceAtLeast(0)
    val end = (hit + 300).coerceAtMost(clean.length)
    return buildString {
        if (start > 0) append("…")
        append(clean.substring(start, end))
        if (end < clean.length) append("…")
    }
}

private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")

private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
