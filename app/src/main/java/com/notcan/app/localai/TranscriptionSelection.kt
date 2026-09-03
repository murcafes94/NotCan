package com.notcan.app.localai

import com.notcan.app.data.local.TranscriptEntity

object TranscriptionSelection {
    fun preferredForAi(items: List<TranscriptEntity>): List<TranscriptEntity> {
        val usable = items.filterNot { transcript ->
            transcript.status.startsWith("PROCESSING", ignoreCase = true) ||
                transcript.status.startsWith("WAITING", ignoreCase = true) ||
                transcript.status.startsWith("FAILED", ignoreCase = true)
        }
        return usable
            .groupBy { it.audioId ?: it.id }
            .values
            .mapNotNull { group ->
                group.filter { it.status.startsWith("FINAL", ignoreCase = true) }
                    .maxByOrNull { it.updatedAtEpochMs }
                    ?: group.maxByOrNull { it.updatedAtEpochMs }
            }
            .sortedBy { it.createdAtEpochMs }
    }
}
