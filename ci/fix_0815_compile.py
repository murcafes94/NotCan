from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt"
s = p.read_text(encoding="utf-8")
old = 'Regex("(?is)<br\\s*/?>")'
# In the generated Kotlin source one backslash reached the string literal; Kotlin needs two.
if old in s:
    s = s.replace(old, 'Regex("(?is)<br\\\\s*/?>")', 1)
else:
    # Accept already-correct source so reruns are idempotent.
    correct = 'Regex("(?is)<br\\\\s*/?>")'
    if correct not in s:
        raise RuntimeError("WriterNoteEditor whitespace regex not found")
p.write_text(s, encoding="utf-8")
print("0.8.15 compile fixes applied")
