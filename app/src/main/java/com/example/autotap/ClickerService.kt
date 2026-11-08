package com.example.autotap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

class ClickerService : AccessibilityService() {

    companion object {
        var instance: ClickerService? = null
        const val PREFS = "autotap"
        const val KEY_CPS = "cps"
        const val MIN_CPS = 1
        const val MAX_CPS = 100
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var panel: View? = null
    private var marker: CrosshairView? = null
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var markerParams: WindowManager.LayoutParams
    private lateinit var toggleBtn: TextView
    private lateinit var minusBtn: TextView
    private lateinit var plusBtn: TextView
    private lateinit var eyeBtn: TextView
    private lateinit var cpsLabel: TextView

    private var running = false
    private var collapsed = false
    private var cps = 10
    private var nextTime = 0.0
    private var gesture: GestureDescription? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_CPS) {
            cps = prefs.getInt(KEY_CPS, 10).coerceIn(MIN_CPS, MAX_CPS)
            if (::cpsLabel.isInitialized) cpsLabel.text = "$cps КПС"
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            gesture?.let { dispatchGesture(it, null, null) }
            nextTime += 1000.0 / cps
            val now = SystemClock.uptimeMillis().toDouble()
            if (nextTime < now) nextTime = now
            handler.postAtTime(this, nextTime.toLong())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        cps = prefs.getInt(KEY_CPS, 10).coerceIn(MIN_CPS, MAX_CPS)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        addMarker()
        addPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        if (running) toggle()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        running = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        marker?.let { wm.removeView(it) }
        panel?.let { wm.removeView(it) }
        marker = null
        panel = null
        instance = null
        return super.onUnbind(intent)
    }

    private fun setCps(value: Int) {
        cps = value.coerceIn(MIN_CPS, MAX_CPS)
        cpsLabel.text = "$cps КПС"
        prefs.edit().putInt(KEY_CPS, cps).apply()
    }

    private fun toggle() {
        running = !running
        toggleBtn.text = if (running) "⏸" else "▶"
        if (running) {
            val m = marker ?: return
            val loc = IntArray(2)
            m.getLocationOnScreen(loc)
            val path = Path().apply {
                moveTo(loc[0] + m.width / 2f, loc[1] + m.height / 2f)
            }
            gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
                .build()
            nextTime = SystemClock.uptimeMillis().toDouble()
            applyMarkerState()
            handler.post(tick)
        } else {
            handler.removeCallbacks(tick)
            applyMarkerState()
        }
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        val vis = if (collapsed) View.GONE else View.VISIBLE
        minusBtn.visibility = vis
        plusBtn.visibility = vis
        cpsLabel.visibility = vis
        eyeBtn.alpha = if (collapsed) 0.5f else 1f
        applyMarkerState()
    }

    private fun applyMarkerState() {
        val m = marker ?: return
        val passThrough = running || collapsed
        markerParams.flags = if (passThrough)
            markerParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else
            markerParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        m.visibility = if (collapsed) View.INVISIBLE else View.VISIBLE
        m.alpha = if (running) 0.5f else 1f
        wm.updateViewLayout(m, markerParams)
    }

    private fun baseParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun addMarker() {
        val size = dp(56)
        val v = CrosshairView(this)
        markerParams = baseParams(size, size).apply {
            x = resources.displayMetrics.widthPixels / 2 - size / 2
            y = resources.displayMetrics.heightPixels / 2 - size / 2
        }
        v.setOnTouchListener(dragListener(markerParams) { v })
        wm.addView(v, markerParams)
        marker = v
    }

    private fun addPanel() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(0xE6212121.toInt())
                cornerRadius = dp(24).toFloat()
            }
        }

        panelParams = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply { x = dp(16); y = dp(120) }

        val handle = label("⠿", 22f).apply {
            setOnTouchListener(dragListener(panelParams) { bar })
        }
        minusBtn = repeatButton("−", -1)
        plusBtn = repeatButton("+", +1)
        cpsLabel = label("$cps КПС", 14f)
        toggleBtn = label("▶", 24f).apply { setOnClickListener { toggle() } }
        eyeBtn = label("👁", 20f).apply { setOnClickListener { toggleCollapsed() } }

        listOf(handle, minusBtn, cpsLabel, plusBtn, toggleBtn, eyeBtn).forEach { bar.addView(it) }
        wm.addView(bar, panelParams)
        panel = bar
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        minWidth = dp(40)
        minHeight = dp(40)
    }

    private fun repeatButton(text: String, delta: Int) = label(text, 24f).apply {
        val repeat = object : Runnable {
            override fun run() {
                setCps(cps + delta)
                handler.postDelayed(this, 80)
            }
        }
        setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    setCps(cps + delta)
                    handler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    handler.removeCallbacks(repeat)
            }
            true
        }
    }

    private fun dragListener(p: WindowManager.LayoutParams, target: () -> View) =
        object : View.OnTouchListener {
            var startX = 0; var startY = 0
            var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = p.x; startY = p.y
                        touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = startX + (e.rawX - touchX).toInt()
                        p.y = startY + (e.rawY - touchY).toInt()
                        wm.updateViewLayout(target(), p)
                    }
                }
                return true
            }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private class CrosshairView(c: Context) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFFFF3B30.toInt()
        }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = width / 2f - 4f
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawLine(cx, cy - r, cx, cy + r, paint)
            canvas.drawLine(cx - r, cy, cx + r, cy, paint)
        }
    }
}
