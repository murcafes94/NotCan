from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt"
text = path.read_text(encoding="utf-8")
constant = 'internal const val NEW_CLASS_RECORDING_SENTINEL = "__NOTCAN_NEW_CLASS__"'
if constant not in text:
    text = text.rstrip() + "\n\n" + constant + "\n"
    path.write_text(text, encoding="utf-8")
print("recording sentinel preserved in V5")
