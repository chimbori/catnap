package com.chimbori.catnap

import android.os.Process
import android.os.SystemClock
import org.jtransforms.dct.FloatDCT_1D

internal class SampleGenerator(
  private val mNoiseService: NoiseService, private val mParams: AudioParams,
  private val mSampleShuffler: SampleShuffler
) {
  private val mWorkerThread: Thread
  private val mRandom = XORShiftRandom() // Not thread safe.

  // Communication variables; must be synchronized.
  private var mStopping = false
  private var mPendingSpectrum: SpectrumData? = null

  // Variables accessed from the thread only.
  private var mLastDctSize = -1
  private var mDct: FloatDCT_1D? = null

  init {
    mWorkerThread = object : Thread("SampleGeneratorThread") {
      override fun run() {
        try {
          threadLoop()
        } catch (e: StopException) {
        }
      }
    }
    mWorkerThread.start()
  }

  fun stopThread() {
    synchronized(this) {
      mStopping = true
      (this as Object).notify()
    }
    try {
      mWorkerThread.join()
    } catch (e: InterruptedException) {
    }
  }

  @Synchronized
  fun updateSpectrum(spectrum: SpectrumData?) {
    mPendingSpectrum = spectrum
    (this as Object).notify()
  }

  @Throws(StopException::class)
  private fun threadLoop() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

    // Chunk-making progress:
    val state = SampleGeneratorState()
    var spectrum: SpectrumData? = null
    var waitMs: Long = -1
    while (true) {
      // This does one of 3 things:
      // - Throw StopException if stopThread() was called.
      // - Check if a new spectrum is waiting.
      // - Block if there's no work to do.
      val newSpectrum = popPendingSpectrum(waitMs)
      if (newSpectrum != null && !newSpectrum.sameSpectrum(spectrum)) {
        spectrum = newSpectrum
        state.reset()
        mNoiseService.updatePercentAsync(state.percent)
      } else if (waitMs == -1L) {
        // Nothing changed.  Keep waiting.
        continue
      }
      val startMs = SystemClock.elapsedRealtime()

      // Generate the next chunk of sound.
      val dctData = doIDCT(state.chunkSize, spectrum)
      if (mSampleShuffler.handleChunk(dctData, state.stage)) {
        // Not dropped.
        state.advance()
        mNoiseService.updatePercentAsync(state.percent)
      }

      // Avoid burning the CPU while the user is scrubbing.  For the
      // first couple large chunks, the next chunk should be ready
      // when this one is ~75% finished playing.
      val sleepTargetMs = state.getSleepTargetMs(mParams.SAMPLE_RATE)
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      waitMs = sleepTargetMs - elapsedMs
      if (waitMs < 0) waitMs = 0
      if (waitMs > sleepTargetMs) waitMs = sleepTargetMs
      if (state.done()) {
        // No chunks left; save RAM.
        mDct = null
        mLastDctSize = -1
        waitMs = -1
      }
    }
  }

  @Synchronized
  @Throws(StopException::class)
  private fun popPendingSpectrum(waitMs: Long): SpectrumData? {
    if (waitMs != 0L && !mStopping && mPendingSpectrum == null) {
      // Wait once.  The retry loop is in the caller.
      try {
        if (waitMs < 0) {
          (this as Object).wait()
        } else {
          (this as Object).wait(waitMs)
        }
      } catch (e: InterruptedException) {
      }
    }
    if (mStopping) {
      throw StopException()
    }
    return try {
      mPendingSpectrum
    } finally {
      mPendingSpectrum = null
    }
  }

  private fun doIDCT(dctSize: Int, spectrum: SpectrumData?): FloatArray {
    if (dctSize != mLastDctSize) {
      mDct = FloatDCT_1D(dctSize.toLong())
      mLastDctSize = dctSize
    }
    val dctData = FloatArray(dctSize)
    spectrum!!.fill(dctData, mParams.SAMPLE_RATE)

    // Multiply by a block of white noise.
    var i = 0
    while (i < dctSize) {
      var rand = mRandom.nextLong()
      for (b in 0..7) {
        dctData[i++] *= rand.toByte() / 128f
        rand = rand shr 8
      }
    }
    mDct!!.inverse(dctData, false)
    return dctData
  }

  private class StopException : Exception()
}
