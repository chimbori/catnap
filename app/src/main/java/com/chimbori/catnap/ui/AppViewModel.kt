package com.chimbori.catnap.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.chimbori.catnap.AppConfig
import com.chimbori.catnap.MAX_VOLUME
import com.chimbori.catnap.NoiseService
import com.chimbori.catnap.NoiseService.Companion.startNoiseService
import com.chimbori.catnap.NoiseService.Companion.stopNoiseService
import com.chimbori.catnap.Preset
import com.chimbori.catnap.R
import com.chimbori.catnap.utils.nonNullValue
import com.chimbori.catnap.utils.update
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppViewModel(application: Application) : AndroidViewModel(application) {
  @SuppressLint("StaticFieldLeak")
  private val context = application.applicationContext

  private val prefs by lazy { context.getSharedPreferences(PREF_NAME, MODE_PRIVATE) }

  private var _presets = mutableListOf<Preset>()
  val presets: LiveData<List<Preset>> = MutableLiveData()

  /**
   * Maintains temporary state, especially when user is editing it in the Equalizer. When user stops editing,
   * this should be persisted to storage as part of [AppConfig].
   */
  private var _activePreset = Preset()
  val activePreset: LiveData<Preset> = MutableLiveData()

  val isLocked: LiveData<Boolean> = MutableLiveData()
  val interactedWhileLocked: LiveData<Boolean> = MutableLiveData()

  val autoPlay: LiveData<Boolean> = MutableLiveData()
  val ignoreAudioFocus: LiveData<Boolean> = MutableLiveData()
  val volumeLimit: LiveData<Int> = MutableLiveData()
  val volumeLimitEnabled: LiveData<Boolean> = MutableLiveData()

  val playStopIconResId: LiveData<Int> = MutableLiveData()
  val statusText: LiveData<String?> = MutableLiveData()
  val statusPercent: LiveData<Int> = MutableLiveData()

  private var isServiceActive = false

  private val noisePercentListener = NoiseService.PercentListener { percent, stopTimestamp, stopReasonId ->
    isServiceActive = percent >= 0

    when {
      percent < 0 -> {  // STOPPED
        // Expire the message after 12 hours, to avoid date ambiguity.
        if (Date().time - stopTimestamp.time <= 12 * 3600 * 1000L) {
          val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT).format(stopTimestamp)
          if (stopReasonId != 0) {
            statusText.update("$timeFmt: ${context.getString(stopReasonId)}")
            statusPercent.update(-1)
          }
        }
      }
      percent < 100 -> {  // GENERATING
        statusText.update(context.getString(R.string.generating))
        statusPercent.update(percent)
      }
      else -> {  // ACTIVE
        // While the service is active, only the restart event is worth showing.
        statusText.update(null)
        statusPercent.update(percent)
      }
    }
    playStopIconResId.update(if (isServiceActive) R.drawable.stop else R.drawable.play)
  }

  init {
    playStopIconResId.update(R.drawable.play)
    statusText.update(null)
    statusPercent.update(-1)
    loadOrCreateState()
    NoiseService.addPercentListener(noisePercentListener)
  }

  override fun onCleared() {
    super.onCleared()
    NoiseService.removePercentListener(noisePercentListener)
  }

  private fun loadOrCreateState() {
    val appConfig = try {
      Json.decodeFromString<AppConfig>(prefs.getString(PREF_KEY, "") ?: "")
    } catch (e: SerializationException) {
      AppConfig()
    }

    _presets = appConfig.presets.toMutableList()
    presets.update(_presets)

    _activePreset = appConfig.activePreset
    activePreset.update(_activePreset)

    isLocked.update(appConfig.locked)
    interactedWhileLocked.update(false)
    autoPlay.update(appConfig.autoPlay)
    ignoreAudioFocus.update(appConfig.ignoreAudioFocus)
    volumeLimit.update(appConfig.volumeLimit)
    volumeLimitEnabled.update(appConfig.volumeLimit != MAX_VOLUME)
  }

  fun saveState() {
    prefs.edit {
      putString(
        PREF_KEY, Json.encodeToString(
          AppConfig(
            presets = _presets,
            activePreset = _activePreset,
            locked = isLocked.nonNullValue,
            autoPlay = autoPlay.nonNullValue,
            ignoreAudioFocus = ignoreAudioFocus.nonNullValue,
            volumeLimit = volumeLimit.nonNullValue,
          )
        )
      )
    }
  }

  private fun startService() {
    context.startNoiseService(
      preset = _activePreset,
      volumeLimit = volumeLimit.nonNullValue.toFloat() / MAX_VOLUME,
      ignoreAudioFocus = ignoreAudioFocus.nonNullValue
    )
  }

  private fun restartServiceIfRequired(): Boolean {
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

  fun togglePlayStop() {
    if (!isServiceActive) {
      startService()
    } else {
      context?.stopNoiseService(R.string.stop_reason_toolbar)
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

  fun setBar(band: Int, value: Float) {
    _activePreset.setBar(band, value)  // Update the in-memory Preset, but don’t notify observers yet.
  }

  fun setPresetEditComplete(): Boolean {
    activePreset.update(_activePreset)  // Notify observers once user interaction is complete.
    restartServiceIfRequired()
    return true
  }

  fun setMinimumVolume(minimumVolume: Int) {
    _activePreset = _activePreset.copy(minimumVolume = minimumVolume)
    activePreset.update(_activePreset)
    restartServiceIfRequired()
  }

  fun setPeriod(period: Int) {
    _activePreset = _activePreset.copy(period = period)
    activePreset.update(_activePreset)
    restartServiceIfRequired()
  }

  fun savePreset(name: String) {
    _presets.add(
      // To prevent further edits from being persisted incorrectly.
      _activePreset.copy(name = name).deepCopy()
    )
    presets.update(_presets)
    saveState()
  }

  fun setAutoPlay(enabled: Boolean) {
    autoPlay.update(enabled)
    if (enabled) {
      startService()  // Demonstrate AutoPlay by acting like the Play/Stop button.
    } else {
      context.stopNoiseService(R.string.stop_reason_autoplay)
    }
  }

  fun setIgnoreAudioFocus(value: Boolean) {
    ignoreAudioFocus.update(value)
    restartServiceIfRequired()
  }

  fun setVolumeLimit(value: Int) {
    volumeLimit.update(if (!volumeLimitEnabled.nonNullValue) MAX_VOLUME else value.coerceIn(0, MAX_VOLUME))
    restartServiceIfRequired()
  }

  fun setVolumeLimitEnabled(checked: Boolean) {
    volumeLimitEnabled.update(checked)
  }

  fun getNextPresetName() = "${context.getString(R.string.preset)} #${presets.nonNullValue.size + 1}"

  companion object {
    /** The name to use when accessing our SharedPreferences. */
    const val PREF_NAME = "app"

    private const val PREF_KEY = "app_config"
  }
}
