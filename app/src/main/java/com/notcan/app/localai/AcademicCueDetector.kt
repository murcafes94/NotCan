package com.notcan.app.localai

import com.notcan.app.data.local.DetectedCueEntity
import java.util.Locale
import java.util.UUID

object AcademicCueDetector {
    private data class Rule(val label: String, val keywords: List<String>)

    private val rules = listOf(
        Rule("📝 Tarea", listOf("tarea", "tareas", "deber", "deberes", "trabajo", "trabajos", "entrega", "entregar")),
        Rule("📚 Examen", listOf("examen", "prueba", "evaluación", "evaluacion", "parcial", "supletorio")),
        Rule("‼ Importantísimo", listOf("importantísimo", "importantisimo", "fundamental", "indispensable", "esto es clave", "muy importante")),
        Rule("👀 Ojazos", listOf("mucho ojo", "no olviden", "no se olviden", "tengan muy presente")),
        Rule("👁 Ojo", listOf("ojo", "atención", "atencion", "recuerden", "tengan presente")),
        Rule("⭐ Importante", listOf("importante", "relevante", "clave"))
    )

    fun detect(
        text: String,
        classSessionId: String,
        transcriptId: String?,
        audioId: String?
    ): List<DetectedCueEntity> {
        if (text.isBlank()) return emptyList()
        val pieces = text
            .replace("\r", "")
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 4 }

        val seen = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        val result = mutableListOf<DetectedCueEntity>()

        for (piece in pieces) {
            val normalized = piece.lowercase(Locale.getDefault())
            for (rule in rules) {
                val keyword = rule.keywords.firstOrNull { normalized.contains(it) } ?: continue
                val key = "${rule.label}|${piece.take(160)}"
                if (!seen.add(key)) continue
                result += DetectedCueEntity(
                    id = UUID.randomUUID().toString(),
                    classSessionId = classSessionId,
                    transcriptId = transcriptId,
                    audioId = audioId,
                    label = rule.label,
                    keyword = keyword,
                    excerpt = piece.take(420),
                    createdAtEpochMs = now + result.size
                )
                if (result.size >= 40) return result
                break
            }
        }
        return result
    }
}
