from pathlib import Path

p = Path('app/src/main/java/com/notcan/app/settings/NotCanPreferences.kt')
s = p.read_text()
anchor = '        private const val KEY_AI_DETAIL = "ai_detail"\n'
if 'private const val KEY_AI_ENGINE' not in s:
    if anchor not in s:
        raise SystemExit('KEY_AI_DETAIL anchor missing')
    s = s.replace(
        anchor,
        anchor
        + '        private const val KEY_AI_ENGINE = "ai_engine_preference"\n'
        + '        private const val KEY_LAST_LFM_ERROR = "last_lfm_error"\n'
    )
p.write_text(s)
print('Preference constants present.')
