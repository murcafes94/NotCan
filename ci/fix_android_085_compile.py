from pathlib import Path
p = Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapScreen.kt')
s = p.read_text()
old = '''            val textDemand = remember(visibleMap) {\n                visibleMap.nodes.sumOf { node ->\n                    val chars = node.title.length + (node.description?.length ?: 0)\n                    108f + (chars / 30f) * 14f\n                }\n            }\n'''
new = '''            val textDemand = remember(visibleMap) {\n                visibleMap.nodes.fold(0f) { total, node ->\n                    val chars = node.title.length + (node.description?.length ?: 0)\n                    total + 108f + (chars / 30f) * 14f\n                }\n            }\n'''
if old not in s:
    raise SystemExit('textDemand block not found')
p.write_text(s.replace(old, new, 1))
print('compile fix applied')
