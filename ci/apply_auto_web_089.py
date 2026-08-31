from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

# Version bump
build = ROOT / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
text = text.replace('versionCode = 25', 'versionCode = 26')
text = text.replace('versionName = "0.8.8"', 'versionName = "0.8.9"')
build.write_text(text, encoding="utf-8")

# Auto mode: use web by default unless the user is clearly asking to stay inside local class material.
web = ROOT / "app/src/main/java/com/notcan/app/ai/WebResearchService.kt"
text = web.read_text(encoding="utf-8")
old = '''        fun shouldAutoSearch(question: String): Boolean {
            val q = question.lowercase()
            return listOf(
                "busca", "buscar", "web", "internet", "en línea", "online", "fuente reciente",
                "hoy", "actualmente", "actual", "reciente", "última", "último", "noticia", "vigente",
                "quién es ahora", "qué pasó", "este año", "2026"
            ).any { it in q }
        }
'''
new = '''        fun shouldAutoSearch(question: String): Boolean {
            val q = question.lowercase().trim()
            if (q.length < 3) return false

            // In Auto, the web is the default research layer. We only stay local when the wording
            // clearly asks TuNot to work from material already stored in the current class.
            val localOnlyHints = listOf(
                "mis apuntes", "mis fuentes", "esta transcripción", "la transcripción",
                "según mis apuntes", "según la transcripción", "según el profesor",
                "material de esta clase", "material de clase", "este documento",
                "este pdf", "este epub", "este archivo", "lo que grabé", "lo que anoté",
                "resume mis", "resume esta clase", "resume el documento"
            )
            if (localOnlyHints.any { it in q }) return false

            return true
        }
'''
if old not in text:
    raise SystemExit("No se encontró shouldAutoSearch esperado")
text = text.replace(old, new)
web.write_text(text, encoding="utf-8")

# Compact source/search-mode selector: one dropdown instead of three chips side-by-side.
screen = ROOT / "app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt"
text = screen.read_text(encoding="utf-8")
if 'import androidx.compose.material.icons.filled.KeyboardArrowDown\n' not in text:
    text = text.replace('import androidx.compose.material.icons.filled.GraphicEq\n', 'import androidx.compose.material.icons.filled.GraphicEq\nimport androidx.compose.material.icons.filled.KeyboardArrowDown\n')
if 'import androidx.compose.material3.DropdownMenu\n' not in text:
    text = text.replace('import androidx.compose.material3.Divider\n', 'import androidx.compose.material3.Divider\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem\n')

pattern = re.compile(r'''@Composable\nprivate fun CompactAiTools\(\n    sourceMode: Int,\n    socraticMode: Boolean,\n    hasMessages: Boolean,\n    onSourceModeChange: \(Int\) -> Unit,\n    onSocraticChange: \(Boolean\) -> Unit,\n    onClear: \(\) -> Unit,\n    modifier: Modifier = Modifier\n\) \{.*?\n\}\n\n@Composable\nprivate fun CompactChatHeader''', re.S)
replacement = '''@Composable
private fun CompactAiTools(
    sourceMode: Int,
    socraticMode: Boolean,
    hasMessages: Boolean,
    onSourceModeChange: (Int) -> Unit,
    onSocraticChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    val modeName = when (sourceMode) {
        0 -> "Mis fuentes"
        2 -> "Web"
        else -> "Automático"
    }
    val modeDescription = when (sourceMode) {
        0 -> "Solo apuntes, transcripciones, documentos y webs guardadas."
        2 -> "Siempre investiga en la web antes de responder."
        else -> "Investiga en la web automáticamente, salvo que pidas trabajar con tus fuentes."
    }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modo de búsqueda", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { sourceMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(modeName, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Elegir modo")
                }
                DropdownMenu(
                    expanded = sourceMenuExpanded,
                    onDismissRequest = { sourceMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Automático", fontWeight = FontWeight.SemiBold)
                                Text("Web cuando haga falta", style = MaterialTheme.typography.bodySmall, color = NotCanGray)
                            }
                        },
                        onClick = {
                            onSourceModeChange(1)
                            sourceMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Mis fuentes", fontWeight = FontWeight.SemiBold)
                                Text("Solo material guardado", style = MaterialTheme.typography.bodySmall, color = NotCanGray)
                            }
                        },
                        onClick = {
                            onSourceModeChange(0)
                            sourceMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Web", fontWeight = FontWeight.SemiBold)
                                Text("Buscar siempre en Internet", style = MaterialTheme.typography.bodySmall, color = NotCanGray)
                            }
                        },
                        onClick = {
                            onSourceModeChange(2)
                            sourceMenuExpanded = false
                        }
                    )
                }
            }
            Text(modeDescription, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            FilterChip(selected = socraticMode, onClick = { onSocraticChange(!socraticMode) }, label = { Text("Socrático") }, leadingIcon = { Icon(Icons.Default.Quiz, null) })
            if (hasMessages) TextButton(onClick = onClear) { Text("Nueva conversación") }
        }
    }
}

@Composable
private fun CompactChatHeader'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"No se pudo reemplazar CompactAiTools: {count}")
screen.write_text(text, encoding="utf-8")

print("NotCan 0.8.9 patch aplicado")
