package com.chimbori.catnap

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import android.media.AudioManager.OnAudioFocusChangeListener
import com.chimbori.catnap.NoiseService.Companion.stopNoiseService
import com.chimbori.catnap.SampleShuffler.VolumeListener
import com.chimbori.catnap.SampleShuffler.VolumeListener.DuckLevel.DUCK
import com.chimbori.catnap.SampleShuffler.VolumeListener.DuckLevel.NORMAL
import com.chimbori.catnap.SampleShuffler.VolumeListener.DuckLevel.SILENT

// This file keeps track of AudioFocus events.
// http://developer.android.com/training/managing-audio/audio-focus.html
internal class AudioFocusHelper(private val context: Context, private val volumeListener: VolumeListener) :
  OnAudioFocusChangeListener {

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private var isActive = false

  private var request: AudioFocusRequest = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
    .setAudioAttributes(AudioParams.makeAudioAttributes())
    .setOnAudioFocusChangeListener(this)
    .build()

  fun setActive(active: Boolean) {
    if (isActive == active) {
      return
    }
    if (active) {
      audioManager.requestAudioFocus(request)
    } else {
      audioManager.abandonAudioFocusRequest(request)
    }
    isActive = active
  }

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      // For example, a music player or a sleep timer stealing focus.
      AUDIOFOCUS_LOSS -> context.stopNoiseService(R.string.stop_reason_audiofocus)
      // For example, an alarm or phone call.
      AUDIOFOCUS_LOSS_TRANSIENT -> volumeListener.setDuckLevel(SILENT)
      // For example, an email notification.
      AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> volumeListener.setDuckLevel(DUCK)
      // Resume the default volume level.
      AUDIOFOCUS_GAIN -> volumeListener.setDuckLevel(NORMAL)
    }
  }
}
