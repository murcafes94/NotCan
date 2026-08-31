from pathlib import Path
import re

root = Path('.')

# --- StudyMapLayoutEngine: content-aware sizing + collision-free layouts ---
p = root / 'app/src/main/java/com/notcan/app/ui/maps/StudyMapLayoutEngine.kt'
s = p.read_text()
s = s.replace('import kotlin.math.cos\nimport kotlin.math.max\nimport kotlin.math.sin', 'import kotlin.math.abs\nimport kotlin.math.ceil\nimport kotlin.math.cos\nimport kotlin.math.max\nimport kotlin.math.sin')

s = re.sub(
    r'    private fun estimatedNodeHeight\(node: StudyMapNode, base: Float\): Float \{.*?\n    \}',
    '''    private fun estimatedNodeHeight(node: StudyMapNode, base: Float, width: Float = 210f): Float {
        // Estimate the real rendered height from the available card width. Compose may grow a card
        // beyond heightIn(), so the layout engine must reserve that space before positioning siblings.
        val usableWidth = (width - 30f).coerceAtLeast(120f)
        val titleCharsPerLine = (usableWidth / 10.5f).toInt().coerceIn(13, 28)
        val bodyCharsPerLine = (usableWidth / 8.2f).toInt().coerceIn(16, 34)
        val titleLines = ceil(node.title.length.coerceAtLeast(1) / titleCharsPerLine.toFloat()).toInt().coerceAtLeast(1)
        val descriptionLines = node.description?.takeIf { it.isNotBlank() }
            ?.let { ceil(it.length / bodyCharsPerLine.toFloat()).toInt().coerceAtLeast(1) } ?: 0
        val sourceLines = if (node.sourceRefs.isNotEmpty()) 1 else 0
        val measured = 28f + titleLines * 27f + descriptionLines * 22f + sourceLines * 23f
        return max(base, measured + 14f)
    }''',
    s,
    flags=re.S,
    count=1,
)

radial = '''    private fun radial(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val levels = collectLevels(map.rootNodeId, children)
        val result = mutableListOf<PositionedStudyMapNode>()
        val centerX = width / 2f
        val centerY = height / 2f
        var previousRadius = 0f

        levels.toSortedMap().forEach { (depth, ids) ->
            if (depth == 0) {
                val root = byId[ids.firstOrNull()] ?: return@forEach
                val rootWidth = 250f
                val rootHeight = estimatedNodeHeight(root, 112f, rootWidth)
                result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)
            } else {
                val cardWidth = if (depth == 1) 230f else 205f
                val cardGap = if (depth == 1) 74f else 62f
                val countRadius = (ids.size * (cardWidth + cardGap) / (2f * PI.toFloat())).coerceAtLeast(0f)
                val depthRadius = if (depth == 1) 390f else previousRadius + 330f
                val radius = max(depthRadius, countRadius + 70f)
                previousRadius = radius
                ids.forEachIndexed { index, id ->
                    val node = byId[id] ?: return@forEachIndexed
                    val angle = (2.0 * PI / max(ids.size, 1)) * index - PI / 2.0
                    val x = centerX + radius * cos(angle).toFloat()
                    val y = centerY + radius * sin(angle).toFloat()
                    val cardHeight = estimatedNodeHeight(node, if (depth == 1) 100f else 88f, cardWidth)
                    result += PositionedStudyMapNode(node, x - cardWidth / 2f, y - cardHeight / 2f, cardWidth, cardHeight)
                }
            }
        }
        return resolveOverlaps(result, map.rootNodeId, width, height, minGap = 54f)
    }
'''
s = re.sub(r'    private fun radial\(.*?\n    \}\n\n    private fun radialCards', radial + '\n    private fun radialCards', s, flags=re.S, count=1)

radial_cards = '''    private fun radialCards(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val result = mutableListOf<PositionedStudyMapNode>()
        val root = byId[map.rootNodeId] ?: return emptyList()
        val centerX = width / 2f
        val centerY = height / 2f
        val rootWidth = 260f
        val rootHeight = estimatedNodeHeight(root, 120f, rootWidth)
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)

        val firstLevel = children[map.rootNodeId].orEmpty()
        val radiusX = max(width * 0.34f, 520f)
        val radiusY = max(height * 0.31f, 450f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = (2.0 * PI / max(firstLevel.size, 1)) * index - PI / 2.0
            val centerNodeX = centerX + radiusX * cos(angle).toFloat()
            val centerNodeY = centerY + radiusY * sin(angle).toFloat()
            val cardWidth = 235f
            val cardHeight = estimatedNodeHeight(node, 126f, cardWidth)
            result += PositionedStudyMapNode(node, centerNodeX - cardWidth / 2f, centerNodeY - cardHeight / 2f, cardWidth, cardHeight)

            val grandchildren = children[id].orEmpty().take(5)
            val outwardX = cos(angle).toFloat()
            val outwardY = sin(angle).toFloat()
            val tangentX = -outwardY
            val tangentY = outwardX
            val childBaseX = centerNodeX + outwardX * 320f
            val childBaseY = centerNodeY + outwardY * 270f
            val spacing = 235f
            val offsetStart = -(grandchildren.size - 1) * spacing / 2f
            grandchildren.forEachIndexed { childIndex, childId ->
                val child = byId[childId] ?: return@forEachIndexed
                val childWidth = 205f
                val childHeight = estimatedNodeHeight(child, 90f, childWidth)
                val offset = offsetStart + childIndex * spacing
                val childCenterX = childBaseX + tangentX * offset
                val childCenterY = childBaseY + tangentY * offset
                result += PositionedStudyMapNode(child, childCenterX - childWidth / 2f, childCenterY - childHeight / 2f, childWidth, childHeight)
            }
        }
        return resolveOverlaps(result, map.rootNodeId, width, height, minGap = 58f)
    }
'''
s = re.sub(r'    private fun radialCards\(.*?\n    \}\n\n    private fun ideaBoard', radial_cards + '\n    private fun ideaBoard', s, flags=re.S, count=1)

idea = '''    private fun ideaBoard(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val result = mutableListOf<PositionedStudyMapNode>()
        val root = byId[map.rootNodeId] ?: return emptyList()
        val centerX = width / 2f
        val centerY = height / 2f
        val rootWidth = 270f
        val rootHeight = estimatedNodeHeight(root, 136f, rootWidth)
        result += PositionedStudyMapNode(root, centerX - rootWidth / 2f, centerY - rootHeight / 2f, rootWidth, rootHeight)

        val firstLevel = children[map.rootNodeId].orEmpty().take(8)
        val slots = listOf(-PI / 2.0, -PI / 6.0, PI / 6.0, PI / 2.0, 5.0 * PI / 6.0, -5.0 * PI / 6.0, 0.0, PI)
        val radiusX = max(width * 0.35f, 560f)
        val radiusY = max(height * 0.32f, 470f)

        firstLevel.forEachIndexed { index, id ->
            val node = byId[id] ?: return@forEachIndexed
            val angle = slots[index % slots.size]
            val cx = centerX + radiusX * cos(angle).toFloat()
            val cy = centerY + radiusY * sin(angle).toFloat()
            val cardWidth = 245f
            val cardHeight = estimatedNodeHeight(node, 148f, cardWidth)
            result += PositionedStudyMapNode(node, cx - cardWidth / 2f, cy - cardHeight / 2f, cardWidth, cardHeight)

            val details = children[id].orEmpty().take(3)
            val outwardX = cos(angle).toFloat()
            val outwardY = sin(angle).toFloat()
            val tangentX = -outwardY
            val tangentY = outwardX
            details.forEachIndexed { detailIndex, detailId ->
                val detail = byId[detailId] ?: return@forEachIndexed
                val detailWidth = 205f
                val detailHeight = estimatedNodeHeight(detail, 92f, detailWidth)
                val spread = (detailIndex - (details.size - 1) / 2f) * 235f
                val detailCx = cx + outwardX * 325f + tangentX * spread
                val detailCy = cy + outwardY * 285f + tangentY * spread
                result += PositionedStudyMapNode(detail, detailCx - detailWidth / 2f, detailCy - detailHeight / 2f, detailWidth, detailHeight)
            }
        }
        return resolveOverlaps(result, map.rootNodeId, width, height, minGap = 62f)
    }
'''
s = re.sub(r'    private fun ideaBoard\(.*?\n    \}\n\n    private fun tree', idea + '\n    private fun tree', s, flags=re.S, count=1)

constellation = '''    private fun constellation(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val base = radial(map, width, height)
        val jittered = base.mapIndexed { index, positioned ->
            if (positioned.node.id == map.rootNodeId) positioned
            else positioned.copy(
                x = positioned.x + ((((index * 37) % 17) - 8) * 5f),
                y = positioned.y + ((((index * 53) % 15) - 7) * 5f)
            )
        }
        return resolveOverlaps(jittered, map.rootNodeId, width, height, minGap = 66f, iterations = 90)
    }

    private fun resolveOverlaps(
        source: List<PositionedStudyMapNode>,
        rootId: String,
        canvasWidth: Float,
        canvasHeight: Float,
        minGap: Float,
        iterations: Int = 70
    ): List<PositionedStudyMapNode> {
        if (source.size < 2) return source
        val nodes = source.toMutableList()
        repeat(iterations) {
            var moved = false
            for (i in 0 until nodes.lastIndex) {
                for (j in i + 1 until nodes.size) {
                    val a = nodes[i]
                    val b = nodes[j]
                    val acx = a.x + a.width / 2f
                    val acy = a.y + a.height / 2f
                    val bcx = b.x + b.width / 2f
                    val bcy = b.y + b.height / 2f
                    val overlapX = (a.width + b.width) / 2f + minGap - abs(acx - bcx)
                    val overlapY = (a.height + b.height) / 2f + minGap - abs(acy - bcy)
                    if (overlapX <= 0f || overlapY <= 0f) continue
                    moved = true
                    val aFixed = a.node.id == rootId
                    val bFixed = b.node.id == rootId
                    if (overlapX < overlapY) {
                        val direction = if (bcx >= acx) 1f else -1f
                        val push = overlapX + 2f
                        val aPush = if (aFixed) 0f else if (bFixed) push else push / 2f
                        val bPush = if (bFixed) 0f else if (aFixed) push else push / 2f
                        nodes[i] = a.copy(x = a.x - direction * aPush)
                        nodes[j] = b.copy(x = b.x + direction * bPush)
                    } else {
                        val direction = if (bcy >= acy) 1f else -1f
                        val push = overlapY + 2f
                        val aPush = if (aFixed) 0f else if (bFixed) push else push / 2f
                        val bPush = if (bFixed) 0f else if (aFixed) push else push / 2f
                        nodes[i] = a.copy(y = a.y - direction * aPush)
                        nodes[j] = b.copy(y = b.y + direction * bPush)
                    }
                }
            }
            for (index in nodes.indices) {
                val n = nodes[index]
                if (n.node.id == rootId) continue
                nodes[index] = n.copy(
                    x = n.x.coerceIn(24f, (canvasWidth - n.width - 24f).coerceAtLeast(24f)),
                    y = n.y.coerceIn(24f, (canvasHeight - n.height - 24f).coerceAtLeast(24f))
                )
            }
            if (!moved) return nodes
        }
        return nodes
    }
'''
s = re.sub(r'    private fun constellation\(.*?\n    \}\n\n    private fun depthOf', constellation + '\n    private fun depthOf', s, flags=re.S, count=1)

p.write_text(s)

# --- StudyMapScreen: roomy virtual canvas and no implicit fit when changing layout ---
p = root / 'app/src/main/java/com/notcan/app/ui/maps/StudyMapScreen.kt'
s = p.read_text()
s = s.replace(
    'onLayoutChange = { layoutStyle = it; collapsedNodes = emptySet(); fitRequest++ },',
    'onLayoutChange = { layoutStyle = it; collapsedNodes = emptySet(); fitRequest = 0; zoom = 1f; centerRequest++ },'
)
old = '''            val virtualWidthDpValue = maxOf(
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
            )'''
new = '''            val nodeCount = visibleMap.nodes.size.coerceAtLeast(1)
            val virtualWidthDpValue = maxOf(
                maxWidth.value,
                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES, StudyMapLayoutStyle.TREE -> 1700f
                    StudyMapLayoutStyle.RADIAL, StudyMapLayoutStyle.RADIAL_CARDS,
                    StudyMapLayoutStyle.IDEA_BOARD, StudyMapLayoutStyle.CONSTELLATION -> maxOf(1800f, nodeCount * 96f)
                }
            )
            val virtualHeightDpValue = maxOf(
                maxHeight.value,
                textDemand * 0.86f,
                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES, StudyMapLayoutStyle.TREE -> nodeCount * 158f
                    else -> maxOf(1800f, nodeCount * 112f)
                }
            )'''
if old not in s:
    raise SystemExit('StudyMapScreen virtual canvas block not found')
s = s.replace(old, new)
p.write_text(s)

# --- DOCX: don't render normal dark Word text as black on NotCan's dark editor ---
p = root / 'app/src/main/java/com/notcan/app/ui/home/NoteDocxImporter.kt'
s = p.read_text()
old = '            color?.let { add("color:#${it.uppercase()}") }'
new = '''            color?.let { hex ->
                val rgb = hex.toIntOrNull(16)
                if (rgb != null) {
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
                    // Word usually stores ordinary body text as #000000. Keeping it would make the
                    // imported note look empty on NotCan's dark editor, so dark/default colours inherit
                    // the editor foreground. Bright intentional colours are preserved.
                    if (luminance >= 105.0) add("color:#${hex.uppercase()}")
                }
            }'''
if old not in s:
    raise SystemExit('DOCX color line not found')
s = s.replace(old, new)
p.write_text(s)

# --- WriterNoteEditor: accept body updates that arrive after an imported page is created ---
p = root / 'app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt'
s = p.read_text()
s = s.replace(
    'var lastSavedHtml by remember(note.id) { mutableStateOf(note.body) }',
    'var lastSavedHtml by remember(note.id) { mutableStateOf(normalizeStoredBody(note.body)) }',
)
needle = '''    var confirmDelete by remember(note.id) { mutableStateOf(false) }
    var shareMenu by remember(note.id) { mutableStateOf(false) }

    LaunchedEffect(note.id, html) {'''
replacement = '''    var confirmDelete by remember(note.id) { mutableStateOf(false) }
    var shareMenu by remember(note.id) { mutableStateOf(false) }

    LaunchedEffect(note.id, note.body) {
        val externalHtml = normalizeStoredBody(note.body)
        if (externalHtml != html && externalHtml != lastSavedHtml) {
            // Imports are created and then populated asynchronously. If the editor was already
            // composed with an empty page, reload the newly arrived body instead of keeping blank HTML.
            html = externalHtml
            lastSavedHtml = externalHtml
            webView?.loadDataWithBaseURL(null, writerDocument(externalHtml), "text/html", "UTF-8", null)
        } else if (externalHtml == html) {
            lastSavedHtml = externalHtml
        }
    }

    LaunchedEffect(note.id, html) {'''
if needle not in s:
    raise SystemExit('Writer state insertion point not found')
s = s.replace(needle, replacement)
p.write_text(s)

# --- Version ---
p = root / 'app/build.gradle.kts'
s = p.read_text().replace('versionCode = 26', 'versionCode = 27').replace('versionName = "0.8.9"', 'versionName = "0.8.10"')
p.write_text(s)

print('Applied NotCan 0.8.10 map legibility and DOCX import fixes')
