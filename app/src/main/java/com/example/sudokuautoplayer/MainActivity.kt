package com.example.sudokuautoplayer

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        val status = TextView(this).apply { textSize = 17f; text = "Starter prototype. Enable accessibility first." }
        layout.addView(status)
        fun addButton(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        addButton("Enable accessibility service") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        addButton("Start automation") { SudokuAccessibilityService.requestStart(); status.text = "Start requested. Game automation is not implemented yet." }
        addButton("Pause") { SudokuAccessibilityService.requestPause(); status.text = "Paused." }
        addButton("Stop") { SudokuAccessibilityService.requestStop(); status.text = "Stopped." }
        layout.addView(TextView(this).apply {
            text = "This is an early scaffold. Board OCR, game control, completion detection, repeat mode and ad handling are still TODO."
            textSize = 14f
        })
        setContentView(layout)
    }
}
