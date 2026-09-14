package com.notcan.app.ai.harness

import java.text.Normalizer
import java.util.Locale

enum class AcademicProfile(val storedValue: String, val label: String) {
    SCHOOL("Colegio", "Colegio"),
    UNIVERSITY("Universidad", "Universidad"),
    SEMINARY("Seminario", "Seminario"),
    CUSTOM("Personalizado", "Personalizado");

    companion object {
        fun fromStored(value: String?): AcademicProfile = entries.firstOrNull {
            it.storedValue.equals(value, ignoreCase = true)
        } ?: UNIVERSITY
    }
}

enum class SubjectDomain(val label: String) {
    GENERAL("General"),
    STEM("Ciencias y tecnología"),
    HEALTH("Salud y ciencias de la vida"),
    LAW("Derecho y ciencias sociales"),
    HUMANITIES("Humanidades"),
    LANGUAGES("Idiomas y lingüística"),
    THEOLOGY("Teología y estudios religiosos")
}

data class TuNotAcademicContext(
    val profile: AcademicProfile,
    val domain: SubjectDomain,
    val profileInstruction: String,
    val domainInstruction: String
) {
    fun promptBlock(): String = buildString {
        appendLine("Perfil académico: ${profile.label}.")
        appendLine(profileInstruction)
        appendLine("Área detectada: ${domain.label}.")
        append(domainInstruction)
    }.trim()
}

object AcademicContextResolver {
    fun resolve(profileValue: String?, subjectName: String?, question: String): TuNotAcademicContext {
        val profile = AcademicProfile.fromStored(profileValue)
        val domain = detectDomain(subjectName, question)
        return TuNotAcademicContext(
            profile = profile,
            domain = domain,
            profileInstruction = profileInstruction(profile),
            domainInstruction = domainInstruction(domain, profile)
        )
    }

    fun detectDomain(subjectName: String?, question: String): SubjectDomain {
        val text = normalize(listOfNotNull(subjectName, question).joinToString(" "))
        fun hasAny(vararg terms: String): Boolean = terms.any(text::contains)

        return when {
            hasAny(
                "teolog", "trinidad", "cristolog", "patrolog", "biblia", "escritura", "evangelio",
                "liturg", "magister", "catecismo", "sacramento", "eclesiolog", "dogma", "canonico",
                "hipostasis", "ousia", "encarnacion", "seminario"
            ) -> SubjectDomain.THEOLOGY
            hasAny(
                "medicin", "anatom", "fisiolog", "enfermer", "farmac", "bioetic", "salud", "clinica",
                "biologia", "genetic", "microbiolog", "nutricion"
            ) -> SubjectDomain.HEALTH
            hasAny(
                "derecho", "jurid", "ley", "constitucion", "codigo civil", "codigo penal", "jurisprud",
                "politica", "sociolog", "economia", "administracion"
            ) -> SubjectDomain.LAW
            hasAny(
                "matemat", "algebra", "geometr", "calculo", "fisica", "quimica", "estadistic", "program",
                "informat", "ingenier", "algorit", "comput", "electron", "tecnolog"
            ) -> SubjectDomain.STEM
            hasAny(
                "ingles", "english", "frances", "latin", "griego", "idioma", "linguist", "gramatica",
                "fonetic", "traduccion", "literatura"
            ) -> SubjectDomain.LANGUAGES
            hasAny(
                "filosof", "historia", "antropolog", "psicolog", "etica", "arte", "humanidad", "pedagog",
                "comunicacion", "cultura"
            ) -> SubjectDomain.HUMANITIES
            else -> SubjectDomain.GENERAL
        }
    }

    private fun profileInstruction(profile: AcademicProfile): String = when (profile) {
        AcademicProfile.SCHOOL ->
            "Explica con lenguaje accesible, pasos claros y ejemplos concretos. Comprueba conceptos previos y evita tecnicismos innecesarios; cuando sean necesarios, defínelos."
        AcademicProfile.UNIVERSITY ->
            "Trabaja con nivel universitario: precisión conceptual, análisis, relaciones entre ideas, metodología académica y distinción clara entre hechos, interpretación y opinión."
        AcademicProfile.SEMINARY ->
            "Trabaja con nivel superior de seminario: rigor filosófico y teológico, precisión terminológica, lectura de fuentes y distinción entre doctrina, disciplina, opinión teológica e interpretación académica cuando corresponda."
        AcademicProfile.CUSTOM ->
            "Adapta profundidad, lenguaje y método al contenido, a la materia y a las instrucciones específicas del estudiante, sin asumir un nivel académico fijo."
    }

    private fun domainInstruction(domain: SubjectDomain, profile: AcademicProfile): String = when (domain) {
        SubjectDomain.STEM ->
            "Prioriza definiciones operativas, procedimiento, unidades, supuestos y comprobación del resultado. En problemas, muestra pasos útiles sin inventar datos."
        SubjectDomain.HEALTH ->
            "Distingue conocimiento académico de orientación clínica personal. Explica mecanismos, evidencia y terminología con precisión; no conviertas una explicación de estudio en diagnóstico individual."
        SubjectDomain.LAW ->
            "Distingue norma, doctrina, jurisprudencia e interpretación. No inventes artículos ni vigencia normativa y señala la jurisdicción cuando sea relevante."
        SubjectDomain.HUMANITIES ->
            "Sitúa conceptos en contexto, compara autores o corrientes cuando aporte valor y separa exposición, argumento e interpretación."
        SubjectDomain.LANGUAGES ->
            "Usa ejemplos breves, explica forma, significado y uso, y conserva grafías, transliteraciones y distinciones terminológicas relevantes."
        SubjectDomain.THEOLOGY -> if (profile == AcademicProfile.SEMINARY) {
            "En cuestiones católicas conserva precisión doctrinal y terminológica. Da prioridad a fuentes oficiales cuando existan; en Trinidad distingue una única ousia o naturaleza divina y tres hipóstasis o Personas realmente distintas, y evita formulaciones modalistas."
        } else {
            "Explica la terminología religiosa y teológica con precisión, indicando tradición, autor o marco confesional cuando sea relevante. Si la pregunta es específicamente católica, distingue fuentes oficiales de opiniones teológicas."
        }
        SubjectDomain.GENERAL ->
            "Responde de forma académica y proporcionada: define primero lo central, añade contexto solo cuando ayude y evita convertir preguntas simples en ensayos."
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
