from pathlib import Path

# 1) Force llama.cpp Android bridge to use the model's Jinja chat template.
p = Path('llama-android/src/main/cpp/CMakeLists.txt')
s = p.read_text()
needle = 'string(REPLACE "constexpr int   BATCH_SIZE              = 512;" "constexpr int   BATCH_SIZE              = 256;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")\n'
insert = needle + 'string(REPLACE "/* use_jinja */ false" "/* use_jinja */ true" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")\n' + 'string(REPLACE "constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;" "constexpr float DEFAULT_SAMPLER_TEMP    = 0.1f;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")\n'
if '/* use_jinja */ true' not in s:
    if needle not in s:
        raise SystemExit('CMake patch marker not found')
    s = s.replace(needle, insert)
p.write_text(s)

# 2) Never expose model-control tokens to the user. If the model emits only
# control tokens, throw so NotCanAiService falls back to the safe extractive engine.
p = Path('app/src/main/java/com/notcan/app/ai/LocalLfmTuNotEngine.kt')
s = p.read_text()
old = '''        output.toString().trim().ifBlank { error("LFM2.5 no produjo una respuesta") }
    }
'''
new = '''        sanitizeModelOutput(output.toString())
            .ifBlank { error("LFM2.5 produjo únicamente tokens de control") }
    }

    private fun sanitizeModelOutput(raw: String): String {
        return raw
            .replace(SPECIAL_TOKEN_REGEX, "")
            .replace("</pad/>", "")
            .replace("</pad>", "")
            .replace("<pad>", "")
            .trim()
    }
'''
if old not in s:
    raise SystemExit('Local engine output marker not found')
s = s.replace(old, new)
const_marker = '        private const val GENERATION_TIMEOUT_MS = 120_000L\n'
const_insert = const_marker + '        private val SPECIAL_TOKEN_REGEX = Regex("""<\\|[^>]+\\|>""")\n'
if 'SPECIAL_TOKEN_REGEX' not in s.split('companion object', 1)[-1]:
    if const_marker not in s:
        raise SystemExit('Local engine companion marker not found')
    s = s.replace(const_marker, const_insert)
p.write_text(s)

# 3) Bump installable build so Android treats this as an update.
p = Path('app/build.gradle.kts')
s = p.read_text()
s = s.replace('versionCode = 39', 'versionCode = 40')
s = s.replace('versionName = "0.8.18"', 'versionName = "0.8.18.1"')
p.write_text(s)

print('LFM2.5 chat-template and control-token fix applied.')
