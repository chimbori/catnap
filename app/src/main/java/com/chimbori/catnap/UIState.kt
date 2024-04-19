package com.chimbori.catnap

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.chimbori.catnap.utils.nonNullValue
import com.chimbori.catnap.utils.update

class UIState(application: Application) : AndroidViewModel(application) {
  private val context = application.applicationContext

  val isLocked: LiveData<Boolean> = MutableLiveData()
  val interactedWhileLocked: LiveData<Boolean> = MutableLiveData()

  init {
    isLocked.update(false)
    interactedWhileLocked.update(false)
  }

  val mActivePos = TrackedPosition()
  var mScratchPhonon: PhononMutable? = null
  var mSavedPhonons: ArrayList<Phonon>? = null

  private var isDirty = false

  var autoPlay = false
    private set

  fun setAutoPlay(enabled: Boolean, fromUser: Boolean) {
    autoPlay = enabled
    if (fromUser) {
      // Demonstrate AutoPlay by acting like the Play/Stop button.
      if (enabled) {
        startService()
      } else {
        NoiseService.stopService(context, R.string.stop_reason_autoplay)
      }
    }
  }

  fun saveState(pref: SharedPreferences.Editor) {
    pref.putBoolean("locked", isLocked.nonNullValue)
    pref.putBoolean("autoPlay", autoPlay)
    pref.putBoolean("ignoreAudioFocus", ignoreAudioFocus)
    pref.putInt("volumeLimit", volumeLimit)
    pref.putString("phononS", mScratchPhonon!!.toJSON())
    for (i in mSavedPhonons!!.indices) {
      pref.putString("phonon$i", mSavedPhonons!![i].toJSON())
      mSavedPhonons!![i]
    }
    pref.putInt("activePhonon", mActivePos.pos)
  }

  fun loadState(pref: SharedPreferences) {
    isLocked.update(pref.getBoolean("locked", false))
    setAutoPlay(pref.getBoolean("autoPlay", false), false)
    ignoreAudioFocus = pref.getBoolean("ignoreAudioFocus", false)
    volumeLimit = pref.getInt("volumeLimit", MAX_VOLUME)
    volumeLimitEnabled = volumeLimit != MAX_VOLUME

    // Load the scratch phonon.
    mScratchPhonon = PhononMutable()
    if (mScratchPhonon!!.loadFromJSON(pref.getString("phononS", null))) {
      //
    } else if (mScratchPhonon!!.loadFromLegacyPrefs(pref)) {
      //
    } else {
      mScratchPhonon!!.resetToDefault()
    }

    // Load the saved phonons.
    mSavedPhonons = ArrayList()
    for (i in 0 until TrackedPosition.NOWHERE) {
      val phm = PhononMutable()
      if (!phm.loadFromJSON(pref.getString("phonon$i", null))) {
        break
      }
      mSavedPhonons!!.add(phm)
    }

    // Load the currently-selected phonon.
    val active = pref.getInt("activePhonon", -1)
    mActivePos.pos = if (-1 <= active && active < mSavedPhonons!!.size) active else -1
  }

  fun startService() {
    ContextCompat.startForegroundService(context, Intent(context, NoiseService::class.java).apply {
      phonon!!.writeIntent(this)
      putExtra("volumeLimit", volumeLimit.toFloat() / MAX_VOLUME)
      putExtra("ignoreAudioFocus", ignoreAudioFocus)
    })
    isDirty = false
  }

  fun restartServiceIfRequired(): Boolean {
    if (isDirty || mActivePos.pos == -1 && mScratchPhonon!!.isDirty) {
      startService()
      return true
    }
    return false
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

  val phonon: Phonon?
    get() = if (mActivePos.pos == -1) {
      mScratchPhonon
    } else mSavedPhonons!![mActivePos.pos]

  val phononMutable: PhononMutable?
    get() {
      if (mActivePos.pos != -1) {
        mScratchPhonon = mSavedPhonons!![mActivePos.pos].makeMutableCopy()
        mActivePos.pos = -1
      }
      return mScratchPhonon
    }

  // -1 or 0..n
  fun setActivePhonon(index: Int) {
    if (!(-1 <= index && index < mSavedPhonons!!.size)) {
      throw ArrayIndexOutOfBoundsException()
    }
    mActivePos.pos = index
    startService()
  }

  var ignoreAudioFocus: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        isDirty = true
      }
    }

  var volumeLimitEnabled: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        if (volumeLimit != MAX_VOLUME) {
          isDirty = true
        }
      }
    }

  var volumeLimit: Int = MAX_VOLUME
    get() = if (volumeLimitEnabled) field else MAX_VOLUME
    set(value) {
      val limit = value.coerceIn(0, MAX_VOLUME)
      if (field != limit) {
        field = limit
        if (volumeLimitEnabled) {
          isDirty = true
        }
      }
    }

  companion object {
    const val MAX_VOLUME = 100
  }
}
