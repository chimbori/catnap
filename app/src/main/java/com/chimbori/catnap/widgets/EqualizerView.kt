package com.chimbori.catnap.widgets

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chimbori.catnap.BAND_COUNT
import com.chimbori.catnap.Phonon
import com.chimbori.catnap.R

class EqualizerView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
  var phonon: Phonon? = null
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

  // Return [true] to force a redraw.
  private val interactionCompleteListeners = mutableListOf<(() -> Boolean)>()
  fun addInteractionCompleteListener(listener: () -> Boolean) {
    interactionCompleteListeners.add(listener)
  }

  // L=light, M=medium, D=dark
  private val mBarColorL = arrayOfNulls<Paint>(BAND_COUNT)
  private val mBarColorM = arrayOfNulls<Paint>(BAND_COUNT)
  private val mBarColorD = arrayOfNulls<Paint>(BAND_COUNT)
  private val mBaseColorL = arrayOfNulls<Paint>(4)
  private val mBaseColorM = arrayOfNulls<Paint>(4)
  private val mBaseColorD = arrayOfNulls<Paint>(4)
  private var mWidth = 0f
  private var mHeight = 0f
  private var mBarWidth = 0f
  private var mZeroLineY = 0f
  private var mCubeTop: Path? = null
  private var mCubeSide: Path? = null
  private var mLastX = 0f
  private var mLastY = 0f

  init {
    makeColors()
  }

  private fun makeColors() {
    val bmp = BitmapFactory.decodeResource(resources, R.drawable.spectrum)
    for (i in 0 until BAND_COUNT) {
      val p = Paint()
      val x = (bmp.getWidth() - 1) * i / (BAND_COUNT - 1)
      p.setColor(bmp.getPixel(x, 0))
      mBarColorL[i] = p
    }
    darken(0.7f, mBarColorL, mBarColorM)
    darken(0.5f, mBarColorL, mBarColorD)
    var i = 0
    for (v in intArrayOf(100, 75, 55, 50)) {
      val p = Paint()
      p.setColor(Color.rgb(v, v, v))
      mBaseColorL[i++] = p
    }
    darken(0.7f, mBaseColorL, mBaseColorM)
    darken(0.5f, mBaseColorL, mBaseColorD)
  }

  private fun darken(mult: Float, src: Array<Paint?>, dst: Array<Paint?>) {
    require(src.size == dst.size) { "length mismatch" }
    for (i in src.indices) {
      val color = src[i]!!.color
      val r = (Color.red(color) * mult).toInt()
      val g = (Color.green(color) * mult).toInt()
      val b = (Color.blue(color) * mult).toInt()
      val p = Paint(src[i])
      p.setColor(Color.argb(Color.alpha(color), r, g, b))
      dst[i] = p
    }
  }

  override fun onDraw(canvas: Canvas) {
    val p = Path()
    for (i in 0 until BAND_COUNT) {
      val bar = phonon?.getBar(i) ?: .5f
      val startX = bandToX(i)
      val stopX = startX + mBarWidth
      val startY = barToY(bar)
      val midY = startY + mBarWidth

      // Lower the brightness and contrast when locked.
      val baseCol = i % 2 + if (isLocked) 2 else 0

      // Bar right (the top-left corner of this rectangle will be clipped.)
      val projX = mBarWidth * PROJECT_X
      val projY = mBarWidth * PROJECT_Y
      canvas.drawRect(stopX, midY + projY, stopX + projX, mHeight, mBaseColorD[baseCol]!!)

      // Bar front
      canvas.drawRect(startX, midY, stopX, mHeight, mBaseColorL[baseCol]!!)
      if (bar > 0) {
        // Cube right
        mCubeSide!!.offset(stopX, startY, p)
        canvas.drawPath(p, mBarColorD[i]!!)

        // Cube top
        mCubeTop!!.offset(startX, startY, p)
        canvas.drawPath(p, mBarColorM[i]!!)

        // Cube front
        canvas.drawRect(startX, startY, stopX, midY, mBarColorL[i]!!)
      } else {
        // Bar top
        mCubeTop!!.offset(startX, midY, p)
        canvas.drawPath(p, mBaseColorM[baseCol]!!)
      }
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (isLocked) {
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          interactedWhileLockedListeners.forEach { it(true) }
          return true
        }
        MotionEvent.ACTION_UP -> {
          interactedWhileLockedListeners.forEach { it(false) }
          return true
        }
        MotionEvent.ACTION_MOVE -> return true
      }
      return false
    }
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        mLastX = event.x
        mLastY = event.y
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE -> {}
      else -> return false
    }
    for (i in 0 until event.historySize) {
      touchLine(event.getHistoricalX(i), event.getHistoricalY(i))
    }
    touchLine(event.x, event.y)
    interactionCompleteListeners.forEach {
      if (it()) {
        invalidate()
      }
    }
    return true
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    mWidth = width.toFloat()
    mHeight = height.toFloat()
    mBarWidth = mWidth / (BAND_COUNT + 2)
    mZeroLineY = mHeight * .9f
    mCubeTop = projectCube(mBarWidth, true)
    mCubeSide = projectCube(mBarWidth, false)
  }

  private fun yToBar(y: Float): Float {
    val barHeight = 1f - y / (mZeroLineY - mBarWidth)
    if (barHeight < 0) {
      return 0f
    }
    return if (barHeight > 1f) {
      1f
    } else barHeight
  }

  private fun barToY(barHeight: Float): Float {
    return (1f - barHeight) * (mZeroLineY - mBarWidth)
  }

  // Accepts 0 <= barIndex < BAND_COUNT,
  // leaving a 1-bar gap on each side of the screen.
  private fun bandToX(barIndex: Int): Float {
    return mBarWidth * (barIndex + 1)
  }

  // Returns 0 <= out < BAND_COUNT,
  // leaving a 1-bar gap on each side of the screen.
  private fun xToBand(x: Float): Int {
    var out = (x / mBarWidth).toInt() - 1
    if (out < 0) {
      out = 0
    }
    if (out > BAND_COUNT - 1) {
      out = BAND_COUNT - 1
    }
    return out
  }

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
    val startX = mLastX
    val startY = mLastY
    mLastX = stopX
    mLastY = stopY
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

  companion object {
    // 3D projection offsets (multiple of mBarWidth)
    private const val PROJECT_X = 0.4f
    private const val PROJECT_Y = -0.25f

    // Draw the top or right side of a cube.
    private fun projectCube(unit: Float, isTop: Boolean): Path {
      val projX = unit * PROJECT_X
      val projY = unit * PROJECT_Y
      val p = Path()
      p.moveTo(0f, 0f)
      p.lineTo(projX, projY)
      if (isTop) {
        // Top
        p.lineTo(unit + projX, projY)
        p.lineTo(unit, 0f)
      } else {
        // Side
        p.lineTo(projX, unit + projY)
        p.lineTo(0f, unit)
      }
      p.close()
      return p
    }
  }
}
