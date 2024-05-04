package com.chimbori.catnap

import android.content.Intent
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
  private val mBars = ShortArray(BAND_COUNT)

  var isDirty = true
    private set

  private var mHash = 0

  fun resetToDefault() {
    for (i in 0 until BAND_COUNT) {
      setBar(i, .5f)
    }
    minVol = 100
    period = 18
    cleanMe()
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
      for (i in 0 until BAND_COUNT) {
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
      j.put("minvol", minVol)
      j.put("period", period)
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

  private val allBars: List<Float>
    get() {
      val out = mutableListOf<Float>()
      for (i in 0 until BAND_COUNT) {
        out.add(getBar(i))
      }
      return out
    }

  var minVol: Int = 100
    set(value) {
      val minVol = value.coerceIn(0, 100)
      if (minVol != field) {
        field = minVol
        isDirty = true
      }
    }

  var period: Int = 18 // Maps to 1 second
    set(value) {
      val period = value.coerceIn(0, PERIOD_MAX)
      if (period != field) {
        field = period
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
      if (period < 9) {
        // 10ms, 20ms, ..., 90ms
        (period + 1) * .010f
      } else if (period < 18) {
        // 100ms, 200ms, ..., 900ms
        (period - 9 + 1) * .100f
      } else if (period < 36) {
        // 1.0s, 1.5s, ..., 9.5s
        (period - 18 + 2) * .5f
      } else if (period < 45) {
        // 10, 11, ..., 19
        (period - 36 + 10) * 1f
      } else {
        // 20, 25, 30, ... 60
        (period - 45 + 4) * 5f
      }

  fun makeMutableCopy(): Phonon {
    check(!isDirty)
    val c = Phonon()
    System.arraycopy(mBars, 0, c.mBars, 0, BAND_COUNT)
    c.minVol = minVol
    c.period = period
    c.mHash = mHash
    c.isDirty = false
    return c
  }

  // We assume that all Intents will be sent to the service,
  // so this also clears the dirty bit.
  fun writeIntent(intent: Intent?) {
    intent!!.putExtra("spectrum", SpectrumData(allBars))
    intent.putExtra("minvol", minVol / 100f)
    intent.putExtra("period", periodSeconds)
    cleanMe()
  }

  private fun cleanMe() {
    var h = mBars.contentHashCode()
    h = 31 * h + minVol
    h = 31 * h + period
    mHash = h
    isDirty = false
  }

  fun fastEquals(other: Phonon?): Boolean {
    check(!(isDirty || other!!.isDirty))
    return if (this === other) {
      true
    } else mHash == other!!.mHash && minVol == other.minVol && period == other.period && mBars.contentEquals(
      other.mBars
    )
  }

  companion object {
    const val PERIOD_MAX = 53 // Maps to 60 seconds.

    // The current value of each bar, [0, 1023]
    private const val BAR_MAX: Short = 1023
  }
}

fun Int.asPercent() = "$this%"

const val BAND_COUNT = 32
