package com.example.sudokuautoplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            status.text = intent?.getStringExtra("status") ?: status.text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        layout.addView(TextView(this).apply {
            text = "Sudoku Auto Player"
            textSize = 24f
        })
        status = TextView(this).apply {
            textSize = 16f
            text = getSharedPreferences("automation", MODE_PRIVATE)
                .getString("status", "Enable the accessibility service, then open Sudoku.")
        }
        layout.addView(status)
        fun addButton(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        addButton("1. Enable accessibility service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton("2. Open Sudoku game") {
            val launch = packageManager.getLaunchIntentForPackage("com.unicostudio.sudoku")
            if (launch != null) startActivity(launch)
            else status.text = "Please open Unico Studio Sudoku manually; its package name may differ."
        }
        addButton("Start") {
            if (!SudokuAccessibilityService.isConnected()) {
                status.text = "Accessibility service is not connected. Enable it in Android Settings first."
            } else {
                SudokuAccessibilityService.requestStart()
                status.text = "Starting…"
            }
        }
        addButton("Pause") { SudokuAccessibilityService.requestPause(); status.text = "Pause requested." }
        addButton("Stop") { SudokuAccessibilityService.requestStop(); status.text = "Stop requested." }
        layout.addView(TextView(this).apply {
            textSize = 13f
            text = "Current build can recognize labelled game menus and select New Game / Easy when those controls are exposed by Android. Automatic 9×9 board reading, solving-in-game and keypad entry are not finished yet. Ads are never clicked."
        })
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, statusReceiver,
            IntentFilter(SudokuAccessibilityService.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        try { unregisterReceiver(statusReceiver) } catch (_: IllegalArgumentException) {}
        super.onPause()
    }
}
