package com.example.sudokuautoplayer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class SudokuAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val ocr by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    @Volatile private var running = false
    @Volatile private var busy = false
    private var gridX = 0f; private var gridY = 0f; private var gridW = 0f; private var gridH = 0f
    private var keyY = 0f
    private val keyX = FloatArray(9)
    private var puzzle = Array(9) { IntArray(9) }
    private var answer = Array(9) { IntArray(9) }
    private val blanks = mutableListOf<Pair<Int, Int>>()
    private var pos = 0

    override fun onServiceConnected() {
        instance = this
        status("Accessibility connected. Open Sudoku, then press Start.")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || busy || event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handler.removeCallbacks(task); handler.postDelayed(task, 800)
        }
    }
    private val task = Runnable { inspect() }

    private fun inspect() {
        if (!running || busy) return
        val root = rootInActiveWindow
        if (root != null) {
            val words = mutableListOf<String>(); collect(root, words); root.recycle()
            val s = words.joinToString(" ").lowercase()
            if ((s.contains("skip") || s.contains("close ad")) && (s.contains("ad") || s.contains("skip"))) {
                if (tapLabel("skip", "close ad")) {
                    status("Dismissed visible ad close/skip control.")
                    handler.postDelayed(task, 900)
                    return
                }
            }
            if (s.contains("excellent") || s.contains("well done") || s.contains("congratulations")) {
                status("Puzzle complete. Starting another Easy game.")
                if (tapLabel("new game", "play again")) handler.postDelayed({ tapLabel("easy"); handler.postDelayed({ capture() }, 1000) }, 1000)
                return
            }
            if (s.contains("new game")) {
                status("Starting a new game.")
                if (tapLabel("new game")) handler.postDelayed({ tapLabel("easy"); handler.postDelayed({ capture() }, 1000) }, 1000)
                else status("New Game is visible but Android exposes no tap target. Trying screen OCR.")
                if (s.contains("easy")) handler.postDelayed({ capture() }, 1500)
                return
            }
            if (s.contains("easy") && (s.contains("medium") || s.contains("hard") || s.contains("difficulty"))) {
                if (tapLabel("easy")) handler.postDelayed({ capture() }, 1000)
                else status("Could not tap Easy; retrying.")
                return
            }
        }
        capture()
    }
    private fun collect(n: AccessibilityNodeInfo, out: MutableList<String>) {
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until n.childCount) { val c=n.getChild(i) ?: continue; collect(c,out); c.recycle() }
    }

    private fun capture() {
        if (!running || busy) return
        if (Build.VERSION.SDK_INT < 30) { status("Screen OCR requires Android 11+."); return }
        busy = true
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(s: ScreenshotResult) {
                val bmp = try { Bitmap.wrapHardwareBuffer(s.hardwareBuffer, s.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { null }
                s.hardwareBuffer.close()
                if (bmp == null) { busy=false; status("Could not capture game screen."); return }
                ocr.process(InputImage.fromBitmap(bmp,0))
                    .addOnSuccessListener { result -> readBoard(bmp.width,bmp.height,result.textBlocks.flatMap { it.lines }) }
                    .addOnFailureListener { status("OCR error: " + (it.message ?: "unknown")) }
                    .addOnCompleteListener { bmp.recycle(); busy=false }
            }
            override fun onFailure(code: Int) { busy=false; status("Screen capture failed ($code). Check accessibility permission.") }
        })
    }

    private fun readBoard(w: Int, h: Int, lines: List<com.google.mlkit.vision.text.Text.Line>) {
        // Calibrated against the supplied Unico Sudoku gameplay screenshot.
        gridX=w*0.0104f; gridY=h*0.1940f; gridW=w*0.9792f; gridH=h*0.4520f
        // Stable keypad centres calibrated from the supplied 832x1790 game screen.
        keyY=h*0.839f
        for (i in 0..8) keyX[i]=w*(0.065f+i*0.1085f)
        val b=Array(9){IntArray(9)}; var clues=0
        for (line in lines) {
            val r=line.boundingBox ?: continue
            val x=r.exactCenterX(); val y=r.exactCenterY()
            if (x<gridX || x>=gridX+gridW || y<gridY || y>=gridY+gridH) continue
            val t=line.text.trim()
            if (t.length!=1 || t[0] !in '1'..'9') continue
            val c=((x-gridX)/(gridW/9f)).toInt().coerceIn(0,8)
            val row=((y-gridY)/(gridH/9f)).toInt().coerceIn(0,8)
            if (b[row][c]==0) { b[row][c]=t.toInt(); clues++ }
        }
        if (clues<25) { status("Only $clues grid clues detected (need at least 25). No taps made; checking screen alignment."); handler.postDelayed({capture()},1800); return }
        val solved=SudokuSolver.solve(b)
        if (solved==null) { status("Puzzle OCR gave conflicting clues. No numbers entered; retrying."); handler.postDelayed({capture()},1800); return }
        puzzle=Array(9){b[it].clone()}; answer=solved
        blanks.clear()
        for(r in 0..8) for(c in 0..8) if(puzzle[r][c]==0) blanks.add(r to c)
        pos=0; status("Read $clues clues and solved the puzzle. Entering ${blanks.size} numbers.")
        handler.postDelayed({ enterCell() },350)
    }

    private fun enterCell() {
        if(!running) return
        if(pos>=blanks.size) { status("All numbers entered. Checking completion…"); handler.postDelayed({capture()},1800); return }
        val (r,c)=blanks[pos]; val n=answer[r][c]
        tap(gridX+(c+0.5f)*gridW/9f, gridY+(r+0.5f)*gridH/9f) {
            handler.postDelayed({
                tap(keyX[n-1],keyY) {
                    pos++
                    if(pos%10==0 || pos==blanks.size) status("Entering numbers: $pos/${blanks.size}")
                    handler.postDelayed({enterCell()},120)
                }
            },100)
        }
    }

    private fun tapLabel(vararg labels:String):Boolean {
        val root=rootInActiveWindow ?: return false
        try {
            for(label in labels) {
                val nodes=root.findAccessibilityNodeInfosByText(label)
                for(node in nodes) {
                    val txt=node.text?.toString().orEmpty()+node.contentDescription?.toString().orEmpty()
                    if(!txt.contains(label,true)) { node.recycle(); continue }
                    var cur:AccessibilityNodeInfo?=AccessibilityNodeInfo.obtain(node)
                    while(cur!=null) {
                        if(cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            cur.recycle(); node.recycle(); return true
                        }
                        val p=cur.parent; cur.recycle(); cur=p
                    }
                    val rect=Rect(); node.getBoundsInScreen(rect); node.recycle()
                    if(!rect.isEmpty) return tap(rect.exactCenterX(),rect.exactCenterY())
                }
            }
        } finally { root.recycle() }
        return false
    }
    private fun tap(x:Float,y:Float,done:(()->Unit)?=null):Boolean {
        val p=Path().apply{moveTo(x,y)}
        val g=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,75)).build()
        return dispatchGesture(g,object:GestureResultCallback(){
            override fun onCompleted(gestureDescription:GestureDescription?){done?.invoke()}
            override fun onCancelled(gestureDescription:GestureDescription?){status("Tap cancelled. Paused to prevent wrong entries."); running=false}
        },handler)
    }
    private fun status(s:String) {
        getSharedPreferences("automation",MODE_PRIVATE).edit().putString("status",s).apply()
        sendBroadcast(android.content.Intent(ACTION_STATUS).setPackage(packageName).putExtra("status",s))
    }
    override fun onInterrupt(){running=false;status("Paused by Android.")}
    override fun onDestroy(){instance=null;ocr.close();super.onDestroy()}
    private fun start(){running=true;status("Started. Reading Sudoku screen…");handler.post(task)}
    private fun pause(){running=false;status("Paused.")}
    private fun stop(){running=false;handler.removeCallbacksAndMessages(null);status("Stopped.")}
    companion object {
        const val ACTION_STATUS="com.example.sudokuautoplayer.STATUS"
        @Volatile private var instance:SudokuAccessibilityService?=null
        fun requestStart(){instance?.start()}
        fun requestPause(){instance?.pause()}
        fun requestStop(){instance?.stop()}
        fun isConnected()=instance!=null
    }
}
