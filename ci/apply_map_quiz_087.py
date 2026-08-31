from pathlib import Path

map_path = Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapScreen.kt')
text = map_path.read_text()
old = '''            val virtualWidthPx = maxOf(
                widthPx,
                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES, StudyMapLayoutStyle.TREE -> 1750f
                    else -> 1450f
                }
            )
            val virtualHeightPx = maxOf(heightPx, textDemand * 0.72f, visibleMap.nodes.size * 145f)
            val virtualWidthDp = with(density) { virtualWidthPx.toDp() }
            val virtualHeightDp = with(density) { virtualHeightPx.toDp() }
            val nodes = remember(visibleMap, layoutStyle, virtualWidthPx, virtualHeightPx) {
                StudyMapLayoutEngine.layout(visibleMap, layoutStyle, virtualWidthPx, virtualHeightPx)
            }
'''
new = '''            // El motor de mapas usa unidades visuales equivalentes a dp. Antes los valores
            // de ancho/alto se trataban como px físicos y en pantallas densas los nodos quedaban
            // demasiado estrechos. Escalamos a px solo en la fase final de dibujo.
            val densityScale = density.density.coerceAtLeast(1f)
            val virtualWidthDpValue = maxOf(
                maxWidth.value,
                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES, StudyMapLayoutStyle.TREE -> 1380f
                    else -> 1120f
                }
            )
            val virtualHeightDpValue = maxOf(
                maxHeight.value,
                textDemand * 0.72f,
                visibleMap.nodes.size * 132f
            )
            val virtualWidthDp = virtualWidthDpValue.dp
            val virtualHeightDp = virtualHeightDpValue.dp
            val virtualWidthPx = with(density) { virtualWidthDp.toPx() }
            val virtualHeightPx = with(density) { virtualHeightDp.toPx() }
            val nodes = remember(visibleMap, layoutStyle, virtualWidthDpValue, virtualHeightDpValue, densityScale) {
                StudyMapLayoutEngine.layout(visibleMap, layoutStyle, virtualWidthDpValue, virtualHeightDpValue)
                    .map { positioned ->
                        positioned.copy(
                            x = positioned.x * densityScale,
                            y = positioned.y * densityScale,
                            width = positioned.width * densityScale,
                            height = positioned.height * densityScale
                        )
                    }
            }
'''
if old not in text:
    raise SystemExit('map virtual-canvas block not found')
text = text.replace(old, new, 1)

old_fit = '''                val fitted = minOf(
                    ((widthPx - padding * 2f) / contentWidth),
                    ((heightPx - padding * 2f) / contentHeight)
                ).coerceIn(0.72f, 1.45f)
                zoom = fitted
                panX = (widthPx - contentWidth * fitted) / 2f - minX * fitted
                panY = (heightPx - contentHeight * fitted) / 2f - minY * fitted
'''
new_fit = '''                val fitted = minOf(
                    ((widthPx - padding * 2f) / contentWidth),
                    ((heightPx - padding * 2f) / contentHeight)
                ).coerceIn(0.62f, 1.35f)
                // Al abrir priorizamos lectura a tamaño real. Ajustar puede reducir explícitamente.
                val targetZoom = if (fitRequest == 0) 1f else fitted
                zoom = targetZoom
                panX = (widthPx - contentWidth * targetZoom) / 2f - minX * targetZoom
                panY = (heightPx - contentHeight * targetZoom) / 2f - minY * targetZoom
'''
if old_fit not in text:
    raise SystemExit('map fit block not found')
text = text.replace(old_fit, new_fit, 1)
map_path.write_text(text)

quiz_path = Path('app/src/main/java/com/notcan/app/ui/ai/StudyQuizScreen.kt')
quiz = quiz_path.read_text()
if 'decorFitsSystemWindows = false' not in quiz:
    raise SystemExit('quiz dialog property not found')
quiz = quiz.replace('decorFitsSystemWindows = false', 'decorFitsSystemWindows = true', 1)

old_button = '''                    if (answer != null) {
                        Button(
                            onClick = ::next,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                        ) {
                            Text(if (position == order.lastIndex) "Ver resultado" else "Siguiente")
                        }
                    }
'''
new_button = '''                    if (answer != null) {
                        // Reserva el inset del sistema fuera del botón para que la navegación
                        // del cuestionario quede siempre sobre Atrás / Inicio / Recientes.
                        Surface(
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                            color = MaterialTheme.colorScheme.background,
                            tonalElevation = 4.dp
                        ) {
                            Button(
                                onClick = ::next,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)
                            ) {
                                Text(if (position == order.lastIndex) "Ver resultado" else "Siguiente")
                            }
                        }
                    }
'''
if old_button not in quiz:
    raise SystemExit('quiz bottom button block not found')
quiz = quiz.replace(old_button, new_button, 1)
quiz_path.write_text(quiz)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text()
if 'versionCode = 23' not in g or 'versionName = "0.8.6"' not in g:
    raise SystemExit('expected 0.8.6 version block not found')
g = g.replace('versionCode = 23', 'versionCode = 24', 1)
g = g.replace('versionName = "0.8.6"', 'versionName = "0.8.7"', 1)
gradle.write_text(g)
