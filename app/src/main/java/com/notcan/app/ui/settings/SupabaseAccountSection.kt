package com.notcan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notcan.app.sync.SupabaseAuthClient
import com.notcan.app.sync.SupabaseSession
import com.notcan.app.sync.SupabaseSyncManager
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SupabaseAccountSection() {
    val context = LocalContext.current
    val auth = remember(context) { SupabaseAuthClient(context.applicationContext) }
    val sync = remember(context) { SupabaseSyncManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SupabaseSession?>(auth.currentSession()) }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun syncAccount() {
        if (busy) return
        busy = true
        message = "Sincronizando…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { sync.syncNow() } }
            message = result.fold(
                onSuccess = { it.message },
                onFailure = { it.message ?: "No se pudo sincronizar." }
            )
            session = auth.currentSession()
            busy = false
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.CloudSync, contentDescription = null, tint = NotCanBlue)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Cuenta y sincronización", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text(
                        session?.email?.takeIf { it.isNotBlank() } ?: "NotCan sigue funcionando sin cuenta",
                        color = if (session != null) NotCanBlue else NotCanGray,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (session == null) {
                Text(
                    "Inicia sesión con la misma cuenta de la web para compartir ciclos, materias, clases, apuntes y calificaciones. Tus audios y documentos pesados siguen locales por ahora.",
                    color = NotCanGray,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; message = null },
                    label = { Text("Correo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && email.isNotBlank() && password.length >= 6,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { auth.signIn(email, password) }
                                }
                                result.onSuccess {
                                    session = it
                                    password = ""
                                    message = "Sesión iniciada. Sincronizando tus datos…"
                                    val synced = withContext(Dispatchers.IO) { runCatching { sync.syncNow() } }
                                    message = synced.fold(
                                        onSuccess = { value -> value.message },
                                        onFailure = { error -> error.message ?: "Sesión iniciada; la sincronización quedó pendiente." }
                                    )
                                }.onFailure { error ->
                                    message = error.message ?: "No se pudo iniciar sesión."
                                }
                                busy = false
                            }
                        }
                    ) { Text("Iniciar sesión") }
                    OutlinedButton(
                        enabled = !busy && email.isNotBlank() && password.length >= 6,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { auth.signUp(email, password) }
                                }
                                result.onSuccess { signUp ->
                                    session = signUp.session
                                    password = ""
                                    message = if (signUp.confirmationRequired) {
                                        "Cuenta creada. Confirma el correo y luego inicia sesión en NotCan."
                                    } else {
                                        "Cuenta creada y sesión iniciada."
                                    }
                                    if (signUp.session != null) {
                                        val synced = withContext(Dispatchers.IO) { runCatching { sync.syncNow() } }
                                        synced.onSuccess { message = it.message }
                                    }
                                }.onFailure { error ->
                                    message = error.message ?: "No se pudo crear la cuenta."
                                }
                                busy = false
                            }
                        }
                    ) { Text("Crear cuenta") }
                }
            } else {
                Text(
                    "La sesión se guarda cifrada con Android Keystore. La sincronización respeta la cuenta mediante RLS de Supabase.",
                    color = NotCanGray,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = ::syncAccount) { Text(if (busy) "Sincronizando…" else "Sincronizar ahora") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                withContext(Dispatchers.IO) { runCatching { auth.signOut() } }
                                session = null
                                password = ""
                                message = "Sesión cerrada. Tus datos locales permanecen en el dispositivo."
                                busy = false
                            }
                        }
                    ) { Text("Cerrar sesión") }
                }
            }
            message?.let { Text(it, color = NotCanGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
    }
}
