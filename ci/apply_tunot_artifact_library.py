from pathlib import Path

path = Path('app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt')
text = path.read_text()
old = 'LaunchedEffect(result, artifactScope, autoSaveNextArtifact) {'
new = 'LaunchedEffect(result, artifactScope) {'
if old not in text:
    raise SystemExit('Expected autosave effect not found')
path.write_text(text.replace(old, new, 1))
