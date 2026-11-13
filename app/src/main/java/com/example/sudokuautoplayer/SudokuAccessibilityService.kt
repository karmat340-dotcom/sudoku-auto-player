package com.example.sudokuautoplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

/**
 * Text-driven navigation for the Unico Studio Sudoku game.
 * It deliberately clicks only visible, labelled controls; it never guesses screen coordinates.
 */
class SudokuAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var mode = Mode.STOPPED
    private var lastActionAt = 0L
    private var lastScreen = ""
    private enum class Mode { STOPPED, RUNNING, PAUSED }

    override fun onServiceConnected() {
        instance = this
        publish("Accessibility service connected. Open Sudoku, then tap Start.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (mode != Mode.RUNNING || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        handler.removeCallbacks(scanTask)
        handler.postDelayed(scanTask, 700)
    }

    private val scanTask = Runnable { inspectScreen() }

    private fun inspectScreen() {
        if (mode != Mode.RUNNING) return
        val root = rootInActiveWindow ?: run { publish("Waiting for the game screen…"); return }
        val labels = mutableListOf<String>()
        collectText(root, labels)
        val screen = labels.joinToString(" | ").lowercase()
        if (screen == lastScreen) return
        lastScreen = screen
        Log.d("SudokuAuto", "Visible UI: $screen")

        // Completion screen: start the next Easy game only when the completion label is visible.
        if (screen.contains("excellent") || screen.contains("well done") || screen.contains("congratulations")) {
            publish("Puzzle completed. Looking for New Game…")
            if (clickLabel(root, listOf("new game", "play again"))) scheduleRescan()
            else publish("Puzzle completed, but New Game control was not accessible.")
            return
        }

        // Difficulty selector: choose Easy by its exact visible label.
        if (screen.contains("easy") && (screen.contains("medium") || screen.contains("hard") || screen.contains("difficulty"))) {
            publish("Difficulty menu found. Selecting Easy…")
            if (clickLabel(root, listOf("easy"))) scheduleRescan()
            else publish("Easy option is visible but not clickable.")
            return
        }

        // Home screen: begin a new game.
        if (screen.contains("new game")) {
            publish("Game menu found. Starting a new game…")
            if (clickLabel(root, listOf("new game"))) scheduleRescan()
            else publish("New Game is visible but not clickable.")
            return
        }

        if (screen.contains("sudoku") || screen.contains("hint") || screen.contains("erase")) {
            publish("Sudoku board detected. Board reading and cell entry are not implemented yet; automation paused safely.")
            mode = Mode.PAUSED
            return
        }

        // Ads are not interacted with automatically. Only report what is visible.
        if (screen.contains("install") || screen.contains("learn more") || screen.contains("advertisement")) {
            publish("An ad screen is visible. No ad content will be clicked.")
            return
        }
        publish("Running. Waiting for a recognized Sudoku screen…")
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
            collectText(child, out)
            child.recycle()
        }
    }

    private fun clickLabel(root: AccessibilityNodeInfo, labels: List<String>): Boolean {
        for (label in labels) {
            val matches = root.findAccessibilityNodeInfosByText(label)
            for (node in matches) {
                val exact = node.text?.toString()?.trim()?.equals(label, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.trim()?.equals(label, ignoreCase = true) == true
                if (exact) {
                    var target: AccessibilityNodeInfo? = node
                    while (target != null) {
                        if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            node.recycle()
                            matches.filter { it !== node }.forEach { it.recycle() }
                            return true
                        }
                        target = target.parent
                    }
                }
                node.recycle()
            }
        }
        return false
    }

    private fun scheduleRescan() {
        lastActionAt = System.currentTimeMillis()
        handler.postDelayed({ lastScreen = ""; inspectScreen() }, 1200)
    }

    private fun publish(message: String) {
        getSharedPreferences("automation", MODE_PRIVATE).edit().putString("status", message).apply()
        sendBroadcast(android.content.Intent(ACTION_STATUS).setPackage(packageName).putExtra("status", message))
    }

    override fun onInterrupt() { mode = Mode.PAUSED; publish("Interrupted / paused.") }
    override fun onDestroy() { instance = null; super.onDestroy() }

    private fun startAutomation() {
        mode = Mode.RUNNING
        lastScreen = ""
        publish("Started. Open Unico Studio Sudoku to begin.")
        handler.post(scanTask)
    }
    private fun pauseAutomation() { mode = Mode.PAUSED; publish("Paused.") }
    private fun stopAutomation() { mode = Mode.STOPPED; handler.removeCallbacksAndMessages(null); publish("Stopped.") }

    companion object {
        const val ACTION_STATUS = "com.example.sudokuautoplayer.STATUS"
        @Volatile private var instance: SudokuAccessibilityService? = null
        fun requestStart() { instance?.startAutomation() }
        fun requestPause() { instance?.pauseAutomation() }
        fun requestStop() { instance?.stopAutomation() }
        fun isConnected() = instance != null
    }
}
