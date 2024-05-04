package com.chimbori.catnap

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.chimbori.catnap.NoiseService.Companion.startNoiseService
import com.chimbori.catnap.NoiseService.Companion.stopNoiseService
import com.chimbori.catnap.utils.nonNullValue
import com.chimbori.catnap.utils.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UIState(application: Application) : AndroidViewModel(application) {
  private val context = application.applicationContext

  private var presets = Presets()

  var ignoreAudioFocus: Boolean = false

  var volumeLimitEnabled: Boolean = false

  var activePhonon = Phonon()
    private set(value) {
      field = value
    }

  val phonons: LiveData<List<Phonon>> = MutableLiveData()
  val isLocked: LiveData<Boolean> = MutableLiveData()
  val interactedWhileLocked: LiveData<Boolean> = MutableLiveData()
  val minimumVolume: LiveData<Int> = MutableLiveData()
  val periodSeekBarEnabled: LiveData<Boolean> = MutableLiveData()

  init {
    isLocked.update(false)
    interactedWhileLocked.update(false)
  }

  var autoPlay = false
    private set

  fun setAutoPlay(enabled: Boolean) {
    autoPlay = enabled
    if (enabled) {
      startService()  // Demonstrate AutoPlay by acting like the Play/Stop button.
    } else {
      context.stopNoiseService(R.string.stop_reason_autoplay)
    }
  }

  fun saveState(pref: SharedPreferences.Editor) {
    pref.putString("presets", Json.encodeToString(presets))
  }

  fun loadState(pref: SharedPreferences) {
    try {
      Json.decodeFromString<Presets>(pref.getString("presets", "") ?: "").let { nonNullPresets ->
        presets = nonNullPresets
      }
    } catch (e: SerializationException) {
      presets = Presets()
    }

    // TODO: Update LiveData

    isLocked.update(presets.locked)
    setAutoPlay(presets.autoPlay)
    ignoreAudioFocus = presets.ignoreAudioFocus
    volumeLimit = presets.volumeLimit
    volumeLimitEnabled = volumeLimit != MAX_VOLUME
  }

  fun startService() {
    context.startNoiseService(activePhonon, volumeLimit.toFloat() / MAX_VOLUME, ignoreAudioFocus)
  }

  fun restartServiceIfRequired(): Boolean {
    // TODO: Check if needed to restart.
    startService()
    return true
  }

  fun toggleLocked() {
    isLocked.update(!isLocked.nonNullValue)
    if (!isLocked.nonNullValue) {
      interactedWhileLocked.update(false)
    }
  }

  fun setInteractedWhileLocked(interacted: Boolean) {
    if (interacted) {
      check(isLocked.nonNullValue) { "Expected isLocked" }
    }
    if (interactedWhileLocked.nonNullValue != interacted) {
      interactedWhileLocked.update(interacted)
    }
  }

  fun setPeriod(period: Int) {
    activePhonon = activePhonon.copy(period = period)
  }

  fun setMinimumVolume(value: Int) {
    activePhonon = activePhonon.copy(minimumVolume = value)
    minimumVolume.update(value)
    periodSeekBarEnabled.update(value != 100)
    restartServiceIfRequired()
  }

  fun savePhonon(phonon: Phonon) {
    presets = presets.copy(
      phonons = presets.phonons.toMutableList().apply {
        add(phonon)
      }
    )
  }

  var volumeLimit: Int = MAX_VOLUME
    get() = if (volumeLimitEnabled) field else MAX_VOLUME
    set(value) {
      field = value.coerceIn(0, MAX_VOLUME)
    }

  companion object {
    const val MAX_VOLUME = 100

    /** The name to use when accessing our SharedPreferences. */
    const val PREF_NAME = "app"
  }
}
