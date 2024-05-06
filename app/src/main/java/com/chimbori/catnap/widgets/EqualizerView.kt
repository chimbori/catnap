package com.chimbori.catnap.widgets

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.View
import com.chimbori.catnap.BAND_COUNT
import com.chimbori.catnap.Preset
import com.chimbori.catnap.R

class EqualizerView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
  private val circleColorsLight = mutableListOf<Paint>()
  private val circleColorsDark = mutableListOf<Paint>()

  // TODO: Replace with Material You Colors
  private val lineColorLight = Paint().apply { setColor(Color.argb(100, 100, 100, 100)) }
  private val lineColorDark = Paint().apply { setColor(Color.argb(20, 100, 100, 100)) }

  var preset: Preset? = null
    set(value) {
      field = value
      invalidate()
    }

  var isLocked: Boolean = false
    set(value) {
      field = value
      invalidate()
    }

  private val barChangedListeners = mutableListOf<(band: Int, value: Float) -> Unit>()
  fun addBarChangedListener(listener: (band: Int, value: Float) -> Unit) {
    barChangedListeners.add(listener)
  }

  private val interactedWhileLockedListeners = mutableListOf<(interacted: Boolean) -> Unit>()
  fun addInteractedWhileLockedListener(listener: (interacted: Boolean) -> Unit) {
    interactedWhileLockedListeners.add(listener)
  }

  private val interactionCompleteListeners = mutableListOf<(() -> Unit)>()
  fun addInteractionCompleteListener(listener: () -> Unit) {
    interactionCompleteListeners.add(listener)
  }

  private var canvasHeight = 0f
  private var barWidth = 0f
  private var lineWidthPx = dpToPixels(1f)
  private var zeroLineY = 0f

  private var lastX = 0f
  private var lastY = 0f

  init {
    initColors()
  }

  private fun initColors() {
    val spectrumBitmap = BitmapFactory.decodeResource(resources, R.drawable.spectrum)
    for (i in 0 until BAND_COUNT) {
      circleColorsLight.add(Paint().apply {
        val x = (spectrumBitmap.getWidth() - 1) * i / BAND_COUNT
        setColor(spectrumBitmap.getPixel(x, 0))
      })
    }
    circleColorsDark.addAll(circleColorsLight.darken(0.5f))
  }

  override fun onDraw(canvas: Canvas) {
    for (i in 0 until BAND_COUNT) {
      val bar = preset?.getBar(i) ?: .5f
      val startX = bandToX(i)
      val stopX = startX + barWidth
      val startY = barToY(bar)
      val midY = startY + barWidth

      val lineOffsetWithinBarX = (barWidth - lineWidthPx) / 2

      canvas.drawRect(
        startX + lineOffsetWithinBarX, midY, stopX - lineOffsetWithinBarX, canvasHeight,
        if (isLocked) lineColorDark else lineColorLight
      )
      canvas.drawRoundRect(
        startX, startY, stopX, midY,
        barWidth, barWidth,
        if (isLocked) circleColorsDark[i] else circleColorsLight[i]
      )
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (isLocked) {
      return when (event.action) {
        ACTION_DOWN -> {
          interactedWhileLockedListeners.forEach { it(true) }
          true
        }
        ACTION_UP -> {
          interactedWhileLockedListeners.forEach { it(false) }
          true
        }
        ACTION_MOVE -> true
        else -> false
      }
    }
    when (event.action) {
      ACTION_DOWN -> {
        lastX = event.x
        lastY = event.y
      }
      ACTION_UP, ACTION_MOVE -> {}
      else -> return false
    }
    for (i in 0 until event.historySize) {
      touchLine(event.getHistoricalX(i), event.getHistoricalY(i))
    }
    touchLine(event.x, event.y)
    invalidate()
    interactionCompleteListeners.forEach { it() }
    return true
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    canvasHeight = height.toFloat()
    barWidth = width.toFloat() / BAND_COUNT
    zeroLineY = canvasHeight * .9f
  }

  private fun yToBar(y: Float) = (1f - y / (zeroLineY - barWidth)).coerceIn(0f, 1f)

  private fun barToY(barHeight: Float) = (1f - barHeight) * (zeroLineY - barWidth)

  private fun bandToX(barIndex: Int) = barWidth * barIndex

  private fun xToBand(x: Float) = (x / barWidth).toInt().coerceIn(0, BAND_COUNT - 1)

  // Starting bar?
  // Ending bar?
  // For each bar it exits:
  //   set Y to exit-Y.
  // For the ending point:
  //   set Y to final-Y.
  // Exits:
  //   Right:
  //     0->3: 0, 1, 2 [endpoint in 3]
  //   Left:
  //     3->0: 3, 2, 1 [endpoint in 0]
  private fun touchLine(stopX: Float, stopY: Float) {
    val startX = lastX
    val startY = lastY
    lastX = stopX
    lastY = stopY
    val startBand = xToBand(startX)
    val stopBand = xToBand(stopX)
    val direction = if (stopBand > startBand) 1 else -1
    var i = startBand
    while (i != stopBand) {
      // Get the x-coordinate where we exited band i.
      val exitX = bandToX(if (direction < 0) i else i + 1)

      // Get the Y value at exitX.
      val slope = (stopY - startY) / (stopX - startX)
      val exitY = startY + slope * (exitX - startX)
      barChangedListeners.forEach { it(i, yToBar(exitY)) }
      i += direction
    }
    // Set the Y endpoint.
    barChangedListeners.forEach { it(stopBand, yToBar(stopY)) }
  }

  private fun dpToPixels(dp: Float) = TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}

private fun List<Paint>.darken(multiplier: Float) = map {
  val color = it.color
  val r = (Color.red(color) * multiplier).toInt()
  val g = (Color.green(color) * multiplier).toInt()
  val b = (Color.blue(color) * multiplier).toInt()
  Paint(it).apply { setColor(Color.argb(Color.alpha(color), r, g, b)) }
}
