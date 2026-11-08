package com.sameerasw.essentials.island.state

data class CompactEntry(
    val itemKey: String,
    val priority: Int,
    val pinned: Boolean,
    // [icon, value] — icon always ends up on the outer edge.
    val cellKeys: List<String>,
    val soloCellKeys: List<String> = emptyList(),
    val needsCompany: Boolean = false,
    val companionOnly: Boolean = false,
    val keepTogether: Boolean = false,
    // Always placed right of the camera, at the outer end
    val endSide: Boolean = false,
)

// `before` / `after` are camera-relative and ordered inner (next to camera) → outer.
data class CompactArrangement(
    val before: List<String>,
    val after: List<String>,
    val visibleItems: Set<String>,
) {
    companion object {
        val Empty = CompactArrangement(emptyList(), emptyList(), emptySet())
    }
}

object CompactLayoutEngine {
    const val MAX_CELLS = 4

    // A bubble this wide is worth moving pinned cells across the camera for
    private const val WIDE_BUBBLE_CELLS = 2
    private const val WIDTH_EPSILON = 0.25f
    private const val STICKY_WIDTH = 1f

    fun arrange(
        entries: List<CompactEntry>,
        anchor: CameraAnchor = CameraAnchor.Center,
        maxCells: Int = MAX_CELLS,
        leftExtra: Int = 0,
        balance: Boolean = false,
        maxItems: Int = Int.MAX_VALUE,
        mirror: Boolean = false,
        maxWidth: Float = Float.MAX_VALUE,
        fits: ((before: List<String>, after: List<String>) -> Int?)? = null,
        cellWidth: (String) -> Float = { 1f },
        leftExtraWidth: Float = leftExtra.toFloat(),
        previous: CompactArrangement = CompactArrangement.Empty,
        // Measured width of a whole entry; defaults to the sum of its cells' widths
        widthOf: (CompactEntry) -> Float = { e -> e.cellKeys.sumOf { cellWidth(it).toDouble() }.toFloat() },
        // Widest each side of the camera may get, bubbles included
        sideMax: Float = Float.MAX_VALUE,
        // Cells of a bubble right of the island, already netted out of leftExtra
        endExtra: Int = 0,
    ): CompactArrangement {
        fun once(pool: List<CompactEntry>) =
            arrangeOnce(
                pool, anchor, maxCells, leftExtra, balance, maxItems, mirror, maxWidth, widthOf, sideMax, endExtra,
                cellWidth, leftExtraWidth, previous,
            )

        fun attempt(pool: List<CompactEntry>): CompactArrangement? {
            val result = once(pool)
            if (fits == null || fits(result.before, result.after) != null) return result
            if (anchor != CameraAnchor.Center) return null
            val selected = pool.filter { it.itemKey in result.visibleItems }
            return bestSplit(selected, result, fits)
        }

        var pool = entries
        val dropped = mutableListOf<CompactEntry>()
        var result: CompactArrangement
        while (true) {
            val fitted = attempt(pool)
            if (fitted != null) {
                result = fitted
                break
            }
            val shown = once(pool)
            if (shown.visibleItems.size <= 1) return shown
            val drop = pool.filter { it.itemKey in shown.visibleItems }.maxByOrNull { it.priority } ?: return shown
            pool = pool - drop
            dropped += drop
        }

        for (entry in dropped.sortedBy { it.priority }) {
            val candidate = attempt(pool + entry) ?: continue
            if (candidate.visibleItems.size > result.visibleItems.size && result.visibleItems.all { it in candidate.visibleItems }) {
                pool = pool + entry
                result = candidate
            }
        }
        if (previous.visibleItems.size > result.visibleItems.size &&
            previousStillValid(previous, entries, maxCells, maxItems, maxWidth, widthOf, fits)
        ) {
            return previous
        }
        return result
    }

    private fun previousStillValid(
        previous: CompactArrangement,
        entries: List<CompactEntry>,
        maxCells: Int,
        maxItems: Int,
        maxWidth: Float,
        widthOf: (CompactEntry) -> Float,
        fits: ((List<String>, List<String>) -> Int?)?,
    ): Boolean {
        val byKey = entries.associateBy { it.itemKey }
        val shown = previous.visibleItems.map { byKey[it] ?: return false }
        if (shown.sumOf { it.cellKeys.size } > maxCells) return false
        val dynamic = shown.filterNot { it.pinned }
        if (dynamic.size > maxItems) return false
        if (shown.sumOf { widthOf(it).toDouble() } > maxWidth) return false
        val cells = (previous.before + previous.after).toSet()
        if (cells != shown.flatMap { it.cellKeys }.toSet()) return false
        return fits == null || fits(previous.before, previous.after) != null
    }

    private fun bestSplit(
        selected: List<CompactEntry>,
        base: CompactArrangement,
        fits: (List<String>, List<String>) -> Int?,
    ): CompactArrangement? {
        val pinnedCells = selected.filter { it.pinned }.flatMap { it.cellKeys }.toSet()
        val dynamic = selected.filterNot { it.pinned }.sortedBy { it.priority }
        if (dynamic.isEmpty() || dynamic.size > 6) return null
        val pinnedBefore = base.before.filter { it in pinnedCells }
        val pinnedAfter = base.after.filter { it in pinnedCells }
        var best: CompactArrangement? = null
        var bestScore = Int.MAX_VALUE
        for (mask in 0 until (1 shl dynamic.size)) {
            val before = pinnedBefore.toMutableList()
            val after = pinnedAfter.toMutableList()
            dynamic.forEachIndexed { i, entry ->
                val cells = if (entry.cellKeys.size == 2) entry.cellKeys.reversed() else entry.cellKeys
                if ((mask shr i) and 1 == 1) before += cells else after += cells
            }
            val score = fits(before, after) ?: continue
            if (score < bestScore) {
                bestScore = score
                best = CompactArrangement(before, after, base.visibleItems)
            }
        }
        return best
    }

    private fun arrangeOnce(
        entries: List<CompactEntry>,
        anchor: CameraAnchor,
        maxCells: Int,
        leftExtra: Int,
        balance: Boolean,
        maxItems: Int,
        mirror: Boolean,
        maxWidth: Float,
        widthOf: (CompactEntry) -> Float,
        sideMax: Float,
        endExtra: Int,
        cellWidth: (String) -> Float,
        leftExtraWidth: Float,
        previous: CompactArrangement,
    ): CompactArrangement {
        val base = entries.filterNot { it.companionOnly }
        val companions = entries.filter { it.companionOnly }
        var effective = base
        if (anchor == CameraAnchor.Center && companions.isNotEmpty()) {
            val alone = select(base, maxCells, maxItems, maxWidth, widthOf)
            if (alone.isNotEmpty() && alone.all { it.needsCompany }) effective = base + companions
        }
        val selected = select(effective, maxCells, maxItems, maxWidth, widthOf)
        if (selected.isEmpty()) return CompactArrangement.Empty

        val pinned = selected.filter { it.pinned }
        val dynamic = selected.filterNot { it.pinned }
        val visible = selected.map { it.itemKey }.toSet()

        if (anchor != CameraAnchor.Center) {
            val cells = pinned.flatMap { it.cellKeys } + dynamic.flatMap { it.cellKeys.reversed() }
            return if (anchor == CameraAnchor.Start) {
                CompactArrangement(emptyList(), cells, visible)
            } else {
                CompactArrangement(cells, emptyList(), visible)
            }
        }

        val before = mutableListOf<String>()
        val after = mutableListOf<String>()
        fun width(cells: List<String>) = cells.sumOf { cellWidth(it).toDouble() }.toFloat()
        fun left() = width(before) + leftExtraWidth
        fun right() = width(after)
        val twoCell = dynamic.filter { it.cellKeys.size == 2 }
        val oneCell = dynamic.filter { it.cellKeys.size == 1 }

        val movable = mutableListOf<CompactEntry>()
        val movableAfter = mutableListOf<CompactEntry>()
        if (twoCell.isNotEmpty()) {
            after += twoCell[0].cellKeys.reversed()
            movableAfter += twoCell[0]
            pinned.forEach { before += it.cellKeys }
            twoCell.drop(1).forEach {
                before += it.cellKeys.reversed()
                movable += it
            }
            oneCell.forEach {
                if (left() <= right()) {
                    before += it.cellKeys
                    movable += it
                } else {
                    after += it.cellKeys
                    movableAfter += it
                }
            }
        } else {
            pinned.getOrNull(0)?.let { before += it.cellKeys }
            pinned.getOrNull(1)?.let { after += it.cellKeys }
            oneCell.forEach {
                if (right() <= left()) {
                    after += it.cellKeys
                    movableAfter += it
                } else {
                    before += it.cellKeys
                    movable += it
                }
            }
        }

        if (balance && leftExtraWidth > 0f) {
            for (entry in movable.sortedByDescending { it.priority }) {
                val size = entry.cellKeys.size
                val w = width(entry.cellKeys)
                val left = left()
                val right = right()
                val gap = kotlin.math.abs(left - right)
                val movedGap = kotlin.math.abs((left - w) - (right + w))
                if (movedGap > gap + WIDTH_EPSILON || (movedGap >= gap - WIDTH_EPSILON && left <= right)) break
                before.removeAll(entry.cellKeys.toSet())
                after.addAll(0, if (size == 2) entry.cellKeys.reversed() else entry.cellKeys)
            }
        }

        if (balance && leftExtraWidth >= WIDE_BUBBLE_CELLS) {
            for (entry in pinned.filter { e -> e.cellKeys.all { it in before } }.sortedByDescending { it.priority }) {
                val size = entry.cellKeys.size
                val w = width(entry.cellKeys)
                val left = left()
                val right = right()
                val gap = kotlin.math.abs(left - right)
                val movedGap = kotlin.math.abs((left - w) - (right + w))
                if (movedGap >= gap - WIDTH_EPSILON) break
                before.removeAll(entry.cellKeys.toSet())
                after.addAll(0, if (size == 2) entry.cellKeys.reversed() else entry.cellKeys)
            }
        }

        // Mirror of the passes above for a bubble right of the island (leftExtraWidth < 0)
        if (balance && mirror && leftExtraWidth < 0f) {
            for (entry in movableAfter.sortedByDescending { it.priority }) {
                val size = entry.cellKeys.size
                val w = width(entry.cellKeys)
                val left = left()
                val right = right()
                val gap = kotlin.math.abs(left - right)
                val movedGap = kotlin.math.abs((left + w) - (right - w))
                if (movedGap > gap + WIDTH_EPSILON || (movedGap >= gap - WIDTH_EPSILON && right <= left)) break
                after.removeAll(entry.cellKeys.toSet())
                before.addAll(0, if (size == 2) entry.cellKeys.reversed() else entry.cellKeys)
            }
        }

        if (balance && mirror && -leftExtraWidth >= WIDE_BUBBLE_CELLS) {
            for (entry in pinned.filter { e -> e.cellKeys.all { it in after } }.sortedByDescending { it.priority }) {
                val size = entry.cellKeys.size
                val w = width(entry.cellKeys)
                val left = left()
                val right = right()
                val gap = kotlin.math.abs(left - right)
                val movedGap = kotlin.math.abs((left + w) - (right - w))
                if (movedGap >= gap - WIDTH_EPSILON) break
                after.removeAll(entry.cellKeys.toSet())
                before.addAll(0, if (size == 2) entry.cellKeys.reversed() else entry.cellKeys)
            }
        }

        if (balance && (previous.before.isNotEmpty() || previous.after.isNotEmpty())) {
            val wasBefore = previous.before.toSet()
            val wasAfter = previous.after.toSet()
            for (entry in dynamic) {
                val cells = if (entry.cellKeys.size == 2) entry.cellKeys.reversed() else entry.cellKeys
                val nowBefore = entry.cellKeys.all { it in before }
                val stickBefore = entry.cellKeys.all { it in wasBefore }
                val stickAfter = entry.cellKeys.all { it in wasAfter }
                if (!(nowBefore && stickAfter) && !(!nowBefore && stickBefore)) continue
                val w = width(entry.cellKeys)
                val gap = kotlin.math.abs(left() - right())
                val sign = if (nowBefore) -1 else 1
                val revertedGap = kotlin.math.abs((left() + sign * w) - (right() - sign * w))
                if (revertedGap > gap + STICKY_WIDTH) continue
                if (nowBefore) {
                    before.removeAll(entry.cellKeys.toSet())
                    after.addAll(0, cells)
                } else {
                    after.removeAll(entry.cellKeys.toSet())
                    before += cells
                }
            }
        }

        if (selected.any { it.keepTogether }) groupChips(before, after, selected, leftExtra)

        val dropped = arrangeByWidth(before, after, selected, leftExtra + endExtra, endExtra, sideMax, widthOf)
        return finish(CompactArrangement(before, after, visible - dropped), selected.filterNot { it.itemKey in dropped })
    }

    private fun finish(arrangement: CompactArrangement, selected: List<CompactEntry>): CompactArrangement {
        val (before, after, visible) = arrangement
        val solo = selected.singleOrNull()
        if (solo != null && solo.soloCellKeys.isNotEmpty() && solo.cellKeys.size == 1) {
            if (solo.keepTogether) return CompactArrangement(emptyList(), solo.soloCellKeys + solo.cellKeys, visible)
            return CompactArrangement(solo.soloCellKeys, solo.cellKeys, visible)
        }

        if (before.isEmpty() && selected.size == 1 && after.size == 2 && selected.first().cellKeys.size == 2) {
            val item = selected.first()
            return CompactArrangement(listOf(item.cellKeys[0]), listOf(item.cellKeys[1]), visible)
        }
        return CompactArrangement(before, after, visible)
    }

    internal fun arrangeByWidth(
        before: MutableList<String>,
        after: MutableList<String>,
        entries: List<CompactEntry>,
        leftExtra: Int,
        rightExtra: Int,
        sideMax: Float,
        widthOf: (CompactEntry) -> Float,
    ): Set<String> {
        val byCell = entries.flatMap { e -> e.cellKeys.map { it to e } }.toMap()
        fun blocks(side: List<String>) = side.mapNotNull { byCell[it] }.distinct()
        fun width(side: List<String>, extra: Int) = extra + blocks(side).sumOf { widthOf(it).toDouble() }.toFloat()
        fun narrows(l: Float, r: Float, shift: Float) = kotlin.math.abs((l + shift) - (r - shift)) < kotlin.math.abs(l - r)
        fun move(e: CompactEntry, from: MutableList<String>, to: MutableList<String>) {
            val cells = from.filter { byCell[it] == e }
            from.removeAll(cells.toSet())
            when {
                to === after -> to.addAll(after.count { byCell[it]?.endSide != true }, cells)
                e.keepTogether -> to.addAll(before.indexOfLast { byCell[it]?.keepTogether == true } + 1, cells)
                else -> to += cells
            }
        }
        blocks(before).filter { it.endSide && !it.pinned }.forEach { move(it, before, after) }
        blocks(after).filter { it.endSide && !it.pinned }.forEach {
            val cells = after.filter { c -> byCell[c] == it }
            after.removeAll(cells.toSet())
            after += cells
        }
        if (entries.any { it.keepTogether && !it.endSide && !it.pinned }) {
            blocks(before).filter { it.keepTogether && !it.pinned }.forEach { move(it, before, before) }
            blocks(after).filter { it.keepTogether && !it.endSide && !it.pinned }.forEach { move(it, after, before) }
            blocks(before).filter { !it.keepTogether && !it.pinned }.forEach { move(it, before, after) }
            for (e in blocks(after).filter { !it.keepTogether && !it.endSide && !it.pinned }.sortedByDescending { it.priority }) {
                if (!narrows(width(before, leftExtra), width(after, rightExtra), widthOf(e))) break
                move(e, after, before)
            }
        }
        for (e in blocks(before).filter { it.pinned && !it.keepTogether }.sortedByDescending { it.priority }) {
            val r = width(after, rightExtra)
            if (r + widthOf(e) > sideMax || !narrows(width(before, leftExtra), r, -widthOf(e))) continue
            move(e, before, after)
        }
        val dropped = mutableSetOf<String>()
        for (fromBefore in listOf(true, false)) {
            val from = if (fromBefore) before else after
            val to = if (fromBefore) after else before
            val fromExtra = if (fromBefore) leftExtra else rightExtra
            val toExtra = if (fromBefore) rightExtra else leftExtra
            while (width(from, fromExtra) > sideMax) {
                val candidates = blocks(from).filterNot { it.pinned }
                val e = (candidates.filterNot { it.endSide }.ifEmpty { candidates }).maxByOrNull { it.priority } ?: break
                if (!e.endSide && width(to, toExtra) + widthOf(e) <= sideMax) {
                    move(e, from, to)
                } else {
                    from.removeAll(e.cellKeys.toSet())
                    dropped += e.itemKey
                }
            }
        }
        return dropped
    }

    internal fun groupChips(before: MutableList<String>, after: MutableList<String>, entries: List<CompactEntry>, leftExtra: Int) {
        val byCell = entries.flatMap { e -> e.cellKeys.map { it to e } }.toMap()
        fun blocks(side: List<String>) = side.mapNotNull { byCell[it] }.distinct()
        fun cellsOf(side: List<String>, entry: CompactEntry) = side.filter { byCell[it] == entry }
        fun chipCells(side: List<String>) = side.count { byCell[it]?.keepTogether == true }
        fun pinnedChip(side: List<String>) = blocks(side).any { it.keepTogether && it.pinned }
        fun chipRunEnd(side: List<String>) = side.indexOfLast { byCell[it]?.keepTogether == true } + 1

        val gatherBefore = if (pinnedChip(before) != pinnedChip(after)) pinnedChip(before) else chipCells(before) >= chipCells(after)
        val target = if (gatherBefore) before else after
        val source = if (gatherBefore) after else before
        if (chipCells(target) > 0) {
            for (chip in blocks(source).filter { it.keepTogether && !it.pinned }) {
                val chipCellKeys = cellsOf(source, chip)
                val swap = blocks(target).firstOrNull { !it.keepTogether && !it.pinned && it.cellKeys.size == chip.cellKeys.size }
                if (swap != null) {
                    val plain = cellsOf(target, swap)
                    val at = source.indexOf(chipCellKeys.first())
                    source.removeAll(chipCellKeys.toSet())
                    source.addAll(at, plain)
                    target.removeAll(plain.toSet())
                } else {
                    source.removeAll(chipCellKeys.toSet())
                }
                target.addAll(chipRunEnd(target), chipCellKeys)
            }
        }

        fun breaker(side: List<String>): CompactEntry? {
            val order = blocks(side)
            val first = order.indexOfFirst { it.keepTogether }
            val last = order.indexOfLast { it.keepTogether }
            if (first < 0 || last <= first) return null
            return order.subList(first + 1, last).firstOrNull { !it.keepTogether && !it.pinned }
        }
        fun regroup(side: MutableList<String>) {
            val order = blocks(side)
            val firstChip = order.indexOfFirst { it.keepTogether }
            if (firstChip < 0) return
            val rest = order.drop(firstChip)
            if (rest.any { !it.keepTogether && it.pinned }) return
            val arranged = order.take(firstChip) + rest.filter { it.keepTogether } + rest.filterNot { it.keepTogether }
            val cells = arranged.flatMap { cellsOf(side, it) }
            side.clear()
            side += cells
        }
        repeat(entries.size) {
            for (fromBefore in listOf(true, false)) {
                val from = if (fromBefore) before else after
                val to = if (fromBefore) after else before
                val entry = breaker(from) ?: continue
                val size = entry.cellKeys.size
                val left = before.size + leftExtra
                val right = after.size
                val gap = kotlin.math.abs(left - right)
                val movedGap = if (fromBefore) kotlin.math.abs((left - size) - (right + size)) else kotlin.math.abs((left + size) - (right - size))
                if (movedGap <= gap) {
                    val cells = cellsOf(from, entry)
                    from.removeAll(cells.toSet())
                    to += cells
                } else {
                    regroup(from)
                }
            }
        }
        regroup(before)
        regroup(after)
    }

    private fun select(
        entries: List<CompactEntry>,
        maxCells: Int,
        maxItems: Int,
        maxWidth: Float = Float.MAX_VALUE,
        widthOf: (CompactEntry) -> Float = { it.cellKeys.size.toFloat() },
    ): List<CompactEntry> {
        var used = 0
        var dynamic = 0
        var width = entries.filter { it.pinned }.sumOf { widthOf(it).toDouble() }.toFloat()
        val result = mutableListOf<CompactEntry>()
        for (entry in entries.sortedBy { it.priority }) {
            val size = entry.cellKeys.size
            if (used + size > maxCells) continue
            if (!entry.pinned && dynamic >= maxItems) continue
            val w = if (entry.pinned) 0f else widthOf(entry)
            if (!entry.pinned && width + w > maxWidth) continue
            used += size
            width += w
            if (!entry.pinned) dynamic++
            result += entry
        }
        return result
    }
}
