package com.chimbori.catnap

import android.os.Parcel
import android.os.Parcelable
import kotlin.math.min
import kotlin.math.pow

// SpectrumData is a Phonon translated into "machine readable" form.
//
// In other words, the values here are suitable for generating noise,
// and not for storage or rendering UI elements.
class SpectrumData : Parcelable {
  private val mData: FloatArray

  constructor(donateBars: FloatArray) {
    if (donateBars.size != BAND_COUNT) {
      throw RuntimeException("Incorrect number of bands")
    }
    mData = donateBars
    for (i in 0 until BAND_COUNT) {
      if (mData[i] <= 0f) {
        mData[i] = 0f
      } else {
        mData[i] = 0.001f * 1000.toDouble().pow(mData[i].toDouble()).toFloat()
      }
    }
  }

  private constructor(`in`: Parcel) {
    mData = FloatArray(BAND_COUNT)
    `in`.readFloatArray(mData)
  }

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeFloatArray(mData)
  }

  fun fill(out: FloatArray, sampleRate: Int) {
    val maxFreq = sampleRate / 2
    subFill(out, 0f, 0, EDGE_FREQS[0], maxFreq)
    for (i in 0 until BAND_COUNT) {
      subFill(out, mData[i], EDGE_FREQS[i], EDGE_FREQS[i + 1], maxFreq)
    }
    subFill(out, 0f, EDGE_FREQS[BAND_COUNT], maxFreq, maxFreq)
  }

  private fun subFill(out: FloatArray, setValue: Float, startFreq: Int, limitFreq: Int, maxFreq: Int) {
    // This min() applies if the sample rate is below 40kHz.
    val limitIndex = min(out.size, limitFreq * out.size / maxFreq)
    for (i in startFreq * out.size / maxFreq until limitIndex) {
      out[i] = setValue
    }
  }

  fun sameSpectrum(other: SpectrumData?): Boolean {
    if (other == null) {
      return false
    }
    for (i in 0 until BAND_COUNT) {
      if (mData[i] != other.mData[i]) {
        return false
      }
    }
    return true
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<SpectrumData> = object : Parcelable.Creator<SpectrumData> {
      override fun createFromParcel(`in`: Parcel): SpectrumData {
        return SpectrumData(`in`)
      }

      override fun newArray(size: Int): Array<SpectrumData?> {
        return arrayOfNulls(size)
      }
    }
    private const val MIN_FREQ = 100f
    private const val MAX_FREQ = 20000f

    // The frequency of the edges between each bar.
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
