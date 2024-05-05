package com.chimbori.catnap

import java.util.Locale
import kotlin.math.round
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The entire saved state of this app, persisted as JSON. */
@Serializable
data class AppConfig(
  @SerialName("phonons") val phonons: List<Phonon> = mutableListOf(),
  @SerialName("active_phonon") val activePhonon: Phonon = Phonon(),
  @SerialName("locked") val locked: Boolean = false,
  @SerialName("auto_play") val autoPlay: Boolean = false,
  @SerialName("ignore_audio_focus") val ignoreAudioFocus: Boolean = false,
  @SerialName("volume_limit") val volumeLimit: Int = MAX_VOLUME,
)

/**
 * Represents a collection of user-tweakable sound information, presented as a single row in the Presets view.
 * Supported operations:
 * - Convert to/from JSON for storage.
 * - Efficient equality testing.
 * - Convert to SpectrumData for playback.
 * - Get and set sound-related values.
 */
@Serializable
data class Phonon(
  @SerialName("bars") val bars: MutableList<Float> = mutableListOf(),
  @SerialName("minimum_volume") val minimumVolume: Int = MAX_VOLUME,
  @SerialName("period") val period: Int = PERIOD_DEFAULT,
) {
  init {
    if (bars.isEmpty()) {
      repeat(BAND_COUNT) { bars.add(0.5f) }
    }
  }

  fun deepCopy() = Json.decodeFromString<Phonon>(Json.encodeToString(this))

  fun setBar(band: Int, value: Float) {
    bars[band] = value.coerceIn(0f, 1f)
  }

  fun getBar(band: Int) = bars[band]

  // This probably isn't very i18n friendly.
  val periodText: String
    get() = if (periodSeconds >= 1f) {
      String.format(Locale.getDefault(), "%.2g sec", periodSeconds)
    } else {
      String.format(Locale.getDefault(), "%d ms", round(periodSeconds * 1000).toInt())
    }

  /** This is a somewhat human-friendly mapping from scroll position to seconds. */
  val periodSeconds: Float
    get() = when {
      period < 9 -> (period + 1) * .010f       // 10ms, 20ms, ..., 90ms
      period < 18 -> (period - 9 + 1) * .100f  // 100ms, 200ms, ..., 900ms
      period < 36 -> (period - 18 + 2) * .5f   // 1.0s, 1.5s, ..., 9.5s
      period < 45 -> (period - 36 + 10) * 1f   // 10, 11, ..., 19
      else -> (period - 45 + 4) * 5f           // 20, 25, 30, ... 60
    }
}

fun Int.asPercent() = "$this%"

/** Maps to 1 second */
const val PERIOD_DEFAULT = 18

/** Maps to 60 seconds. */
const val PERIOD_MAX = 53

const val BAND_COUNT = 32

const val MAX_VOLUME = 100
