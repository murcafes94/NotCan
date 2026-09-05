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
import com.notcan.app.storage.StorageMaintenance
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Controles seguros para vigilar y recortar únicamente la caché reconstruible de NotCan. */
@Composable
internal fun StoragePerformanceSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<StorageMaintenance.CacheSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        snapshot = withContext(Dispatchers.IO) { StorageMaintenance.cacheSnapshot(context) }
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
                    Text("Controla temporales sin tocar tus modelos ni archivos.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
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
            Text(
                "La limpieza manual conserva archivos recientes para no interrumpir una transcripción o una tarea en curso. Gemma, Whisper, Moonshine, grabaciones, documentos y base de datos quedan fuera de esta limpieza.",
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
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
