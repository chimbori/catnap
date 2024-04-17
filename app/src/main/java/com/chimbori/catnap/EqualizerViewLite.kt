package com.chimbori.catnap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color.WHITE
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.Paint.Style.STROKE
import android.graphics.Path
import android.graphics.PorterDuff.Mode.SRC_IN
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.View

class EqualizerViewLite(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
  private var barWidth = 0f
  private var canvasBitmap: Bitmap? = null

  var phonon: Phonon? = null
    set(value) {
      if (field != value) {
        field = value
      }
      canvasBitmap = null
      invalidate()
    }

  private fun createBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw a white line
    val whitePaint = Paint(ANTI_ALIAS_FLAG).apply {
      setColor(WHITE)
      setAlpha(if (isEnabled) 250 else 94)
      style = STROKE
      strokeWidth = dpToPixels(3f)
    }

    val path = Path()
    var first = true
    for (i in 0 until BAND_COUNT) {
      val bar = phonon?.getBar(i) ?: .5f
      val x = barWidth * (i + 0.5f)
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
    val spectrumOverlay = BitmapFactory.decodeResource(resources, R.drawable.spectrum)
    val src = Rect(0, 0, spectrumOverlay.getWidth(), spectrumOverlay.getHeight())
    val dst = Rect(0, 0, bitmap.getWidth(), bitmap.getHeight())

    val alphaPaint = Paint()
    alphaPaint.setXfermode(PorterDuffXfermode(SRC_IN))
    canvas.drawBitmap(spectrumOverlay, src, dst, alphaPaint)

    return bitmap
  }

  override fun onDraw(canvas: Canvas) {
    if (canvasBitmap == null) {
      canvasBitmap = createBitmap()
    }
    canvas.drawBitmap(canvasBitmap!!, 0f, 0f, null)
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    barWidth = width.toFloat() / BAND_COUNT
    canvasBitmap = null
  }

  private fun barToY(barHeight: Float) = (1f - barHeight) * height

  private fun dpToPixels(dp: Float) = TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    canvasBitmap = null
    invalidate()
  }

  companion object {
    private const val BAND_COUNT = SpectrumData.BAND_COUNT
  }
}
