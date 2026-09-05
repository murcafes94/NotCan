package com.notcan.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notcan.app.performance.PerformanceMetricsStore
import com.notcan.app.storage.StorageMaintenance
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Controles seguros para caché y mediciones locales de rendimiento. */
@Composable
internal fun StoragePerformanceSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val performance = remember(context) { PerformanceMetricsStore(context.applicationContext) }
    var snapshot by remember { mutableStateOf<StorageMaintenance.CacheSnapshot?>(null) }
    var metrics by remember { mutableStateOf<PerformanceMetricsStore.Snapshot?>(null) }
    var runtime by remember { mutableStateOf<PerformanceMetricsStore.RuntimeSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val data = withContext(Dispatchers.IO) {
            Triple(
                StorageMaintenance.cacheSnapshot(context),
                performance.snapshot(),
                performance.runtimeSnapshot()
            )
        }
        snapshot = data.first
        metrics = data.second
        runtime = data.third
    }

    LaunchedEffect(Unit) { refresh() }

    Card(
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = NotCanBlue)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Rendimiento y almacenamiento", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Mide el dispositivo y controla temporales sin tocar tus modelos ni archivos.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
            }

            val current = snapshot
            if (current == null) {
                Text("Calculando caché…", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            } else {
                val warning = current.bytes >= 512L * 1024L * 1024L
                Text(
                    "Caché temporal: ${formatBytes(current.bytes)} · ${current.files} archivo(s)",
                    color = if (warning) MaterialTheme.colorScheme.error else NotCanBlue,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (warning) {
                        "La caché está alta. Optimizar puede liberar temporales antiguos de Whisper, Groq, TuNot y visores."
                    } else {
                        "NotCan mantiene una caché acotada para evitar que los temporales crezcan sin límite."
                    },
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                StorageMaintenance.cleanupTransientCache(context, aggressive = true)
                            }
                            refresh()
                            busy = false
                            message = if (result.bytesFreed > 0L) {
                                "Liberados ${formatBytes(result.bytesFreed)} en ${result.filesRemoved} temporal(es)."
                            } else {
                                "No había temporales antiguos seguros para eliminar."
                            }
                        }
                    }
                ) { Text(if (busy) "Optimizando…" else "Optimizar ahora") }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                ) { Text("Ajustes del sistema") }
            }

            message?.let {
                Text(it, color = NotCanBlue, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Mediciones reales del dispositivo", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            Text(
                "Se guardan solo tiempos y uso de recursos en este dispositivo; no se envía contenido ni telemetría a servidores.",
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )

            metrics?.let { m ->
                MetricLine("Arranque hasta primera composición", formatTimeMetric(m.startupMs))
                MetricLine("Abrir materia / datos listos", formatTimeMetric(m.subjectOpenMs))

                if (m.gemmaLoadMs > 0L || m.gemmaTotalMs > 0L) {
                    val backend = m.gemmaBackend.ifBlank { "motor local" }
                    MetricLine("Gemma · carga del motor", "${formatTimeMetric(m.gemmaLoadMs)} · $backend")
                    MetricLine("Gemma · primer token", formatTimeMetric(m.gemmaFirstTokenMs))
                    MetricLine("Gemma · respuesta completa", formatTimeMetric(m.gemmaTotalMs))
                    if (m.estimatedTokensPerSecond > 0.0) {
                        MetricLine("Gemma · velocidad estimada", "≈ %.1f tokens/s · %,d caracteres".format(m.estimatedTokensPerSecond, m.gemmaOutputChars))
                    }
                    if (m.gemmaPromptChars > 0) {
                        MetricLine("Contexto enviado a Gemma", "%,d caracteres".format(m.gemmaPromptChars))
                    }
                } else {
                    Text("Gemma: aún no hay una respuesta local medida.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }

                if (m.transcriptionMs > 0L) {
                    MetricLine(
                        "Última transcripción",
                        "${formatDuration(m.transcriptionMs)} · ${m.transcriptionProvider.ifBlank { "Whisper" }}"
                    )
                    if (m.transcriptionAudioMs > 0L) {
                        MetricLine(
                            "Audio procesado",
                            "${formatDuration(m.transcriptionAudioMs)} · %.2f× tiempo real".format(m.transcriptionRealtimeFactor)
                        )
                    }
                } else {
                    Text("Transcripción: aún no hay una sesión medida.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }

                if (m.whisperConvertMs > 0L || m.whisperModelLoadMs > 0L || m.whisperInferenceMs > 0L) {
                    MetricLine("Whisper local · convertir audio", formatTimeMetric(m.whisperConvertMs))
                    MetricLine("Whisper local · cargar modelo", formatTimeMetric(m.whisperModelLoadMs))
                    MetricLine("Whisper local · inferencia", formatDuration(m.whisperInferenceMs))
                }
            }

            runtime?.let { r ->
                MetricLine("RAM actual", "%.0f MB PSS · Java %.0f MB · nativa %.0f MB".format(r.pssMb, r.javaHeapMb, r.nativeHeapMb))
                MetricLine(
                    "Estado térmico Android",
                    r.thermalLabel,
                    warning = r.thermallyConstrained
                )
                if (r.thermallyConstrained) {
                    Text(
                        "Android ya está reduciendo rendimiento por temperatura. NotCan evita añadir carga de diagnóstico automática; deja enfriar el dispositivo antes de comparar Gemma o Whisper.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { refresh() } }) { Text("Actualizar métricas") }
                OutlinedButton(onClick = {
                    performance.clearMeasurements()
                    scope.launch { refresh() }
                }) { Text("Reiniciar mediciones") }
            }

            Text(
                "La limpieza manual conserva archivos recientes para no interrumpir una transcripción o una tarea en curso. Gemma, Whisper, Moonshine, grabaciones, documentos y base de datos quedan fuera de esta limpieza.",
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, warning: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = NotCanGray, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            color = if (warning) MaterialTheme.colorScheme.error else NotCanOffWhite,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimeMetric(ms: Long): String = when {
    ms <= 0L -> "Sin medir"
    ms < 1_000L -> "$ms ms"
    else -> "%.2f s".format(ms / 1_000.0)
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "Sin medir"
    if (ms < 60_000L) return "%.1f s".format(ms / 1_000.0)
    val totalSeconds = ms / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    val mb = value / (1024.0 * 1024.0)
    return if (mb < 1024.0) {
        if (mb < 10.0) "%.1f MB".format(mb) else "%.0f MB".format(mb)
    } else {
        "%.2f GB".format(mb / 1024.0)
    }
}
