package com.chimbori.catnap

import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_MUSIC
import android.media.AudioAttributes.USAGE_MEDIA
import android.media.AudioFormat
import android.media.AudioFormat.CHANNEL_OUT_STEREO
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioTrack
import android.media.AudioTrack.MODE_STREAM
import kotlin.math.max

const val SHORTS_PER_SAMPLE = 2 // 16-bit Stereo
const val BYTES_PER_SAMPLE = 4 // 16-bit Stereo
const val LATENCY_MS = 100

val SAMPLE_RATE = AudioTrack.getNativeOutputSampleRate(STREAM_MUSIC)
val BUF_BYTES = max(
  AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_STEREO, ENCODING_PCM_16BIT),
  SAMPLE_RATE * LATENCY_MS / 1000 * BYTES_PER_SAMPLE
)
val BUF_SAMPLES = BUF_BYTES / BYTES_PER_SAMPLE

fun makeAudioTrack() = AudioTrack(
  makeAudioAttributes(),
  AudioFormat.Builder()
    .setSampleRate(SAMPLE_RATE)
    .setChannelMask(CHANNEL_OUT_STEREO)
    .setEncoding(ENCODING_PCM_16BIT)
    .build(),
  BUF_BYTES,
  MODE_STREAM,
  AUDIO_SESSION_ID_GENERATE
)

internal fun makeAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
  .setUsage(USAGE_MEDIA)
  .setContentType(CONTENT_TYPE_MUSIC)
  .build()
