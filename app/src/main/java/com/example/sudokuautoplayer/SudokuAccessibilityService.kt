package com.example.sudokuautoplayer

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Safe starter only: no blind taps or unverified screen coordinates. */
class SudokuAccessibilityService : AccessibilityService() {
    private var running = false
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running) return
        // TODO: implement verified game-state detection and calibrated interaction.
    }
    override fun onInterrupt() { running = false }
    private fun startAutomation() { running = true }
    private fun pauseAutomation() { running = false }
    private fun stopAutomation() { running = false }
    companion object {
        @Volatile private var instance: SudokuAccessibilityService? = null
        fun requestStart() { instance?.startAutomation() }
        fun requestPause() { instance?.pauseAutomation() }
        fun requestStop() { instance?.stopAutomation() }
    }
}
