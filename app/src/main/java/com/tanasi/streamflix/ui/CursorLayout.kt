package com.tanasi.streamflix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class CursorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val CURSOR_DISAPPEAR_TIMEOUT = 30000L
        private const val CURSOR_ACTIVATION_TIMEOUT = 3000L
    }

    private var cursorRadius: Int = 0
    private var cursorStrokeWidth: Float = 0f
    private var maxCursorSpeed: Float = 0f
    private var scrollStartPadding: Int = 0

    private val cursorPosition = PointF()
    private val cursorSpeed = PointF()
    private val cursorDirection = Point(0, 0)
    private val tmpPointF = PointF()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler()
    private var lastCursorUpdate = System.currentTimeMillis()

    private var isCursorActive = false
    private var upButtonPressed = false
    private var upButtonPressStartTime = 0L

    private var callback: Callback? = null

    interface Callback {
        fun onUserInteraction()
    }

    init {
        setWillNotDraw(false)
        initializeCursor()
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun initializeCursor() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)

        cursorStrokeWidth = size.x / 400f
        cursorRadius = size.x / 110
        maxCursorSpeed = size.x / 25f
        scrollStartPadding = size.x / 15
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cursorPosition.set(w / 2f, h / 2f)
    }

    private val cursorHideRunnable = Runnable { 
        isCursorActive = false
        invalidate() 
    }

    private val cursorActivationRunnable = Runnable {
        isCursorActive = true
        lastCursorUpdate = System.currentTimeMillis()
        invalidate()
    }

    private val cursorUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isCursorActive) {
                return
            }

            val now = System.currentTimeMillis()
            val delta = now - lastCursorUpdate
            lastCursorUpdate = now

            val acceleration = delta * 0.05f

            cursorSpeed.x = clamp(cursorSpeed.x + clamp(cursorDirection.x.toFloat(), 1f) * acceleration, maxCursorSpeed)
            cursorSpeed.y = clamp(cursorSpeed.y + clamp(cursorDirection.y.toFloat(), 1f) * acceleration, maxCursorSpeed)

            if (kotlin.math.abs(cursorSpeed.x) < 0.1f) cursorSpeed.x = 0f
            if (kotlin.math.abs(cursorSpeed.y) < 0.1f) cursorSpeed.y = 0f

            if (cursorDirection == Point(0, 0) && cursorSpeed == PointF(0f, 0f)) {
                handler.post(this)
                return
            }

            tmpPointF.set(cursorPosition)
            cursorPosition.offset(cursorSpeed.x, cursorSpeed.y)

            cursorPosition.x = clamp(cursorPosition.x, 0f, width - 1f)
            cursorPosition.y = clamp(cursorPosition.y, 0f, height - 1f)

            scrollIfNeeded()

            invalidate()
            handler.post(this)
        }
    }

    private fun scrollIfNeeded() {
        val child = getChildAt(0) ?: return

        if (cursorPosition.y > height - scrollStartPadding && cursorSpeed.y > 0 && child.canScrollVertically(1)) {
            child.scrollBy(0, cursorSpeed.y.toInt())
        } else if (cursorPosition.y < scrollStartPadding && cursorSpeed.y < 0 && child.canScrollVertically(-1)) {
            child.scrollBy(0, cursorSpeed.y.toInt())
        }

        if (cursorPosition.x > width - scrollStartPadding && cursorSpeed.x > 0 && child.canScrollHorizontally(1)) {
            child.scrollBy(cursorSpeed.x.toInt(), 0)
        } else if (cursorPosition.x < scrollStartPadding && cursorSpeed.x < 0 && child.canScrollHorizontally(-1)) {
            child.scrollBy(cursorSpeed.x.toInt(), 0)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isCursorActive) {
            val x = cursorPosition.x
            val y = cursorPosition.y

            paint.color = Color.argb(128, 255, 255, 255)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, cursorRadius.toFloat(), paint)

            paint.color = Color.GRAY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cursorStrokeWidth
            canvas.drawCircle(x, y, cursorRadius.toFloat(), paint)
        }
    }

    private fun isCursorDisappeared(): Boolean {
        return !isCursorActive
    }

    private fun dispatchCursorClick() {
        val time = SystemClock.uptimeMillis()

        val properties = arrayOf(PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })

        val coords = arrayOf(PointerCoords().apply {
            x = cursorPosition.x
            y = cursorPosition.y
            pressure = 1f
            size = 1f
        })

        val downEvent = MotionEvent.obtain(
            time, time, MotionEvent.ACTION_DOWN, 1, properties, coords,
            0, 0, 1f, 1f, 0, 0, 0, 0
        )
        dispatchTouchEvent(downEvent)

        val upEvent = MotionEvent.obtain(
            time, time, MotionEvent.ACTION_UP, 1, properties, coords,
            0, 0, 1f, 1f, 0, 0, 0, 0
        )
        dispatchTouchEvent(upEvent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        callback?.onUserInteraction()

        val keyCode = event.keyCode
        val action = event.action

        val isDown = action == KeyEvent.ACTION_DOWN
        val isUp = action == KeyEvent.ACTION_UP

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isDown && !upButtonPressed) {
                    upButtonPressed = true
                    upButtonPressStartTime = System.currentTimeMillis()
                    if (!isCursorActive) {
                        handler.postDelayed(cursorActivationRunnable, CURSOR_ACTIVATION_TIMEOUT)
                    }
                } else if (isUp) {
                    upButtonPressed = false
                    handler.removeCallbacks(cursorActivationRunnable)
                }
                
                if (isCursorActive) {
                    handleDirectionKeyEvent(event, -100, if (isDown) -1 else 0, isDown)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isCursorActive) {
                    handleDirectionKeyEvent(event, -100, if (isDown) 1 else 0, isDown)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isCursorActive) {
                    handleDirectionKeyEvent(event, if (isDown) -1 else 0, -100, isDown)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isCursorActive) {
                    handleDirectionKeyEvent(event, if (isDown) 1 else 0, -100, isDown)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (isCursorActive && isDown) {
                    dispatchCursorClick()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            KeyEvent.KEYCODE_BACK -> {
                if (isCursorActive && isDown) {
                    isCursorActive = false
                    invalidate()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        if (isCursorActive) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun handleDirectionKeyEvent(event: KeyEvent, dx: Int, dy: Int, pressed: Boolean) {
        lastCursorUpdate = System.currentTimeMillis()

        if (!pressed) {
            keyDispatcherState.handleUpEvent(event)
            cursorSpeed.set(0f, 0f)
        } else if (!keyDispatcherState.isTracking(event)) {
            handler.removeCallbacks(cursorUpdateRunnable)
            handler.post(cursorUpdateRunnable)
            keyDispatcherState.startTracking(event, this)
        }

        val newX = if (dx == -100) cursorDirection.x else dx
        val newY = if (dy == -100) cursorDirection.y else dy
        cursorDirection.set(newX, newY)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        callback?.onUserInteraction()
        return super.onInterceptTouchEvent(ev)
    }

    private fun clamp(value: Float, max: Float): Float {
        return kotlin.math.max(-max, kotlin.math.min(max, value))
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return kotlin.math.max(min, kotlin.math.min(max, value))
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    fun showCursorAlways() {
        isCursorActive = true
        handler.removeCallbacks(cursorHideRunnable)
        invalidate()
    }
} 