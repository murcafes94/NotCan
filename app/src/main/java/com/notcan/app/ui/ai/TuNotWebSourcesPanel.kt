package com.notcan.app.ui.ai

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notcan.app.ai.WebResearchService
import com.notcan.app.sources.ClassSourceStore
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.net.URI

@Composable
internal fun TuNotWebSourcesPanel(
    store: ClassSourceStore,
    scopeKey: String,
    onSourcesChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val research = remember(context) { WebResearchService(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var query by remember(scopeKey) { mutableStateOf("") }
    var results by remember(scopeKey) { mutableStateOf<List<WebResearchService.Result>>(emptyList()) }
    var searching by remember(scopeKey) { mutableStateOf(false) }
    var savingUrl by remember(scopeKey) { mutableStateOf<String?>(null) }
    var error by remember(scopeKey) { mutableStateOf<String?>(null) }
    var opened by remember(scopeKey) { mutableStateOf<WebResearchService.Result?>(null) }
    var savedUrls by remember(scopeKey) { mutableStateOf(store.list(scopeKey).mapNotNull { it.sourceUrl }.toSet()) }

    fun refreshSaved() {
        savedUrls = store.list(scopeKey).mapNotNull { it.sourceUrl }.toSet()
        onSourcesChanged()
    }

    fun search() {
        val q = query.trim()
        if (q.isBlank() || searching) return
        searching = true
        error = null
        scope.launch {
            val found = runCatching { withContext(Dispatchers.IO) { research.search(q, 6) } }
            found.onSuccess { results = it }
                .onFailure { error = it.message ?: "No se pudo buscar en la web" }
            if (found.getOrNull().isNullOrEmpty() && error == null) error = "No se encontraron resultados. Intenta con otras palabras."
            searching = false
        }
    }

    fun save(title: String, url: String, extractedText: String = "", fallback: String = "") {
        if (url in savedUrls || savingUrl != null) return
        savingUrl = url
        error = null
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    extractedText.trim().ifBlank { research.readPage(url) }.ifBlank { fallback }
                }
                require(text.isNotBlank()) { "La página no entregó texto legible para guardar." }
                withContext(Dispatchers.IO) { store.importWeb(scopeKey, title, url, text) }
            }.onSuccess { refreshSaved() }
                .onFailure { error = it.message ?: "No se pudo guardar la página" }
            savingUrl = null
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Buscar en la web", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("DuckDuckGo · las páginas que guardes se convierten en fuentes de esta clase", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Buscar información") },
                placeholder = { Text("Tema, autor, documento…") }
            )
            Button(onClick = ::search, enabled = query.isNotBlank() && !searching) {
                if (searching) CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                else Text("Buscar")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        results.forEach { result ->
            val saved = result.url in savedUrls
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.82f)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(domainOf(result.url), color = NotCanBlue, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (result.snippet.isNotBlank()) Text(result.snippet, color = NotCanGray, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { opened = result }) { Text("Abrir") }
                        TextButton(
                            onClick = { save(result.title, result.url, fallback = result.snippet) },
                            enabled = !saved && savingUrl == null
                        ) {
                            Text(when {
                                saved -> "Guardada"
                                savingUrl == result.url -> "Guardando…"
                                else -> "Guardar en clase"
                            })
                        }
                    }
                }
            }
        }
    }

    opened?.let { result ->
        WebSourceViewer(
            initial = result,
            alreadySaved = result.url in savedUrls,
            onDismiss = { opened = null },
            onSave = { title, url, text -> save(title, url, text, result.snippet) }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebSourceViewer(
    initial: WebResearchService.Result,
    alreadySaved: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, extractedText: String) -> Unit
) {
    BackHandler(onBack = onDismiss)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(initial.title) }
    var currentUrl by remember { mutableStateOf(initial.url) }
    var extractedText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        val view = webView
                        if (view?.canGoBack() == true) view.goBack() else onDismiss()
                    }) { Icon(Icons.Default.ArrowBack, "Volver", tint = NotCanOffWhite) }
                    Column(Modifier.weight(1f)) {
                        Text(title.ifBlank { "Fuente web" }, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(domainOf(currentUrl), color = NotCanGray, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                    TextButton(
                        onClick = { onSave(title.ifBlank { initial.title }, currentUrl, extractedText) },
                        enabled = !alreadySaved && currentUrl.startsWith("http")
                    ) { Text(if (alreadySaved) "Guardada" else "Guardar") }
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }

                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    super.onPageFinished(view, url)
                                    loading = false
                                    currentUrl = url
                                    title = view.title?.takeIf { it.isNotBlank() } ?: title
                                    view.evaluateJavascript("(document.body && document.body.innerText) ? document.body.innerText : ''") { raw ->
                                        extractedText = decodeJavascriptString(raw).take(120_000)
                                    }
                                }
                            }
                            loadUrl(initial.url)
                        }
                    },
                    update = { view -> webView = view }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}

private fun decodeJavascriptString(raw: String): String = runCatching {
    when (val value = JSONTokener(raw).nextValue()) {
        is String -> value
        else -> value?.toString().orEmpty()
    }
}.getOrDefault("").replace(Regex("[\\t ]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()

private fun domainOf(url: String): String = runCatching {
    URI(url).host?.removePrefix("www.") ?: url
}.getOrDefault(url)
