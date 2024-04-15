package com.chimbori.catnap

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import com.chimbori.catnap.NoiseService.Companion.stopNow
import com.chimbori.catnap.SampleShuffler.VolumeListener

// This file keeps track of AudioFocus events.
// http://developer.android.com/training/managing-audio/audio-focus.html
internal class AudioFocusHelper(private val mContext: Context, private val mVolumeListener: VolumeListener) : OnAudioFocusChangeListener {
    private val mAudioManager: AudioManager
    private var mActive = false
    private var mRequest: AudioFocusRequest? = null

    init {
        mAudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android Oreo (API 26) and above
            mRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioParams.makeAudioAttributes())
                .setOnAudioFocusChangeListener(this)
                .build()
        }
    }

    fun setActive(active: Boolean) {
        if (mActive == active) {
            return
        }
        if (active) {
            requestFocus()
        } else {
            abandonFocus()
        }
        mActive = active
    }

    @Suppress("deprecation")
    private fun requestFocus() {
        // I'm too lazy to check the return value.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.requestAudioFocus(mRequest!!)
        } else {
            mAudioManager.requestAudioFocus(this, AudioParams.STREAM_TYPE, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    @Suppress("deprecation")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.abandonAudioFocusRequest(mRequest!!)
        } else {
            mAudioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS ->         // For example, a music player or a sleep timer stealing focus.
                stopNow(mContext, R.string.stop_reason_audiofocus)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->         // For example, an alarm or phone call.
                mVolumeListener.setDuckLevel(VolumeListener.DuckLevel.SILENT)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->         // For example, an email notification.
                mVolumeListener.setDuckLevel(VolumeListener.DuckLevel.DUCK)
            AudioManager.AUDIOFOCUS_GAIN ->         // Resume the default volume level.
                mVolumeListener.setDuckLevel(VolumeListener.DuckLevel.NORMAL)
        }
    }
}
