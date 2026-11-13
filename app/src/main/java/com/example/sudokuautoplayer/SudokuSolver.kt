package com.example.sudokuautoplayer

/** Backtracking Sudoku solver. Empty cells are 0; returns null for invalid puzzles. */
object SudokuSolver {
    fun solve(input: Array<IntArray>): Array<IntArray>? {
        if (input.size != 9 || input.any { it.size != 9 }) return null
        val board = Array(9) { input[it].clone() }
        if (!valid(board)) return null
        return if (search(board)) board else null
    }
    private fun search(b: Array<IntArray>): Boolean {
        var row = -1; var col = -1; var options: List<Int>? = null
        for (r in 0..8) for (c in 0..8) if (b[r][c] == 0) {
            val candidates = (1..9).filter { allowed(b, r, c, it) }
            if (candidates.isEmpty()) return false
            if (options == null || candidates.size < options!!.size) {
                row = r; col = c; options = candidates
            }
        }
        val choices = options ?: return true
        for (n in choices) { b[row][col] = n; if (search(b)) return true; b[row][col] = 0 }
        return false
    }
    private fun allowed(b: Array<IntArray>, r: Int, c: Int, n: Int): Boolean {
        for (i in 0..8) if (b[r][i] == n || b[i][c] == n) return false
        val r0 = r / 3 * 3; val c0 = c / 3 * 3
        for (y in r0 until r0 + 3) for (x in c0 until c0 + 3) if (b[y][x] == n) return false
        return true
    }
    private fun valid(b: Array<IntArray>): Boolean {
        for (r in 0..8) for (c in 0..8) {
            val n = b[r][c]
            if (n !in 0..9) return false
            if (n != 0) { b[r][c] = 0; val ok = allowed(b, r, c, n); b[r][c] = n; if (!ok) return false }
        }
        return true
    }
}
