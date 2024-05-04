package com.chimbori.catnap.audio

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
import android.os.Build
import android.os.Process
import android.os.Process.THREAD_PRIORITY_AUDIO
import android.util.Log
import com.chimbori.catnap.audio.SampleShuffler.VolumeListener.DuckLevel
import com.chimbori.catnap.audio.SampleShuffler.VolumeListener.DuckLevel.DUCK
import com.chimbori.catnap.audio.SampleShuffler.VolumeListener.DuckLevel.NORMAL
import com.chimbori.catnap.audio.SampleShuffler.VolumeListener.DuckLevel.SILENT
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sin

const val SHORTS_PER_SAMPLE = 2 // 16-bit Stereo
const val BYTES_PER_SAMPLE = 4 // 16-bit Stereo
const val LATENCY_MS = 100

val SAMPLE_RATE = AudioTrack.getNativeOutputSampleRate(STREAM_MUSIC)
val BUF_BYTES = max(
  AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_STEREO, ENCODING_PCM_16BIT),
  SAMPLE_RATE * LATENCY_MS / 1000 * BYTES_PER_SAMPLE
)
val BUF_SAMPLES = BUF_BYTES / BYTES_PER_SAMPLE

/* Crossfade notes:

When adding two streams together, the perceived amplitude stays constant
if x^2 + y^2 == 1 for amplitudes x, y.

A useful identity is: sin(x)^2 + cos(x)^2 == 1.

Thus, we can perform a constant-amplitude crossfade using:
  result = fade_out * cos(x) + fade_in * sin(x)
  for x in [0, pi/2]

But we also need to prevent clipping.  The maximum of sin(x) + cos(x)
occurs at the midpoint, with a result of sqrt(2), or ~1.414.

Thus, for a 16-bit output stream in the range +/-32767, we need to keep the
individual streams below 32767 / sqrt(2), or ~23170.
*/
internal class SampleShuffler {
  private val mShuffleBag = ShuffleBag()
  private val mPlaybackThread: PlaybackThread
  private var mAudioChunks: MutableList<AudioChunk>? = null
  private var mGlobalVolumeFactor = 0f

  // Filler state.
  // Note that it's essential for this to be synchronized, because
  // the shuffle thread can steal the next fillBuffer() at any time.
  private var mCursor0 = 0
  private var mChunk0: ShortArray? = null
  private var mChunk1: ShortArray? = null
  private var mAlternateFuture: ShortArray? = null
  private var mAmpWave = AmpWave(1f, 0f)

  init {
    mPlaybackThread = PlaybackThread()
  }

  fun stopThread() {
    mPlaybackThread.stopPlaying()
    try {
      mPlaybackThread.join()
    } catch (e: InterruptedException) {
    }
    // Explicitly discard chunks to make life easier for the garbage
    // collector.  Comment this out to make memory leaks more obvious.
    // (Hopefully, I fixed the leak that prompted me to do this.)
    mAudioChunks = null
  }

  val volumeListener: VolumeListener
    get() = mPlaybackThread

  @Synchronized
  fun setAmpWave(minVol: Float, period: Float) {
    if (mAmpWave.mMinVol != minVol || mAmpWave.mPeriod != period) {
      mAmpWave = AmpWave(minVol, period)
    }
  }

  fun handleChunk(dctData: FloatArray?, stage: Int): Boolean {
    val newChunk = AudioChunk(dctData)
    when (stage) {
      SampleGeneratorState.S_FIRST_SMALL -> {
        handleChunkPioneer(newChunk, true)
        return true
      }
      SampleGeneratorState.S_OTHER_SMALL -> {
        handleChunkAdaptVolume(newChunk)
        return true
      }
      SampleGeneratorState.S_FIRST_VOLUME -> {
        handleChunkPioneer(newChunk, false)
        return true
      }
      SampleGeneratorState.S_OTHER_VOLUME -> {
        handleChunkAdaptVolume(newChunk)
        return true
      }
      SampleGeneratorState.S_LAST_VOLUME -> {
        handleChunkFinalizeVolume(newChunk)
        return true
      }
      SampleGeneratorState.S_LARGE_NOCLIP -> return handleChunkNoClip(newChunk)
    }
    throw RuntimeException("Invalid stage")
  }

  // Add a new chunk, deleting all the earlier ones.
  private fun handleChunkPioneer(newChunk: AudioChunk, notify: Boolean) {
    mGlobalVolumeFactor = BASE_AMPLITUDE / newChunk.maxAmplitude
    exchangeChunk(withPcm(newChunk), notify)
  }

  // Add a new chunk.  If it would clip, make everything quieter.
  private fun handleChunkAdaptVolume(newChunk: AudioChunk) {
    if (newChunk.maxAmplitude * mGlobalVolumeFactor > CLIP_AMPLITUDE) {
      changeGlobalVolume(newChunk.maxAmplitude, newChunk)
    } else {
      addChunk(withPcm(newChunk))
    }
  }

  // Add a new chunk, and force a max volume that no others can cross.
  private fun handleChunkFinalizeVolume(newChunk: AudioChunk) {
    var maxAmplitude: Float = newChunk.maxAmplitude
    for (c in mAudioChunks!!) {
      maxAmplitude = max(maxAmplitude, c.maxAmplitude)
    }
    if (maxAmplitude * mGlobalVolumeFactor >= BASE_AMPLITUDE) {
      changeGlobalVolume(maxAmplitude, newChunk)
    } else {
      addChunk(withPcm(newChunk))
    }

    // Delete the now-unused float data, to conserve RAM.
    for (c in mAudioChunks!!) {
      c.purgeFloatData()
    }
  }

  // Add a new chunk.  If it clips, discard it and ask for another.
  private fun handleChunkNoClip(newChunk: AudioChunk): Boolean {
    return if (newChunk.maxAmplitude * mGlobalVolumeFactor > CLIP_AMPLITUDE) {
      false
    } else {
      addChunk(withPcm(newChunk))
      true
    }
  }

  // Recompute all chunks with a new volume level.
  // Add a new one first, so the chunk list is never completely empty.
  private fun changeGlobalVolume(maxAmplitude: Float, newChunk: AudioChunk) {
    mGlobalVolumeFactor = BASE_AMPLITUDE / maxAmplitude
    val oldChunks = exchangeChunk(withPcm(newChunk), false)
    val playedChunks: MutableList<AudioChunk> = ArrayList()

    // First, process the never-played chunks.
    for (c in oldChunks!!) {
      if (c.neverPlayed()) {
        addChunk(withPcm(c))
      } else {
        playedChunks.add(c)
      }
    }
    // Process the leftovers.
    for (c in playedChunks) {
      addChunk(withPcm(c))
    }
  }

  private fun withPcm(chunk: AudioChunk): AudioChunk {
    chunk.buildPcmData(mGlobalVolumeFactor)
    return chunk
  }

  @Synchronized
  private fun resetFillState(chunk0: ShortArray?) {
    // mCursor0 begins at the first non-faded sample, not at 0.
    mCursor0 = FADE_LEN
    mChunk0 = chunk0
    mChunk1 = null
  }

  @get:Synchronized
  private val randomChunk: ShortArray
    get() = mAudioChunks!!.get(mShuffleBag.next).pcmData

  @Synchronized
  private fun addChunk(chunk: AudioChunk) {
    val pos = mAudioChunks!!.size
    mAudioChunks!!.add(chunk)
    mShuffleBag.put(pos, chunk.neverPlayed())
  }

  @Synchronized
  private fun exchangeChunk(chunk: AudioChunk, notify: Boolean): List<AudioChunk>? {
    if (notify) {
      if (mAudioChunks != null && mAlternateFuture == null) {
        // Grab the chunk of data that would've been played if it
        // weren't for this interruption.  Later, we'll cross-fade it
        // with the new data to avoid pops.
        val peek = ShortArray(FADE_LEN * SHORTS_PER_SAMPLE)
        fillBuffer(peek)
        mAlternateFuture = peek
      }
      resetFillState(null)
    }
    val oldChunks: List<AudioChunk>? = mAudioChunks
    mAudioChunks = ArrayList()
    mAudioChunks!!.add(chunk)
    mShuffleBag.clear()
    mShuffleBag.put(0, chunk.neverPlayed())

    // Begin playback when the first chunk arrives.
    // The fade-in effect makes mAlternateFuture unnecessary.
    if (oldChunks == null) {
      mPlaybackThread.start()
    }
    return oldChunks
  }

  // Requires: out has room for at least FADE_LEN samples.
  // Returns: the current mAmpWave.
  //
  @Synchronized
  private fun fillBuffer(out: ShortArray): AmpWave {
    if (mChunk0 == null) {
      // This should only happen after a reset.
      mChunk0 = randomChunk
    }
    var outPos = 0
    outerLoop@ while (true) {
      // Get the index within mChunk0 where the fade-out begins.
      val firstFadeSample = mChunk0!!.size - FADE_LEN

      // For cheap stereo, just play the same chunk backwards.
      var reverseCursor0 = mChunk0!!.size - 1 - mCursor0

      // Fill from the non-faded middle of the first chunk.
      while (mCursor0 < firstFadeSample) {
        out[outPos++] = mChunk0!![mCursor0++]
        out[outPos++] = mChunk0!![reverseCursor0--]
        if (outPos >= out.size) {
          break@outerLoop
        }
      }

      // Fill from the crossfade between two chunks.
      if (mChunk1 == null) {
        mChunk1 = randomChunk
      }
      var cursor1 = mCursor0 - firstFadeSample
      var reverseCursor1 = mChunk1!!.size - 1 - cursor1
      while (mCursor0 < mChunk0!!.size) {
        out[outPos++] = (mChunk0!![mCursor0++] + mChunk1!![cursor1++]).toShort()
        out[outPos++] = (mChunk0!![reverseCursor0--] + mChunk1!![reverseCursor1--]).toShort()
        if (outPos >= out.size) {
          break@outerLoop
        }
      }

      // Make sure we've consumed all the fade data.
      check(cursor1 == FADE_LEN) { "Out of sync" }

      // Switch to the next chunk.
      resetFillState(mChunk1)
    }
    if (mAlternateFuture != null) {
      // This means that the spectrum was abruptly changed.  Crossfade
      // from old to new, to avoid pops.  This is more CPU-intensive
      // than fading between two chunks, because the envelopes aren't
      // precomputed.  Also, this might result in clipping if the inputs
      // happen to be in the middle of a crossfade already.
      outPos = 0
      // Note: changed i++ to i+=8, for scrubbing latency of ~10ms.
      var i = 1
      while (i <= FADE_LEN) {
        for (chan in 0..1) {
          var sample = mAlternateFuture!![outPos] * SINE[SINE_LEN + i] +
              out[outPos] * SINE[i]
          if (sample > 32767f) sample = 32767f
          if (sample < -32767f) sample = -32767f
          out[outPos++] = sample.toInt().toShort()
        }
        i += 8
      }
      mAlternateFuture = null
    }
    return mAmpWave
  }

  interface VolumeListener {
    fun setDuckLevel(d: DuckLevel)
    fun setVolumeLevel(v: Float) // Range is 0..1
    enum class DuckLevel {
      SILENT,
      DUCK,
      NORMAL
    }
  }

  // This class keeps track of a set of numbers, and dishes them out in
  // a random order, while maintaining a minimum distance between two
  // occurrences of the same number.
  private class ShuffleBag {
    // Chunks that have never been played before, in arbitrary order.
    private val newQueue: MutableList<Int> = ArrayList()

    // Recent chunks sit here to avoid being played too soon.
    private val feederQueue: MutableList<Int> = ArrayList()
    private val mRandom = XORShiftRandom() // Not thread safe.

    // Randomly draw chunks from here.
    private var drawPile: MutableList<Int> = ArrayList()

    // Chunks go here once they've been played.
    private var discardPile: MutableList<Int> = ArrayList()
    fun clear() {
      newQueue.clear()
      feederQueue.clear()
      drawPile.clear()
      discardPile.clear()
    }

    fun put(x: Int, neverPlayed: Boolean) {
      // Put never-played chunks into the newQueue.
      // There's no ideal place for the old chunks, but drawPile is simplest.
      (if (neverPlayed) newQueue else drawPile).add(x)
    }

    val next: Int
      get() {
        if (!newQueue.isEmpty()) {
          return discard(pop(newQueue))
        }
        if (drawPile.isEmpty()) {
          check(feederQueue.isEmpty())
          // Everything is now in discardPile.  Move the recently-played chunks
          // to feederQueue.  Note that pop(feederQueue) will yield chunks in
          // the same order they were discarded.
          val feederSize = discardPile.size / 2
          for (i in 0 until feederSize) {
            feederQueue.add(pop(discardPile))
          }
          // Move everything else to the drawPile.
          val empty = drawPile
          drawPile = discardPile
          discardPile = empty
        }
        if (drawPile.isEmpty()) {
          throw NoSuchElementException()
        }
        val pos = mRandom.nextInt(drawPile.size)
        val ret = drawPile[pos]
        if (!feederQueue.isEmpty()) {
          // Overwrite the vacant space.
          drawPile[pos] = pop(feederQueue)
        } else {
          // Move last element to the vacant space.
          try {
            drawPile[pos] = pop(drawPile)
          } catch (e: IndexOutOfBoundsException) {
            // Last element *was* the vacant space.
          }
        }
        return discard(ret)
      }

    private fun discard(x: Int): Int {
      discardPile.add(x)
      return x
    }

    companion object {
      private fun pop(list: MutableList<Int>): Int {
        return list.removeAt(list.size - 1)
      }
    }
  }

  private class AudioChunk(private var mFloatData: FloatArray?) {
    private var mNeverPlayed = true
    private lateinit var mPcmData: ShortArray

    var maxAmplitude = 0f
      private set

    init {
      computeMaxAmplitude()
    }

    // Figure out the max amplitude of this chunk once.
    private fun computeMaxAmplitude() {
      maxAmplitude = 1f // Prevent division by zero.
      for (sample in mFloatData!!) {
        if (sample.absoluteValue > maxAmplitude) {
          maxAmplitude = sample
        }
      }
    }

    fun buildPcmData(volumeFactor: Float) {
      val len = mFloatData!!.size
      require(!(len < FADE_LEN * 2)) { "Undersized chunk: $len" }
      mPcmData = ShortArray(len)
      for (i in 0 until FADE_LEN) {
        // Fade in using sin(x), x=(0,pi/2)
        val fadeFactor = SINE[i + 1]
        mPcmData[i] = (mFloatData!![i] * volumeFactor * fadeFactor).toInt().toShort()
      }
      for (i in FADE_LEN until len - FADE_LEN) {
        mPcmData[i] = (mFloatData!![i] * volumeFactor).toInt().toShort()
      }
      for (i in len - FADE_LEN until len) {
        val j = i - (len - FADE_LEN)
        // Fade out using cos(x), x=(0,pi/2)
        val fadeFactor = SINE[SINE_LEN + j + 1]
        mPcmData[i] = (mFloatData!![i] * volumeFactor * fadeFactor).toInt().toShort()
      }
    }

    fun neverPlayed(): Boolean {
      return mNeverPlayed
    }

    val pcmData: ShortArray
      get() {
        mNeverPlayed = false
        return mPcmData
      }

    fun purgeFloatData() {
      mFloatData = null
    }
  }

  private class AmpWave(minVol: Float, period: Float /* seconds */) {
    // The minimum amplitude, from [0,1]
    val mMinVol: Float

    // The wave period, in seconds.
    val mPeriod: Float

    // Same length as mSine, but shifted/stretched according to mMinAmp.
    // We want to do the multiply using integer math, so [0.0, 1.0] is
    // stored as [0, 32767].
    private val mTweakedSine: ShortArray?
    private var mSpeed = 0
    private var mPos = QUIET_POS

    init {
      var minVol = minVol
      var period = period
      if (minVol > .999f || period < .001f) {
        mTweakedSine = null
        mSpeed = 0
      } else {
        // Make sure the numbers stay reasonable.
        if (minVol < 0f) minVol = 0f
        if (period > 300f) period = 300f

        // Make a sine wave oscillate from minVol to 100%.
        mTweakedSine = ShortArray(4 * SINE_LEN)
        val scale = (1f - minVol) / 2f
        for (i in mTweakedSine.indices) {
          mTweakedSine[i] = ((SINE[i] * scale + 1f - scale) * 32767f).toInt().toShort()
        }

        // When period == 1 sec, SAMPLE_RATE iterations should cover
        // SINE_PERIOD virtual points.
        mSpeed = (SINE_PERIOD / (period * SAMPLE_RATE)).toInt()
      }
      mMinVol = minVol
      mPeriod = period
    }

    // It's only safe to call this from the playback thread.
    fun copyOldPosition(old: AmpWave?) {
      if (old != null && old !== this) {
        mPos = old.mPos
      }
    }

    // Apply the amplitude wave to this audio buffer.
    // It's only safe to call this from the playback thread.
    // Returns true if stopAtLoud reached its target.
    fun mutateBuffer(buf: ShortArray, stopAtLoud: Boolean): Boolean {
      if (mTweakedSine == null) {
        return false
      }
      var outPos = 0
      while (outPos < buf.size) {
        if (stopAtLoud && LOUD_POS <= mPos && mPos < QUIET_POS) {
          return true // Reached 100% volume.
        }
        // Multiply by [0, 1) using integer math.
        val mult = mTweakedSine[mPos / SINE_STRETCH]
        mPos = mPos + mSpeed and SINE_PERIOD - 1
        for (chan in 0 until SHORTS_PER_SAMPLE) {
          buf[outPos] = (buf[outPos] * mult shr 15).toShort()
          outPos++
        }
      }
      return false
    }

    companion object {
      // This constant defines how many virtual points map to one period
      // of the amplitude wave.  Must be a power of 2.
      const val SINE_PERIOD = 1 shl 30
      val SINE_STRETCH = SINE_PERIOD / (4 * SINE_LEN)

      // Quietest point on the sine wave (3π/2)
      val QUIET_POS = (SINE_PERIOD * .75).toInt()

      // Loudest point on the sine wave (π/2)
      val LOUD_POS = (SINE_PERIOD * .25).toInt()
    }
  }

  private inner class PlaybackThread : Thread("SampleShufflerThread"), VolumeListener {
    private var mPreventStart = false
    private var mTrack: AudioTrack? = null
    private var mDuckLevel = NORMAL
    private var mVolumeLevel = 1f

    @Synchronized
    private fun startPlaying(): Boolean {
      if (mPreventStart || mTrack != null) {
        return false
      }
      // I occasionally receive this crash report:
      // "java.lang.IllegalStateException: play() called on uninitialized AudioTrack."
      // Perhaps it just needs a retry loop?  I have no idea if this helps at all.
      var i = 1
      while (true) {
        mTrack = makeAudioTrack()
        setVolumeInternal()
        try {
          mTrack!!.play()
          return true
        } catch (e: IllegalStateException) {
          if (i >= 3) throw e
          Log.w("PlaybackThread", "Failed to play(); retrying:", e)
          System.gc()
        }
        i++
      }
    }

    @Synchronized
    fun stopPlaying() {
      if (mTrack == null) {
        mPreventStart = true
      } else {
        mTrack!!.stop()
      }
    }

    // Manage "Audio Focus" by changing the volume level.
    @Synchronized
    override fun setDuckLevel(d: DuckLevel) {
      mDuckLevel = d
      if (mTrack != null) {
        setVolumeInternal()
      }
    }

    @Synchronized
    override fun setVolumeLevel(v: Float) {
      if (v < 0f || v > 1f) {
        throw IllegalArgumentException("Invalid volume: $v")
      }
      mVolumeLevel = v
      if (mTrack != null) {
        setVolumeInternal()
      }
    }

    @Suppress("deprecation")
    private fun setVolumeCompat(mTrack: AudioTrack?, v: Float) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        mTrack!!.setVolume(v)
      } else {
        mTrack!!.setStereoVolume(v, v)
      }
    }

    private fun setVolumeInternal() {
      setVolumeCompat(
        mTrack, when (mDuckLevel) {
          SILENT -> 0f
          DUCK -> mVolumeLevel * 0.1f
          NORMAL -> mVolumeLevel
        }
      )
    }

    override fun run() {
      Process.setThreadPriority(THREAD_PRIORITY_AUDIO)
      if (!startPlaying()) {
        return
      }

      // Apply a fade-in effect on startup (half-period = 1sec)
      var fadeIn: AmpWave? = AmpWave(0f, 2f)

      // Aim to write half of the AudioTrack's buffer per iteration,
      // but FADE_LEN is the bare minimum to avoid errors.
      val buf = ShortArray(max(BUF_SAMPLES / 2, FADE_LEN) * SHORTS_PER_SAMPLE)
      var oldAmpWave: AmpWave? = null
      var result: Int
      do {
        val newAmpWave = fillBuffer(buf)
        newAmpWave.copyOldPosition(oldAmpWave)
        newAmpWave.mutateBuffer(buf, false)
        oldAmpWave = newAmpWave
        if (fadeIn != null && fadeIn.mutateBuffer(buf, true)) {
          fadeIn = null
        }
        // AudioTrack will write everything, unless it's been stopped.
        result = mTrack!!.write(buf, 0, buf.size)
      } while (result == buf.size)
      if (result < 0) {
        Log.w("PlaybackThread", "write() failed: $result")
      }
      mTrack!!.release()
    }
  }

  companion object {
    // These lengths are measured in samples.
    private const val SINE_LEN = 1 shl 12

    // FADE_LEN follows the "interior", excluding 0 or 1 values.
    private val FADE_LEN = SINE_LEN - 1
    private const val BASE_AMPLITUDE = 20000f
    private const val CLIP_AMPLITUDE = 23000f // 32K/sqrt(2)

    // Sine wave, 4*SINE_LEN points, from [0, 2pi).
    private val SINE: FloatArray

    init {
      SINE = FloatArray(4 * SINE_LEN)
      // First quarter, compute directly.
      for (i in 0..SINE_LEN) {
        val progress = i.toDouble() / SINE_LEN
        SINE[i] = sin(progress * Math.PI / 2.0).toFloat()
      }
      // Second quarter, flip the first horizontally.
      for (i in SINE_LEN + 1 until 2 * SINE_LEN) {
        SINE[i] = SINE[2 * SINE_LEN - i]
      }
      // Third/Fourth quarters, flip the first two vertically.
      for (i in 2 * SINE_LEN until 4 * SINE_LEN) {
        SINE[i] = -SINE[i - 2 * SINE_LEN]
      }
    }

    private fun makeAudioTrack() = AudioTrack(
      makeAudioAttributes(),
      AudioFormat.Builder()
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(CHANNEL_OUT_STEREO)
        .setEncoding(ENCODING_PCM_16BIT)
        .build(),
      BUF_BYTES, MODE_STREAM, AUDIO_SESSION_ID_GENERATE
    )

    internal fun makeAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
      .setUsage(USAGE_MEDIA)
      .setContentType(CONTENT_TYPE_MUSIC)
      .build()
  }
}
