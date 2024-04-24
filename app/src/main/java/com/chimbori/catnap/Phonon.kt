package com.chimbori.catnap

import android.content.Intent
import android.content.SharedPreferences
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// A Phonon represents a collection of user-tweakable sound
// information, presented as a single row in the "Memory" view.
//
// Supported operations:
// - Convert to/from JSON for storage.
// - Efficient equality testing.
// - Convert to SpectrumData for playback.
// - Get and set sound-related values.
class Phonon {
  private val mBars = ShortArray(SpectrumData.BAND_COUNT)
  private var mMinVol = 100
  private var mPeriod = 18 // Maps to 1 second
  var isDirty = true
    private set
  private var mHash = 0
  fun resetToDefault() {
    for (i in 0 until SpectrumData.BAND_COUNT) {
      setBar(i, .5f)
    }
    mMinVol = 100
    mPeriod = 18
    cleanMe()
  }

  // Load data from <= Chroma Doze 2.2.
  fun loadFromLegacyPrefs(pref: SharedPreferences): Boolean {
    if (pref.getFloat("barHeight0", -1f) < 0) {
      return false
    }
    for (i in 0 until SpectrumData.BAND_COUNT) {
      setBar(i, pref.getFloat("barHeight$i", .5f))
    }
    minVol = pref.getInt("minVol", 100)
    period = pref.getInt("period", 18)
    cleanMe()
    return true
  }

  fun loadFromJSON(jsonString: String?): Boolean {
    if (jsonString == null) {
      return false
    }
    try {
      val j = JSONObject(jsonString)
      minVol = j.getInt("minvol")
      period = j.getInt("period")
      val jBars = j.getJSONArray("bars")
      for (i in 0 until SpectrumData.BAND_COUNT) {
        val b = jBars.getInt(i)
        if (!(0 <= b && b <= BAR_MAX)) {
          return false
        }
        mBars[i] = b.toShort()
      }
    } catch (e: JSONException) {
      return false
    }
    cleanMe()
    return true
  }

  // Storing everything as text might be useful if I ever want
  // to do an export feature.
  fun toJSON(): String? {
    return try {
      val j = JSONObject()
      val jBars = JSONArray()
      for (s in mBars) {
        jBars.put(s.toInt())
      }
      j.put("bars", jBars)
      j.put("minvol", mMinVol)
      j.put("period", mPeriod)
      j.toString()
    } catch (e: JSONException) {
      throw RuntimeException("impossible")
    }
  }

  // band: The index number of the bar.
  // value: Between 0 and 1.
  fun setBar(band: Int, value: Float) {
    // Map from 0..1 to a discrete short.
    val sval: Short
    sval = if (value <= 0f) {
      0
    } else if (value >= 1f) {
      BAR_MAX
    } else {
      (value * BAR_MAX).toInt().toShort()
    }
    if (mBars[band] != sval) {
      mBars[band] = sval
      isDirty = true
    }
  }

  fun getBar(band: Int): Float {
    return mBars[band] / BAR_MAX.toFloat()
  }

  private val allBars: FloatArray
    get() {
      val out = FloatArray(SpectrumData.BAND_COUNT)
      for (i in 0 until SpectrumData.BAND_COUNT) {
        out[i] = getBar(i)
      }
      return out
    }

  val isSilent: Boolean
    // Return true if all equalizer bars are set to zero.
    get() {
      for (i in 0 until SpectrumData.BAND_COUNT) {
        if (mBars[i] > 0) {
          return false
        }
      }
      return true
    }

  var minVol: Int
    get() = mMinVol
    // Range: [0, 100]
    set(minVol) {
      var minVol = minVol
      if (minVol < 0) {
        minVol = 0
      } else if (minVol > 100) {
        minVol = 100
      }
      if (minVol != mMinVol) {
        mMinVol = minVol
        isDirty = true
      }
    }

  val minVolText: String
    get() = "$mMinVol%"

  var period: Int
    // This gets the slider position.
    get() = mPeriod
    set(period) {
      var period = period
      if (period < 0) {
        period = 0
      } else if (period > PERIOD_MAX) {
        period = PERIOD_MAX
      }
      if (period != mPeriod) {
        mPeriod = period
        isDirty = true
      }
    }

  val periodText: String
    get() {
      // This probably isn't very i18n friendly.
      val s = periodSeconds
      return if (s >= 1f) {
        String.format(Locale.getDefault(), "%.2g sec", s)
      } else {
        String.format(Locale.getDefault(), "%d ms", Math.round(s * 1000))
      }
    }

  private val periodSeconds: Float
    get() =// This is a somewhat human-friendly mapping from
      // scroll position to seconds.
      if (mPeriod < 9) {
        // 10ms, 20ms, ..., 90ms
        (mPeriod + 1) * .010f
      } else if (mPeriod < 18) {
        // 100ms, 200ms, ..., 900ms
        (mPeriod - 9 + 1) * .100f
      } else if (mPeriod < 36) {
        // 1.0s, 1.5s, ..., 9.5s
        (mPeriod - 18 + 2) * .5f
      } else if (mPeriod < 45) {
        // 10, 11, ..., 19
        (mPeriod - 36 + 10) * 1f
      } else {
        // 20, 25, 30, ... 60
        (mPeriod - 45 + 4) * 5f
      }

  fun makeMutableCopy(): Phonon {
    check(!isDirty)
    val c = Phonon()
    System.arraycopy(mBars, 0, c.mBars, 0, SpectrumData.BAND_COUNT)
    c.mMinVol = mMinVol
    c.mPeriod = mPeriod
    c.mHash = mHash
    c.isDirty = false
    return c
  }

  // We assume that all Intents will be sent to the service,
  // so this also clears the dirty bit.
  fun writeIntent(intent: Intent?) {
    intent!!.putExtra("spectrum", SpectrumData(allBars))
    intent.putExtra("minvol", mMinVol / 100f)
    intent.putExtra("period", periodSeconds)
    cleanMe()
  }

  private fun cleanMe() {
    var h = mBars.contentHashCode()
    h = 31 * h + mMinVol
    h = 31 * h + mPeriod
    mHash = h
    isDirty = false
  }

  fun fastEquals(other: Phonon?): Boolean {
    check(!(isDirty || other!!.isDirty))
    return if (this === other) {
      true
    } else mHash == other!!.mHash && mMinVol == other.mMinVol && mPeriod == other.mPeriod && mBars.contentEquals(other.mBars)
  }

  companion object {
    const val PERIOD_MAX = 53 // Maps to 60 seconds.

    // The current value of each bar, [0, 1023]
    private const val BAR_MAX: Short = 1023
  }
}
