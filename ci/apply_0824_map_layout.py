from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(p): return (ROOT / p).read_text(encoding='utf-8')
def write(p, s):
    path = ROOT / p
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(s, encoding='utf-8')

def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f'pattern not found: {path}: {old[:120]!r}')
    write(path, text.replace(old, new, 1))

# Version
replace_once('app/build.gradle.kts', 'versionCode = 46\n        versionName = "0.8.23"', 'versionCode = 47\n        versionName = "0.8.24"')

# Layout engine: reserve bounded readable card heights and hierarchical layouts.
p = 'app/src/main/java/com/notcan/app/ui/maps/StudyMapLayoutEngine.kt'
text = read(p)
text = re.sub(
    r'    private fun estimatedNodeHeight\(node: StudyMapNode, base: Float, width: Float = 210f\): Float \{.*?\n    \}\n    fun layout\(',
    '''    private fun estimatedNodeHeight(node: StudyMapNode, base: Float, width: Float = 210f): Float {
        // Map cards are intentionally summaries. The full node text remains in the artifact,
        // while the canvas reserves a bounded number of lines so one verbose node cannot
        // collapse an entire layout into a tall overlapping column.
        val usableWidth = (width - 34f).coerceAtLeast(120f)
        val titleCharsPerLine = (usableWidth / 10.2f).toInt().coerceIn(13, 30)
        val bodyCharsPerLine = (usableWidth / 8.0f).toInt().coerceIn(17, 36)
        val titleLines = ceil(node.title.length.coerceAtLeast(1) / titleCharsPerLine.toFloat())
            .toInt().coerceIn(1, 4)
        val descriptionLines = node.description?.takeIf { it.isNotBlank() }
            ?.let { ceil(it.length / bodyCharsPerLine.toFloat()).toInt().coerceIn(1, 6) } ?: 0
        val sourceLines = if (node.sourceRefs.isNotEmpty()) 1 else 0
        val measured = 30f + titleLines * 27f + descriptionLines * 21f + sourceLines * 22f
        return max(base, measured + 16f)
    }
    fun layout(''',
    text,
    count=1,
    flags=re.S
)

# Replace horizontal branches implementation.
start = text.index('    private fun horizontalBranches(')
end = text.index('    private fun radial(', start)
new_horizontal = '''    private fun horizontalBranches(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val root = byId[map.rootNodeId] ?: return emptyList()
        val result = mutableListOf<PositionedStudyMapNode>()
        val verticalGap = 54f
        val horizontalGap = 84f
        val rootWidth = 270f
        val rootHeight = estimatedNodeHeight(root, 112f, rootWidth)
        val subtreeCache = mutableMapOf<String, Float>()

        fun cardWidth(depth: Int): Float = when {
            depth == 0 -> rootWidth
            depth == 1 -> 250f
            depth == 2 -> 225f
            else -> 205f
        }

        fun cardHeight(id: String, depth: Int): Float {
            val node = byId[id] ?: return 90f
            return estimatedNodeHeight(node, if (depth <= 1) 104f else 90f, cardWidth(depth))
        }

        fun subtreeHeight(id: String, depth: Int, seen: Set<String> = emptySet()): Float {
            subtreeCache[id]?.let { return it }
            if (id in seen) return cardHeight(id, depth)
            val childIds = children[id].orEmpty().filterNot { it in seen }
            val own = cardHeight(id, depth)
            val descendants = if (childIds.isEmpty()) 0f else {
                childIds.sumOf { subtreeHeight(it, depth + 1, seen + id).toDouble() }.toFloat() +
                    verticalGap * (childIds.size - 1).coerceAtLeast(0)
            }
            return max(own, descendants).also { subtreeCache[id] = it }
        }

        val totalHeight = subtreeHeight(map.rootNodeId, 0)
        val contentTop = max(54f, (height - totalHeight) / 2f)
        val rootY = contentTop + totalHeight / 2f - rootHeight / 2f
        val rootX = 64f
        result += PositionedStudyMapNode(root, rootX, rootY, rootWidth, rootHeight)

        fun placeChildren(parentId: String, depth: Int, left: Float, top: Float, seen: Set<String>) {
            val childIds = children[parentId].orEmpty().filterNot { it in seen }
            if (childIds.isEmpty()) return
            var cursor = top
            childIds.forEach { childId ->
                val node = byId[childId] ?: return@forEach
                val branchHeight = subtreeHeight(childId, depth, seen + parentId)
                val w = cardWidth(depth)
                val h = cardHeight(childId, depth)
                val y = cursor + branchHeight / 2f - h / 2f
                result += PositionedStudyMapNode(node, left, y, w, h)
                placeChildren(childId, depth + 1, left + w + horizontalGap, cursor, seen + parentId + childId)
                cursor += branchHeight + verticalGap
            }
        }

        placeChildren(map.rootNodeId, 1, rootX + rootWidth + horizontalGap, contentTop, setOf(map.rootNodeId))
        return result
    }

'''
text = text[:start] + new_horizontal + text[end:]

# Replace tree with classic top-down hierarchy.
start = text.index('    private fun tree(')
end = text.index('    private fun constellation(', start)
new_tree = '''    private fun tree(map: StudyMap, width: Float, height: Float): List<PositionedStudyMapNode> {
        val byId = map.nodes.associateBy { it.id }
        val children = map.edges.groupBy { it.from }.mapValues { (_, value) -> value.map { it.to } }
        val root = byId[map.rootNodeId] ?: return emptyList()
        val result = mutableListOf<PositionedStudyMapNode>()
        val horizontalGap = 58f
        val verticalGap = 118f
        val leafWidth = 245f
        val subtreeWidthCache = mutableMapOf<String, Float>()

        fun nodeWidth(depth: Int): Float = if (depth == 0) 270f else if (depth == 1) 235f else 210f
        fun nodeHeight(id: String, depth: Int): Float {
            val node = byId[id] ?: return 92f
            return estimatedNodeHeight(node, if (depth == 0) 112f else 94f, nodeWidth(depth))
        }

        fun subtreeWidth(id: String, depth: Int, seen: Set<String> = emptySet()): Float {
            subtreeWidthCache[id]?.let { return it }
            if (id in seen) return nodeWidth(depth)
            val childIds = children[id].orEmpty().filterNot { it in seen }
            val own = nodeWidth(depth)
            val descendants = if (childIds.isEmpty()) leafWidth else {
                childIds.sumOf { subtreeWidth(it, depth + 1, seen + id).toDouble() }.toFloat() +
                    horizontalGap * (childIds.size - 1).coerceAtLeast(0)
            }
            return max(own, descendants).also { subtreeWidthCache[id] = it }
        }

        val levels = collectLevels(map.rootNodeId, children)
        val maxHeightByDepth = levels.mapValues { (depth, ids) -> ids.maxOfOrNull { nodeHeight(it, depth) } ?: 96f }
        val yByDepth = mutableMapOf<Int, Float>()
        var yCursor = 56f
        for (depth in 0..(levels.keys.maxOrNull() ?: 0)) {
            yByDepth[depth] = yCursor
            yCursor += (maxHeightByDepth[depth] ?: 96f) + verticalGap
        }

        val totalWidth = subtreeWidth(map.rootNodeId, 0)
        val contentLeft = max(54f, (width - totalWidth) / 2f)

        fun place(id: String, depth: Int, left: Float, seen: Set<String>) {
            val node = byId[id] ?: return
            if (id in seen) return
            val branchWidth = subtreeWidth(id, depth, seen)
            val w = nodeWidth(depth)
            val h = nodeHeight(id, depth)
            val x = left + branchWidth / 2f - w / 2f
            val y = yByDepth[depth] ?: 56f
            result += PositionedStudyMapNode(node, x, y, w, h)

            val childIds = children[id].orEmpty().filterNot { it in seen }
            var childLeft = left
            childIds.forEach { childId ->
                val cw = subtreeWidth(childId, depth + 1, seen + id)
                place(childId, depth + 1, childLeft, seen + id)
                childLeft += cw + horizontalGap
            }
        }

        place(root.id, 0, contentLeft, emptySet())
        return result
    }

'''
text = text[:start] + new_tree + text[end:]
write(p, text)

# Screen: auto-fit, separate tree edge geometry, bounded card text, larger virtual canvas.
p = 'app/src/main/java/com/notcan/app/ui/maps/StudyMapScreen.kt'
text = read(p)
text = text.replace(
'''                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES, StudyMapLayoutStyle.TREE -> 1700f
                    StudyMapLayoutStyle.RADIAL, StudyMapLayoutStyle.RADIAL_CARDS,
                    StudyMapLayoutStyle.IDEA_BOARD, StudyMapLayoutStyle.CONSTELLATION -> maxOf(1800f, nodeCount * 96f)
                }
''',
'''                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> maxOf(1800f, nodeCount * 150f)
                    StudyMapLayoutStyle.TREE -> maxOf(1900f, nodeCount * 250f)
                    StudyMapLayoutStyle.RADIAL, StudyMapLayoutStyle.RADIAL_CARDS,
                    StudyMapLayoutStyle.IDEA_BOARD, StudyMapLayoutStyle.CONSTELLATION -> maxOf(1900f, nodeCount * 112f)
                }
''')
text = text.replace(
'''                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES, StudyMapLayoutStyle.TREE -> nodeCount * 158f
                    else -> maxOf(1800f, nodeCount * 112f)
                }
''',
'''                when (layoutStyle) {
                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> maxOf(1500f, nodeCount * 190f)
                    StudyMapLayoutStyle.TREE -> maxOf(1400f, nodeCount * 120f)
                    else -> maxOf(1900f, nodeCount * 128f)
                }
''')
text = text.replace(
'''                ).coerceIn(0.62f, 1.35f)
                // Al abrir priorizamos lectura a tamaño real. Ajustar puede reducir explícitamente.
                val targetZoom = if (fitRequest == 0) 1f else fitted
''',
'''                ).coerceIn(0.38f, 1.25f)
                // Open every layout already framed inside the viewport. The user can immediately
                // zoom in for reading instead of discovering half the graph outside the screen.
                val targetZoom = if (fitRequest == 0) minOf(fitted, 0.92f) else fitted
''')

old_geom = '''                            val horizontal = layoutStyle == StudyMapLayoutStyle.HORIZONTAL_BRANCHES || layoutStyle == StudyMapLayoutStyle.TREE
                            val start = if (horizontal) {
                                Offset(from.x + from.width, from.y + from.height / 2f)
                            } else {
                                Offset(from.x + from.width / 2f, from.y + from.height / 2f)
                            }
                            val end = if (horizontal) {
                                Offset(to.x, to.y + to.height / 2f)
                            } else {
                                Offset(to.x + to.width / 2f, to.y + to.height / 2f)
                            }
'''
new_geom = '''                            val start = when (layoutStyle) {
                                StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> Offset(from.x + from.width, from.y + from.height / 2f)
                                StudyMapLayoutStyle.TREE -> Offset(from.x + from.width / 2f, from.y + from.height)
                                else -> Offset(from.x + from.width / 2f, from.y + from.height / 2f)
                            }
                            val end = when (layoutStyle) {
                                StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> Offset(to.x, to.y + to.height / 2f)
                                StudyMapLayoutStyle.TREE -> Offset(to.x + to.width / 2f, to.y)
                                else -> Offset(to.x + to.width / 2f, to.y + to.height / 2f)
                            }
'''
if old_geom not in text: raise RuntimeError('edge geometry pattern missing')
text = text.replace(old_geom, new_geom, 1)

old_label = '''                        val horizontal = layoutStyle == StudyMapLayoutStyle.HORIZONTAL_BRANCHES || layoutStyle == StudyMapLayoutStyle.TREE
                        val startX = if (horizontal) from.x + from.width else from.x + from.width / 2f
                        val startY = from.y + from.height / 2f
                        val endX = if (horizontal) to.x else to.x + to.width / 2f
                        val endY = to.y + to.height / 2f
'''
new_label = '''                        val startX = when (layoutStyle) {
                            StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> from.x + from.width
                            else -> from.x + from.width / 2f
                        }
                        val startY = when (layoutStyle) {
                            StudyMapLayoutStyle.TREE -> from.y + from.height
                            else -> from.y + from.height / 2f
                        }
                        val endX = when (layoutStyle) {
                            StudyMapLayoutStyle.HORIZONTAL_BRANCHES -> to.x
                            else -> to.x + to.width / 2f
                        }
                        val endY = when (layoutStyle) {
                            StudyMapLayoutStyle.TREE -> to.y
                            else -> to.y + to.height / 2f
                        }
'''
if old_label not in text: raise RuntimeError('label geometry pattern missing')
text = text.replace(old_label, new_label, 1)

text = text.replace(
'''                    softWrap = true,
                    modifier = Modifier.weight(1f)
''',
'''                    softWrap = true,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
''', 1)
text = text.replace(
'''                    style = if (visualCard) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    softWrap = true
''',
'''                    style = if (visualCard) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    softWrap = true,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
''', 1)
text = text.replace(
'''                    fontWeight = FontWeight.Medium,
                    softWrap = true
''',
'''                    fontWeight = FontWeight.Medium,
                    softWrap = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
''', 1)
write(p, text)
