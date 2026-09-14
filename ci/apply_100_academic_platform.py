from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Anchor not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


# Version milestone.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 60\n        versionName = "0.8.36"',
    '        versionCode = 61\n        versionName = "1.0.0"',
)

# Settings UI: academic profile + remote privacy.
settings = "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt"
replace_once(
    settings,
    '    var detail by remember { mutableStateOf(preferences.aiDetail) }\n    var aiEngine by remember { mutableStateOf(preferences.aiEnginePreference) }',
    '    var detail by remember { mutableStateOf(preferences.aiDetail) }\n    var academicProfile by remember { mutableStateOf(preferences.academicProfile) }\n    var protectRemotePii by remember { mutableStateOf(preferences.protectPersonalDataRemote) }\n    var aiEngine by remember { mutableStateOf(preferences.aiEnginePreference) }',
)

profile_card = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Perfil académico", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("TuNot adapta lenguaje, profundidad y criterios según tu etapa de estudio.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Colegio", "Universidad").forEach { option ->
                        FilterChip(
                            selected = academicProfile == option,
                            onClick = { academicProfile = option; preferences.academicProfile = option },
                            label = { Text(option) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Seminario", "Personalizado").forEach { option ->
                        FilterChip(
                            selected = academicProfile == option,
                            onClick = { academicProfile = option; preferences.academicProfile = option },
                            label = { Text(option) }
                        )
                    }
                }
                Text(
                    when (academicProfile) {
                        "Colegio" -> "Explicaciones guiadas, ejemplos y lenguaje accesible."
                        "Seminario" -> "Nivel superior con especialización teológica y filosófica cuando la materia lo requiera."
                        "Personalizado" -> "TuNot se guía principalmente por la materia y tus instrucciones propias."
                        else -> "Rigor universitario, análisis, investigación y metodología académica."
                    },
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

'''
replace_once(
    settings,
    '        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {\n            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {\n                Row(verticalAlignment = Alignment.CenterVertically) {\n                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)\n                    Spacer(Modifier.width(8.dp))\n                    Column {\n                        Text("Motor de TuNot", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)',
    profile_card + '        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {\n            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {\n                Row(verticalAlignment = Alignment.CenterVertically) {\n                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)\n                    Spacer(Modifier.width(8.dp))\n                    Column {\n                        Text("Motor de TuNot", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)',
)

replace_once(
    settings,
    '''        SettingsSwitch(
            title = "Transcribir al terminar",''',
    '''        SettingsSwitch(
            title = "Privacidad al usar IA online",
            subtitle = "Antes de enviar contenido a un proveedor remoto, NotCan oculta correos, teléfonos e identificadores largos detectados con alta confianza.",
            checked = protectRemotePii,
            onCheckedChange = { protectRemotePii = it; preferences.protectPersonalDataRemote = it }
        )
        SettingsSwitch(
            title = "Transcribir al terminar",''',
)

# Service: general academic profile, privacy and citation guard.
service = "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
replace_once(
    service,
    'import com.notcan.app.ai.harness.TuNotEngine\nimport com.notcan.app.ai.harness.TuNotHarness\nimport com.notcan.app.ai.harness.TuNotTool',
    'import com.notcan.app.ai.harness.TuNotCitationGuard\nimport com.notcan.app.ai.harness.TuNotEngine\nimport com.notcan.app.ai.harness.TuNotHarness\nimport com.notcan.app.ai.harness.TuNotPolicy\nimport com.notcan.app.ai.harness.TuNotPrivacyGuard\nimport com.notcan.app.ai.harness.TuNotTool',
)
replace_once(
    service,
    '''            webRequested = webRequested,
            artifactRequest = mapRequest || flashcardRequest || quizRequest
        )''',
    '''            webRequested = webRequested,
            artifactRequest = mapRequest || flashcardRequest || quizRequest,
            academicProfile = preferences.academicProfile,
            subjectName = subjectName
        )''',
)

old_intro = '''            appendLine("CONTEXTO DE NOTCAN")
            appendLine("TuNot es un tutor académico católico orientado principalmente a teología, filosofía, Sagrada Escritura y derecho canónico.")
            appendLine("Su objetivo es ayudar a estudiar con rigor, fidelidad doctrinal y claridad pedagógica; no debe responder como un asistente religioso genérico.")
            appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")
            if (preferences.aiInstructions.isNotBlank()) appendLine("Preferencias del usuario: ${preferences.aiInstructions}")
            appendLine("No muestres cadena de pensamiento, reflexiones internas ni monólogos. Entrega directamente el resultado útil.")
            appendLine("No inventes citas, páginas, autores, fechas, referencias ni afirmaciones ausentes de las fuentes.")
            appendLine("Si el usuario indica que puede haber un error, no inventes una corrección: corrige solo cuando tengas fundamento suficiente.")
            appendLine("Cuando una cuestión sea doctrinal, distingue con precisión entre: enseñanza oficial de la Iglesia, disciplina eclesiástica vigente, opinión teológica e interpretación académica.")
            appendLine("Si existe tensión entre una formulación secundaria y una fuente oficial de la Iglesia, da prioridad a la fuente oficial.")
            appendLine("Para respuestas normales usa Markdown simple y limpio: títulos breves, listas con guion, negrita para conceptos clave y párrafos separados. Evita tablas salvo que sean imprescindibles.")
            appendLine("No abuses de comillas, asteriscos ni encabezados. La respuesta debe verse como apuntes bien editados, no como texto técnico del modelo.")
            appendLine()
            appendLine(TuNotCatholicSourcePolicy.promptPolicy())'''
new_intro = '''            appendLine("CONTEXTO DE NOTCAN 1.0")
            appendLine("TuNot es un tutor académico adaptable para colegio, universidad, seminario y estudio personalizado.")
            appendLine(executionPlan.academicContext.promptBlock())
            appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")
            if (preferences.aiInstructions.isNotBlank()) appendLine("Preferencias del usuario: ${preferences.aiInstructions}")
            appendLine("No muestres cadena de pensamiento, reflexiones internas ni monólogos. Entrega directamente el resultado útil.")
            appendLine("No inventes citas, páginas, autores, fechas, referencias ni afirmaciones ausentes de las fuentes.")
            appendLine("Si el usuario indica que puede haber un error, no inventes una corrección: corrige solo cuando tengas fundamento suficiente.")
            if (TuNotPolicy.CATHOLIC_ACADEMIC in executionPlan.policies) {
                appendLine("Cuando una cuestión sea doctrinal católica, distingue entre enseñanza oficial, disciplina vigente, opinión teológica e interpretación académica.")
                appendLine("Si existe tensión entre una formulación secundaria y una fuente oficial de la Iglesia, da prioridad a la fuente oficial.")
                appendLine(TuNotCatholicSourcePolicy.promptPolicy())
            }
            appendLine("Para respuestas normales usa Markdown simple y limpio: títulos breves, listas con guion, negrita para conceptos clave y párrafos separados. Evita tablas salvo que sean imprescindibles.")
            appendLine("No abuses de comillas, asteriscos ni encabezados. La respuesta debe verse como apuntes bien editados, no como texto técnico del modelo.")'''
replace_once(service, old_intro, new_intro)

replace_once(
    service,
    '''        return try {
            markEngine("Mistral · online", sendToMistral(prompt))
        } catch (_: Throwable) {''',
    '''        return try {
            val remotePrompt = if (preferences.protectPersonalDataRemote) {
                TuNotPrivacyGuard.sanitizeForRemote(prompt)
            } else prompt
            val rawAnswer = sendToMistral(remotePrompt)
            val groundedAnswer = if (wantsWeb) {
                TuNotCitationGuard.enforceRetrievedUrls(rawAnswer, webResults.map { it.url }.toSet())
            } else rawAnswer
            markEngine("Mistral · online", groundedAnswer)
        } catch (_: Throwable) {''',
)

# Gemma: inject profile/domain adaptation without growing every prompt globally.
gemma = "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
replace_once(
    gemma,
    'import com.notcan.app.localai.GemmaLiteRtModelManager',
    'import com.notcan.app.ai.harness.AcademicContextResolver\nimport com.notcan.app.ai.harness.SubjectDomain\nimport com.notcan.app.localai.GemmaLiteRtModelManager',
)
replace_once(
    gemma,
    'systemInstruction = Contents.of(buildAdaptiveSystemInstruction(strictSources, pedagogicalMode, intentQuestion)),',
    'systemInstruction = Contents.of(buildAdaptiveSystemInstruction(strictSources, pedagogicalMode, intentQuestion, subjectName)),',
)
replace_once(
    gemma,
    '''    private fun buildAdaptiveSystemInstruction(
        strictSources: Boolean,
        pedagogicalMode: Boolean,
        question: String
    ): String {
        if (!isSimpleDefinition(question)) return buildSystemInstruction(strictSources, pedagogicalMode)

        return buildString {
            appendLine("Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.")''',
    '''    private fun buildAdaptiveSystemInstruction(
        strictSources: Boolean,
        pedagogicalMode: Boolean,
        question: String,
        subjectName: String?
    ): String {
        val academicContext = AcademicContextResolver.resolve(preferences.academicProfile, subjectName, question)
        if (!isSimpleDefinition(question)) return buildSystemInstruction(strictSources, pedagogicalMode, academicContext)

        return buildString {
            appendLine("Eres TuNot, tutor académico adaptable de NotCan ejecutándose completamente en el dispositivo.")
            appendLine(academicContext.promptBlock())''',
)
replace_once(
    gemma,
    '''            if (isTheologicalPrecisionQuery(question)) {
                appendLine("En teología católica usa terminología patrística, trinitaria y cristológica con precisión.")''',
    '''            if (academicContext.domain == SubjectDomain.THEOLOGY || isTheologicalPrecisionQuery(question)) {
                appendLine("En cuestiones teológicas identifica el marco o tradición cuando sea relevante y usa terminología patrística, trinitaria y cristológica con precisión.")''',
)
replace_once(
    gemma,
    '''    private fun buildSystemInstruction(strictSources: Boolean, pedagogicalMode: Boolean): String = buildString {
        appendLine("Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.")
        appendLine("Responde en español claro, natural, preciso y útil para estudiar.")''',
    '''    private fun buildSystemInstruction(
        strictSources: Boolean,
        pedagogicalMode: Boolean,
        academicContext: com.notcan.app.ai.harness.TuNotAcademicContext
    ): String = buildString {
        appendLine("Eres TuNot, tutor académico adaptable de NotCan ejecutándose completamente en el dispositivo.")
        appendLine(academicContext.promptBlock())
        appendLine("Responde en español claro, natural, preciso y útil para estudiar.")''',
)
old_theology = '''        appendLine("En teología católica distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica.")
        appendLine("En terminología patrística, trinitaria y cristológica conserva con rigor las distinciones entre naturaleza/esencia (ousia, physis), hipóstasis/persona y prosopon; no identifiques sin más hipóstasis o persona con esencia o naturaleza. Si una equivalencia es discutida o depende del autor/época, indícalo con prudencia.")
        appendLine("En teología trinitaria católica no describas al Padre, al Hijo y al Espíritu Santo como tres modos, manifestaciones o formas en que se presenta una sola persona. Formula con precisión: una única esencia o naturaleza divina (ousia) y tres Personas o hipóstasis realmente distintas y consustanciales; la distinción personal no divide la esencia divina.")
        appendLine("Cuando expliques hipóstasis, distingue sus usos filosófico/patrístico, trinitario y cristológico. En cristología, Jesucristo es una sola Persona o hipóstasis, la del Verbo, en dos naturalezas, divina y humana, sin confusión ni división.")'''
new_theology = '''        if (academicContext.domain == SubjectDomain.THEOLOGY) {
            appendLine("En teología distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica cuando corresponda al marco de la pregunta.")
            appendLine("En terminología patrística, trinitaria y cristológica conserva las distinciones entre naturaleza/esencia (ousia, physis), hipóstasis/persona y prosopon; no identifiques sin más hipóstasis con esencia o naturaleza.")
            appendLine("En teología trinitaria católica no describas al Padre, al Hijo y al Espíritu Santo como tres modos o manifestaciones de una sola persona: una única esencia divina y tres Personas o hipóstasis realmente distintas y consustanciales.")
            appendLine("Cuando expliques hipóstasis, distingue sus usos filosófico/patrístico, trinitario y cristológico. En cristología católica, Jesucristo es una sola Persona o hipóstasis, la del Verbo, en dos naturalezas, divina y humana, sin confusión ni división.")
        }'''
replace_once(gemma, old_theology, new_theology)

print("NotCan 1.0 academic-platform patch applied")
