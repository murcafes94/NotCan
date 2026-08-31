package com.notcan.app.sync

import android.content.Context
import com.notcan.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseAuthClient(context: Context) {
    private val store = SupabaseAccountStore(context.applicationContext)

    data class SignUpResult(val session: SupabaseSession?, val confirmationRequired: Boolean)

    fun currentSession(): SupabaseSession? = store.load()

    fun signIn(email: String, password: String): SupabaseSession {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val response = request("POST", "/auth/v1/token?grant_type=password", body)
        return parseAndSaveSession(response, email.trim())
    }

    fun signUp(email: String, password: String): SignUpResult {
        val redirect = URLEncoder.encode("https://murcafes94.github.io/NotCan/", Charsets.UTF_8.name())
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val response = request("POST", "/auth/v1/signup?redirect_to=$redirect", body)
        val access = response.optString("access_token")
        if (access.isBlank()) return SignUpResult(null, confirmationRequired = true)
        return SignUpResult(parseAndSaveSession(response, email.trim()), confirmationRequired = false)
    }

    fun ensureSession(): SupabaseSession? {
        val current = store.load() ?: return null
        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAtEpochSec > now + 90L) return current
        if (current.refreshToken.isBlank()) {
            store.clear()
            return null
        }
        return runCatching {
            val body = JSONObject().put("refresh_token", current.refreshToken)
            parseAndSaveSession(
                request("POST", "/auth/v1/token?grant_type=refresh_token", body),
                current.email
            )
        }.getOrElse {
            store.clear()
            null
        }
    }

    fun signOut() {
        val session = store.load()
        if (session != null) runCatching { request("POST", "/auth/v1/logout", JSONObject(), session.accessToken) }
        store.clear()
    }

    private fun parseAndSaveSession(root: JSONObject, fallbackEmail: String): SupabaseSession {
        val access = root.optString("access_token")
        val refresh = root.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) error("Supabase no devolvió una sesión válida.")
        val user = root.optJSONObject("user") ?: error("Supabase no devolvió el usuario.")
        val userId = user.optString("id")
        if (userId.isBlank()) error("La sesión no contiene un identificador de usuario.")
        val now = System.currentTimeMillis() / 1000L
        val expiresAt = root.optLong("expires_at").takeIf { it > now }
            ?: (now + root.optLong("expires_in", 3600L))
        val session = SupabaseSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = user.optString("email").ifBlank { fallbackEmail },
            expiresAtEpochSec = expiresAt
        )
        store.save(session)
        return session
    }

    private fun request(method: String, path: String, body: JSONObject? = null, accessToken: String? = null): JSONObject {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) doOutput = true
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val message = root.optString("msg")
                    .ifBlank { root.optString("message") }
                    .ifBlank { root.optString("error_description") }
                    .ifBlank { root.optString("error") }
                    .ifBlank { "Error de Supabase ($code)" }
                error(message)
            }
            root
        } finally {
            connection.disconnect()
        }
    }
}
