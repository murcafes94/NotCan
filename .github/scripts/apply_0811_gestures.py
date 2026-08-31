from pathlib import Path

ROOT = Path('.')

def replace(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# Quiz swipes: left = next only after answered, right = previous.
q = 'app/src/main/java/com/notcan/app/ui/ai/StudyQuizScreen.kt'
replace(q, 'import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectHorizontalDragGestures\n')
replace(q, 'import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.input.pointer.pointerInput\n')
replace(q,
'''    fun next() {\n        typedAnswer = ""\n        revealShortAnswer = false\n        if (position < order.lastIndex) position++ else finished = true\n    }\n''',
'''    fun next() {\n        typedAnswer = ""\n        revealShortAnswer = false\n        if (position < order.lastIndex) position++ else finished = true\n    }\n\n    fun previous() {\n        if (position <= 0) return\n        typedAnswer = ""\n        revealShortAnswer = false\n        position--\n    }\n''')
replace(q,
'''                Column(Modifier.fillMaxSize()) {\n                    Row(''',
'''                var swipeDistance = 0f\n                Column(\n                    Modifier\n                        .fillMaxSize()\n                        .pointerInput(question.id, position, answer) {\n                            detectHorizontalDragGestures(\n                                onDragStart = { swipeDistance = 0f },\n                                onHorizontalDrag = { change, dragAmount ->\n                                    swipeDistance += dragAmount\n                                    change.consume()\n                                },\n                                onDragEnd = {\n                                    when {\n                                        swipeDistance <= -72f && answer != null -> next()\n                                        swipeDistance >= 72f && position > 0 -> previous()\n                                    }\n                                    swipeDistance = 0f\n                                },\n                                onDragCancel = { swipeDistance = 0f }\n                            )\n                        }\n                ) {\n                    Row(''')

# Flashcard swipes: left next, right previous. Buttons remain available.
f = 'app/src/main/java/com/notcan/app/ui/ai/StudyFlashcardsScreen.kt'
replace(f, 'import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectHorizontalDragGestures\n')
replace(f, 'import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.input.pointer.pointerInput\n')
replace(f,
'''                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {\n                    val wide = maxWidth >= 600.dp''',
'''                var swipeDistance = 0f\n                BoxWithConstraints(\n                    Modifier\n                        .weight(1f)\n                        .fillMaxWidth()\n                        .pointerInput(position, order) {\n                            detectHorizontalDragGestures(\n                                onDragStart = { swipeDistance = 0f },\n                                onHorizontalDrag = { change, dragAmount ->\n                                    swipeDistance += dragAmount\n                                    change.consume()\n                                },\n                                onDragEnd = {\n                                    when {\n                                        swipeDistance <= -72f -> next()\n                                        swipeDistance >= 72f -> previous()\n                                    }\n                                    swipeDistance = 0f\n                                },\n                                onDragCancel = { swipeDistance = 0f }\n                            )\n                        }\n                ) {\n                    val wide = maxWidth >= 600.dp''')

print('Applied swipe gestures to quiz and flashcards')
