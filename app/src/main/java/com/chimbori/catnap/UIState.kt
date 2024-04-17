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
  private val mContext = application.applicationContext

  val isLocked: LiveData<Boolean> = MutableLiveData()
  val interactedWhileLocked: LiveData<Boolean> = MutableLiveData()

  init {
    isLocked.update(false)
    interactedWhileLocked.update(false)
  }

  val mActivePos = TrackedPosition()
  var mScratchPhonon: PhononMutable? = null
  var mSavedPhonons: ArrayList<Phonon>? = null

  private var mDirty = false
  var autoPlay = false
    private set
  private var mIgnoreAudioFocus = false
  private var mVolumeLimitEnabled = false
  private var mVolumeLimit = 0

  fun saveState(pref: SharedPreferences.Editor) {
    pref.putBoolean("locked", isLocked.nonNullValue)
    pref.putBoolean("autoPlay", autoPlay)
    pref.putBoolean("ignoreAudioFocus", mIgnoreAudioFocus)
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
    volumeLimitEnabled = mVolumeLimit != MAX_VOLUME

    // Load the scratch phonon.
    mScratchPhonon = PhononMutable()
    if (mScratchPhonon!!.loadFromJSON(pref.getString("phononS", null))) {
    } else if (mScratchPhonon!!.loadFromLegacyPrefs(pref)) {
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

  fun sendToService() {
    val intent = Intent(mContext, NoiseService::class.java)
    phonon!!.writeIntent(intent)
    intent.putExtra("volumeLimit", volumeLimit.toFloat() / MAX_VOLUME)
    intent.putExtra("ignoreAudioFocus", mIgnoreAudioFocus)
    ContextCompat.startForegroundService(mContext, intent)
    mDirty = false
  }

  fun sendIfDirty(): Boolean {
    if (mDirty || mActivePos.pos == -1 && mScratchPhonon!!.isDirty) {
      sendToService()
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
    sendToService()
  }

  fun setAutoPlay(enabled: Boolean, fromUser: Boolean) {
    autoPlay = enabled
    if (fromUser) {
      // Demonstrate AutoPlay by acting like the Play/Stop button.
      if (enabled) {
        sendToService()
      } else {
        NoiseService.stopNow(mContext, R.string.stop_reason_autoplay)
      }
    }
  }

  var ignoreAudioFocus: Boolean
    get() = mIgnoreAudioFocus
    set(enabled) {
      if (mIgnoreAudioFocus == enabled) {
        return
      }
      mIgnoreAudioFocus = enabled
      mDirty = true
    }
  var volumeLimitEnabled: Boolean
    get() = mVolumeLimitEnabled
    set(enabled) {
      if (mVolumeLimitEnabled == enabled) {
        return
      }
      mVolumeLimitEnabled = enabled
      if (mVolumeLimit != MAX_VOLUME) {
        mDirty = true
      }
    }
  var volumeLimit: Int
    get() = if (mVolumeLimitEnabled) mVolumeLimit else MAX_VOLUME
    set(value) {
      var limit = value
      if (limit < 0) {
        limit = 0
      } else if (limit > MAX_VOLUME) {
        limit = MAX_VOLUME
      }
      if (mVolumeLimit == limit) {
        return
      }
      mVolumeLimit = limit
      if (mVolumeLimitEnabled) {
        mDirty = true
      }
    }

  companion object {
    const val MAX_VOLUME = 100
  }
}
