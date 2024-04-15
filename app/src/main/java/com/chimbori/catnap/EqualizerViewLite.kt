package com.chimbori.catnap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class EqualizerViewLite(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
  private var mPhonon: Phonon? = null
  private var mWidth = 0
  private var mHeight = 0
  private var mBarWidth = 0f
  private var mBitmap: Bitmap? = null
  fun setPhonon(ph: Phonon) {
    if (mPhonon !== ph) {
      mPhonon = ph
      mBitmap = null
      invalidate()
    }
  }

  private fun makeBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Draw a white line
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    whitePaint.setColor(Color.WHITE)
    whitePaint.setAlpha(if (isEnabled) 250 else 94)
    whitePaint.style = Paint.Style.STROKE
    whitePaint.strokeWidth = dpToPixels(3f)
    val path = Path()
    var first = true
    for (i in 0 until BAND_COUNT) {
      val bar = if (mPhonon != null) mPhonon!!.getBar(i) else .5f
      val x = mBarWidth * (i + 0.5f)
      val y = barToY(bar)
      if (first) {
        first = false
        path.moveTo(x, y)
      } else {
        path.lineTo(x, y)
      }
    }
    canvas.drawPath(path, whitePaint)

    // Overlay the spectrum bitmap to add color.
    val colorBmp = BitmapFactory.decodeResource(resources, R.drawable.spectrum)
    val src = Rect(0, 0, colorBmp.getWidth(), colorBmp.getHeight())
    val dst = Rect(0, 0, bmp.getWidth(), bmp.getHeight())
    val alphaPaint = Paint()
    alphaPaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
    canvas.drawBitmap(colorBmp, src, dst, alphaPaint)
    return bmp
  }

  override fun onDraw(canvas: Canvas) {
    if (mBitmap == null) {
      mBitmap = makeBitmap()
    }
    canvas.drawBitmap(mBitmap!!, 0f, 0f, null)
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    mWidth = width
    mHeight = height
    mBarWidth = mWidth.toFloat() / BAND_COUNT
    mBitmap = null
  }

  private fun barToY(barHeight: Float): Float {
    return (1f - barHeight) * mHeight
  }

  private fun dpToPixels(dp: Float): Float {
    val r = resources
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP, dp, r.displayMetrics
    )
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    mBitmap = null
    invalidate()
  }

  companion object {
    private const val BAND_COUNT = SpectrumData.BAND_COUNT
  }
}
