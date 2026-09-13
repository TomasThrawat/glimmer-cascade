package com.glimmercascade.game.engine

/**
 * Core match-3 rules engine: detection, cascade resolution, special-tile
 * creation (CometShard / NovaBurst / PrismCore) and special-tile activation.
 */
object MatchEngine {

    data class ResolveResult(
        val totalCleared: Int,
        val cascadeCount: Int,
        val scoreGained: Int,
        val specialsCreated: List<Pair<Int, Int>>
    )

    fun isSpecial(tile: Tile): Boolean =
        tile is Tile.CometShard || tile is Tile.NovaBurst || tile is Tile.PrismCore

    /** All contiguous horizontal/vertical runs of 3+ same-color gems, as separate groups. */
    fun findMatches(board: Board): List<List<Pair<Int, Int>>> {
        val result = mutableListOf<List<Pair<Int, Int>>>()
        for (y in 0 until board.height) {
            var x = 0
            while (x < board.width) {
                val tile = board.get(x, y)
                if (tile is Tile.Gem) {
                    var length = 1
                    while (x + length < board.width) {
                        val next = board.get(x + length, y)
                        if (next is Tile.Gem && next.color == tile.color) length++ else break
                    }
                    if (length >= 3) result.add((0 until length).map { Pair(x + it, y) })
                    x += length
                } else x++
            }
        }
        for (x in 0 until board.width) {
            var y = 0
            while (y < board.height) {
                val tile = board.get(x, y)
                if (tile is Tile.Gem) {
                    var length = 1
                    while (y + length < board.height) {
                        val next = board.get(x, y + length)
                        if (next is Tile.Gem && next.color == tile.color) length++ else break
                    }
                    if (length >= 3) result.add((0 until length).map { Pair(x, y + it) })
                    y += length
                } else y++
            }
        }
        return result
    }

    /** Adjacent, in-bounds, non-blocker/non-empty swap that would create a match. */
    fun isValidSwap(board: Board, x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        if (!board.inBounds(x1, y1) || !board.inBounds(x2, y2)) return false
        if (kotlin.math.abs(x1 - x2) + kotlin.math.abs(y1 - y2) != 1) return false
        val t1 = board.get(x1, y1)
        val t2 = board.get(x2, y2)
        if (t1 is Tile.Blocker || t1 is Tile.Empty) return false
        if (t2 is Tile.Blocker || t2 is Tile.Empty) return false
        board.swap(x1, y1, x2, y2)
        val valid = findMatches(board).any { it.size >= 3 }
        board.swap(x1, y1, x2, y2)
        return valid
    }

    fun hasAnyValidMove(board: Board): Boolean {
        for (y in 0 until board.height) for (x in 0 until board.width) {
            if (x + 1 < board.width && isValidSwap(board, x, y, x + 1, y)) return true
            if (y + 1 < board.height && isValidSwap(board, x, y, x, y + 1)) return true
        }
        return false
    }

    /** Cells a given special tile at (x,y) would clear when activated. */
    private fun cellsForSpecial(board: Board, x: Int, y: Int): Set<Pair<Int, Int>> {
        val cells = mutableSetOf(Pair(x, y))
        when (val tile = board.get(x, y)) {
            is Tile.CometShard -> {
                if (tile.horizontal) for (cx in 0 until board.width) cells.add(Pair(cx, y))
                else for (cy in 0 until board.height) cells.add(Pair(x, cy))
            }
            is Tile.NovaBurst -> {
                for (dx in -1..1) for (dy in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (board.inBounds(nx, ny)) cells.add(Pair(nx, ny))
                }
            }
            is Tile.PrismCore -> {
                var color: GemColor? = null
                for (dx in -1..1) for (dy in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (board.inBounds(nx, ny)) {
                        val t = board.get(nx, ny)
                        if (t is Tile.Gem) color = t.color
                    }
                }
                val target = color ?: GemColor.values().random()
                for (cy in 0 until board.height) for (cx in 0 until board.width) {
                    val t = board.get(cx, cy)
                    if (t is Tile.Gem && t.color == target) cells.add(Pair(cx, cy))
                }
            }
            else -> {}
        }
        return cells
    }

    /**
     * Activates every special tile at the given seed coordinates (typically the
     * one/two tile(s) the player just swapped, when either side was already a
     * special). Chain-activates any further specials it clears. Does NOT run
     * gravity/refill - call resolveCascades afterwards for that plus any
     * resulting matches. Returns (cellsCleared, bonusScore).
     */
    fun activateSpecialsAt(board: Board, seeds: List<Pair<Int, Int>>): Pair<Int, Int> {
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque(seeds.filter { board.inBounds(it.first, it.second) && isSpecial(board.get(it.first, it.second)) })
        var cleared = 0
        var bonus = 0
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (Pair(x, y) in visited) continue
            if (!isSpecial(board.get(x, y))) continue
            visited.add(Pair(x, y))
            bonus += 30
            for (c in cellsForSpecial(board, x, y)) {
                val t = board.get(c.first, c.second)
                if (t !is Tile.Empty) {
                    cleared++
                    if (isSpecial(t) && c !in visited) queue.addLast(c)
                    board.set(c.first, c.second, Tile.Empty)
                }
            }
        }
        return Pair(cleared, bonus)
    }

    /**
     * Resolves all standing matches into clears + specials, applies gravity and
     * refill, and repeats until the board is settled (no matches left). Call
     * activateSpecialsAt first if the triggering move involved swapping a
     * special tile, then always call this to settle the resulting board.
     */
    fun resolveCascades(board: Board): ResolveResult {
        var totalCleared = 0
        var cascadeCount = 0
        var scoreGained = 0
        val specialsCreated = mutableListOf<Pair<Int, Int>>()

        while (true) {
            val matches = findMatches(board)
            if (matches.isEmpty()) break

            var passCleared = 0
            var passSpecials = 0
            val consumed = mutableSetOf<List<Pair<Int, Int>>>()

            // 1) Detect crossing horizontal+vertical pairs sharing a cell -> NovaBurst.
            outer@ for (i in matches.indices) {
                val a = matches[i]
                if (a in consumed) continue
                val aHorizontal = a.map { it.second }.toSet().size == 1
                for (j in matches.indices) {
                    if (i == j) continue
                    val b = matches[j]
                    if (b in consumed) continue
                    val bHorizontal = b.map { it.second }.toSet().size == 1
                    if (aHorizontal == bHorizontal) continue
                    val shared = a.toSet().intersect(b.toSet())
                    if (shared.isNotEmpty()) {
                        val center = shared.first()
                        board.set(center.first, center.second, Tile.NovaBurst((board.get(center.first, center.second) as? Tile.Gem)?.color ?: GemColor.values().random(), board.nextId()))
                        specialsCreated.add(center)
                        passSpecials++
                        for (coord in (a.toSet() + b.toSet())) {
                            if (coord == center) continue
                            if (board.get(coord.first, coord.second) is Tile.Gem) passCleared++
                            board.set(coord.first, coord.second, Tile.Empty)
                        }
                        consumed.add(a); consumed.add(b)
                        continue@outer
                    }
                }
            }

            // 2) Remaining groups: 3 -> plain clear, 4 -> CometShard, 5+ -> PrismCore.
            for (group in matches) {
                if (group in consumed) continue
                when {
                    group.size == 4 -> {
                        val anchor = group.last()
                        val color = (board.get(anchor.first, anchor.second) as? Tile.Gem)?.color ?: GemColor.values().random()
                        val horizontal = group.map { it.second }.toSet().size == 1
                        board.set(anchor.first, anchor.second, Tile.CometShard(color, board.nextId(), horizontal))
                        specialsCreated.add(anchor)
                        passSpecials++
                        for (coord in group) {
                            if (coord == anchor) continue
                            if (board.get(coord.first, coord.second) is Tile.Gem) passCleared++
                            board.set(coord.first, coord.second, Tile.Empty)
                        }
                    }
                    group.size >= 5 -> {
                        val mid = group[group.size / 2]
                        board.set(mid.first, mid.second, Tile.PrismCore(board.nextId()))
                        specialsCreated.add(mid)
                        passSpecials++
                        for (coord in group) {
                            if (coord == mid) continue
                            if (board.get(coord.first, coord.second) is Tile.Gem) passCleared++
                            board.set(coord.first, coord.second, Tile.Empty)
                        }
                    }
                    else -> {
                        for (coord in group) {
                            if (board.get(coord.first, coord.second) is Tile.Gem) passCleared++
                            board.set(coord.first, coord.second, Tile.Empty)
                        }
                    }
                }
            }

            // Gravity + refill per column.
            for (x in 0 until board.width) {
                val nonEmpty = (0 until board.height).map { y -> board.get(x, y) }.filter { it !is Tile.Empty }
                val emptyCount = board.height - nonEmpty.size
                for (y in 0 until board.height) {
                    board.set(x, y, if (y < emptyCount) Tile.Empty else nonEmpty[y - emptyCount])
                }
                for (y in 0 until board.height) {
                    if (board.get(x, y) is Tile.Empty) board.set(x, y, board.randomGem())
                }
            }

            cascadeCount++
            val multiplier = 1.0 + 0.5 * (cascadeCount - 1)
            scoreGained += ((passCleared * 10 + passSpecials * 50) * multiplier).toInt()
            totalCleared += passCleared
        }

        return ResolveResult(totalCleared, cascadeCount, scoreGained, specialsCreated)
    }
}
