package com.chimbori.catnap.audio

import android.os.Parcel
import android.os.Parcelable
import com.chimbori.catnap.BAND_COUNT
import kotlin.math.min
import kotlin.math.pow

/**
 * SpectrumData is a Phonon translated into "machine readable" form. In other
 * words, the values here are suitable for generating noise, and not for
 * storage or rendering UI elements.
 */
class SpectrumData : Parcelable {
  private val data = mutableListOf<Float>()

  constructor(donateBars: List<Float>) {
    if (donateBars.size != BAND_COUNT) {
      throw RuntimeException("Incorrect number of bands: ${donateBars.size}")
    }
    data.clear()
    data.addAll(donateBars.map {
      0.001f * 1000.toDouble().pow(it.toDouble()).toFloat().coerceAtLeast(0f)
    })
  }

  private constructor(input: Parcel) {
    @Suppress("DEPRECATION")
    input.readList(data, Float::class.java.classLoader)
  }

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeList(data)
  }

  fun fill(out: FloatArray, sampleRate: Int) {
    val maxFreq = sampleRate / 2
    subFill(out, 0f, 0, EDGE_FREQS[0], maxFreq)
    for (i in 0 until BAND_COUNT) {
      subFill(out, data[i], EDGE_FREQS[i], EDGE_FREQS[i + 1], maxFreq)
    }
    subFill(out, 0f, EDGE_FREQS[BAND_COUNT], maxFreq, maxFreq)
  }

  private fun subFill(out: FloatArray, setValue: Float, startFreq: Int, limitFreq: Int, maxFreq: Int) {
    // This min() applies if the sample rate is below 40kHz.
    val limitIndex = min(out.size.toDouble(), (limitFreq * out.size / maxFreq).toDouble()).toInt()
    for (i in startFreq * out.size / maxFreq until limitIndex) {
      out[i] = setValue
    }
  }

  fun sameSpectrum(other: SpectrumData?): Boolean {
    if (other == null) {
      return false
    }
    for (i in 0 until BAND_COUNT) {
      if (data[i] != other.data[i]) {
        return false
      }
    }
    return true
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<SpectrumData> = object : Parcelable.Creator<SpectrumData> {
      override fun createFromParcel(input: Parcel) = SpectrumData(input)
      override fun newArray(size: Int): Array<SpectrumData?> = arrayOfNulls(size)
    }

    private const val MIN_FREQ = 100f
    private const val MAX_FREQ = 20000f

    /** The frequency of the edges between each bar. */
    private val EDGE_FREQS = calculateEdgeFreqs()

    private fun calculateEdgeFreqs(): IntArray {
      val edgeFreqs = IntArray(BAND_COUNT + 1)
      val range = MAX_FREQ / MIN_FREQ
      for (i in 0..BAND_COUNT) {
        edgeFreqs[i] = (MIN_FREQ * range.toDouble().pow((i.toFloat() / BAND_COUNT).toDouble())).toInt()
      }
      return edgeFreqs
    }
  }
}
