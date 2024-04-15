package com.chimbori.catnap

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max

internal class AudioParams {
    val SAMPLE_RATE: Int
    val BUF_BYTES: Int
    val BUF_SAMPLES: Int

    init {
        SAMPLE_RATE = AudioTrack.getNativeOutputSampleRate(STREAM_TYPE)
        BUF_BYTES = max(
            AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            SAMPLE_RATE * LATENCY_MS / 1000 * BYTES_PER_SAMPLE
        )
        BUF_SAMPLES = BUF_BYTES / BYTES_PER_SAMPLE
    }

    @Suppress("deprecation")
    private fun makeAudioTrackLegacy(): AudioTrack {
        return AudioTrack(
            STREAM_TYPE, SAMPLE_RATE, CHANNEL_CONFIG,
            AUDIO_FORMAT, BUF_BYTES, AudioTrack.MODE_STREAM
        )
    }

    fun makeAudioTrack(): AudioTrack {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioTrack(
                makeAudioAttributes(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build(),
                BUF_BYTES,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        } else {
            makeAudioTrackLegacy()
        }
    }

    companion object {
        const val STREAM_TYPE = AudioManager.STREAM_MUSIC
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SHORTS_PER_SAMPLE = 2 // 16-bit Stereo
        const val BYTES_PER_SAMPLE = 4 // 16-bit Stereo
        const val LATENCY_MS = 100
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        fun makeAudioAttributes(): AudioAttributes {
            return AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        }
    }
}
