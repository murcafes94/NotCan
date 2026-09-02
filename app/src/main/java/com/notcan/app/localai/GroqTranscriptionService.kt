package com.notcan.app.localai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.notcan.app.ai.GroqCredentialsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.roundToLong

class GroqApiException(
    val statusCode: Int,
    message: String,
    val retryAfterSeconds: Long? = null
) : RuntimeException(message)

/**
 * Online final transcription through Groq's OpenAI-compatible speech endpoint.
 * The transcript is deliberately literal: academic vocabulary is supplied as recognition
 * context, never as a post-hoc semantic rewrite.
 */
class GroqTranscriptionService(private val context: Context) {
    private val credentials = GroqCredentialsStore(context)

    suspend fun transcribeM4aDetailed(
        audio: File,
        terms: List<AcademicTranscriptionTerm> = emptyList(),
        subjectName: String? = null,
        classTitle: String? = null
    ): WhisperTranscriptionResult = withContext(Dispatchers.IO) {
        require(audio.exists() && audio.length() > 0L) { "El audio local ya no existe." }
        val apiKey = credentials.apiKey()
        require(apiKey.isNotBlank()) { "Configura una API key de Groq en Ajustes." }

        val chunks = splitForUpload(audio)
        try {
            val mergedSegments = mutableListOf<WhisperSegmentResult>()
            val mergedText = mutableListOf<String>()
            var previousTail = ""

            chunks.forEach { chunk ->
                val prompt = buildPrompt(subjectName, classTitle, terms, previousTail)
                val result = transcribeChunk(apiKey, chunk.file, prompt, chunk.durationMs)
                val text = result.text.trim()
                if (text.isNotBlank()) {
                    mergedText += text
                    previousTail = text.takeLast(420)
                }
                result.segments.forEach { segment ->
                    mergedSegments += segment.copy(
                        startMs = segment.startMs + chunk.startMs,
                        endMs = segment.endMs + chunk.startMs
                    )
                }
            }

            val text = mergedText.joinToString("\n").trim().ifBlank {
                "No se detectó voz suficiente para generar una transcripción."
            }
            WhisperTranscriptionResult(
                text = text,
                segments = mergedSegments.sortedBy { it.startMs }
            )
        } finally {
            chunks.filter { it.temporary }.forEach { runCatching { it.file.delete() } }
        }
    }

    private fun transcribeChunk(
        apiKey: String,
        file: File,
        prompt: String,
        durationMs: Long
    ): WhisperTranscriptionResult {
        val boundary = "----NotCanGroq${UUID.randomUUID()}"
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.useCaches = false
            connection.connectTimeout = 30_000
            connection.readTimeout = 10 * 60_000
            connection.setChunkedStreamingMode(64 * 1024)
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            DataOutputStream(BufferedOutputStream(connection.outputStream)).use { out ->
                writeField(out, boundary, "model", MODEL)
                writeField(out, boundary, "language", "es")
                writeField(out, boundary, "response_format", "verbose_json")
                writeField(out, boundary, "temperature", "0")
                writeField(out, boundary, "timestamp_granularities[]", "segment")
                if (prompt.isNotBlank()) writeField(out, boundary, "prompt", prompt)

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${safeFileName(file.name)}\"\r\n")
                out.writeBytes("Content-Type: audio/mp4\r\n\r\n")
                file.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "Groq respondió con HTTP $code" }
                throw GroqApiException(
                    statusCode = code,
                    message = message,
                    retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                )
            }
            return parseVerboseJson(body, durationMs)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVerboseJson(body: String, fallbackDurationMs: Long): WhisperTranscriptionResult {
        val json = JSONObject(body)
        val text = json.optString("text").trim()
        val array = json.optJSONArray("segments")
        val segments = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val segmentText = item.optString("text").trim()
                    if (segmentText.isBlank()) continue
                    val startMs = (item.optDouble("start", 0.0) * 1_000.0).roundToLong().coerceAtLeast(0L)
                    val endMs = (item.optDouble("end", startMs / 1_000.0) * 1_000.0).roundToLong().coerceAtLeast(startMs)
                    add(WhisperSegmentResult(startMs, endMs, segmentText))
                }
            }
        }.ifEmpty {
            if (text.isBlank()) emptyList()
            else listOf(WhisperSegmentResult(0L, fallbackDurationMs.coerceAtLeast(0L), text))
        }
        return WhisperTranscriptionResult(text = text, segments = segments)
    }

    private fun buildPrompt(
        subjectName: String?,
        classTitle: String?,
        terms: List<AcademicTranscriptionTerm>,
        previousTail: String
    ): String = buildString {
        append("Transcribe literalmente en español. Conserva frases inusuales tal como se oyen; no las reformules ni las hagas más lógicas. ")
        subjectName?.takeIf { it.isNotBlank() }?.let { append("Materia: $it. ") }
        classTitle?.takeIf { it.isNotBlank() }?.let { append("Clase: $it. ") }
        val vocabulary = terms
            .sortedByDescending { it.weight }
            .map { it.value }
            .distinctBy { it.lowercase() }
            .take(60)
            .joinToString(", ")
        if (vocabulary.isNotBlank()) append("Términos que pueden aparecer: $vocabulary. ")
        if (previousTail.isNotBlank()) append("Final del segmento anterior, solo como contexto: ${previousTail.takeLast(420)}")
    }.take(MAX_PROMPT_CHARS)

    private fun splitForUpload(source: File): List<AudioChunk> {
        val durationMs = mediaDurationMs(source)
        if (source.length() <= SAFE_FREE_TIER_BYTES) {
            return listOf(AudioChunk(source, 0L, durationMs, temporary = false))
        }

        require(durationMs > 0L) { "No se pudo determinar la duración para dividir el audio." }
        val outputDir = File(context.cacheDir, "groq_chunks").apply { mkdirs() }
        val chunks = mutableListOf<AudioChunk>()
        var startMs = 0L
        while (startMs < durationMs) {
            val endMs = minOf(startMs + CHUNK_DURATION_MS, durationMs)
            val output = File(outputDir, "${source.nameWithoutExtension}_${startMs}_${System.nanoTime()}.m4a")
            val chunk = extractM4aRange(source, output, startMs, endMs)
            require(chunk.file.length() <= SAFE_FREE_TIER_BYTES) {
                "Un fragmento de audio supera el límite seguro de subida de Groq."
            }
            chunks += chunk
            startMs = endMs
        }
        require(chunks.isNotEmpty()) { "No se pudo dividir el audio para Groq." }
        return chunks
    }

    private fun extractM4aRange(
        source: File,
        output: File,
        requestedStartMs: Long,
        requestedEndMs: Long
    ): AudioChunk {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.absolutePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = index
                    format = candidate
                    break
                }
            }
            require(audioTrack >= 0) { "No se encontró una pista de audio válida." }
            val audioFormat = requireNotNull(format) { "No se encontró una pista de audio válida." }
            extractor.selectTrack(audioTrack)

            val startUs = requestedStartMs * 1_000L
            val endUs = requestedEndMs * 1_000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (extractor.sampleTime >= 0L && extractor.sampleTime < startUs) {
                if (!extractor.advance()) break
            }
            val firstSampleUs = extractor.sampleTime
            require(firstSampleUs >= 0L) { "No se encontraron muestras de audio en el fragmento." }

            output.parentFile?.mkdirs()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val targetTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxInput = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 256 * 1024
            val buffer = ByteBuffer.allocate(maxOf(256 * 1024, maxInput))
            val info = MediaCodec.BufferInfo()
            var lastSampleUs = firstSampleUs
            var wroteAny = false

            while (true) {
                val sampleUs = extractor.sampleTime
                if (sampleUs < 0L || sampleUs >= endUs) break
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = (sampleUs - firstSampleUs).coerceAtLeast(0L)
                info.flags = extractor.sampleFlags
                buffer.position(0)
                buffer.limit(size)
                muxer.writeSampleData(targetTrack, buffer, info)
                wroteAny = true
                lastSampleUs = sampleUs
                if (!extractor.advance()) break
            }
            require(wroteAny) { "El fragmento de audio quedó vacío." }
            val durationMs = ((lastSampleUs - firstSampleUs) / 1_000L).coerceAtLeast(1L)
            return AudioChunk(output, firstSampleUs / 1_000L, durationMs, temporary = true)
        } catch (t: Throwable) {
            runCatching { output.delete() }
            throw t
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun mediaDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeField(out: DataOutputStream, boundary: String, name: String, value: String) {
        out.writeBytes("--$boundary\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        out.write(value.toByteArray(Charsets.UTF_8))
        out.writeBytes("\r\n")
    }

    private fun safeFileName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private data class AudioChunk(
        val file: File,
        val startMs: Long,
        val durationMs: Long,
        val temporary: Boolean
    )

    companion object {
        const val DISPLAY_NAME = "Groq · Whisper Large V3"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL = "whisper-large-v3"
        private const val SAFE_FREE_TIER_BYTES = 23L * 1024L * 1024L
        private const val CHUNK_DURATION_MS = 20L * 60L * 1_000L
        private const val MAX_PROMPT_CHARS = 1_800
    }
}
