package com.chimbori.catnap

import android.os.Process
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.os.SystemClock
import org.jtransforms.dct.FloatDCT_1D

internal class SampleGenerator(private val noiseService: NoiseService, private val sampleShuffler: SampleShuffler) {
  private val workerThread = object : Thread("SampleGeneratorThread") {
    override fun run() {
      try {
        threadLoop()
      } catch (e: StopException) {
        // Ignore
      }
    }
  }

  private val xorShiftRandom = XORShiftRandom() // Not thread safe.

  // Communication variables; must be synchronized.
  private var isStopping = false
  private var pendingSpectrum: SpectrumData? = null

  // Variables accessed from the thread only.
  private var lastDctSize = -1
  private var dct: FloatDCT_1D? = null

  init {
    workerThread.start()
  }

  fun stopThread() {
    synchronized(this) {
      isStopping = true
      (this as Object).notify()
    }
    try {
      workerThread.join()
    } catch (e: InterruptedException) {
      // Ignore
    }
  }

  @Synchronized
  fun updateSpectrum(spectrum: SpectrumData?) {
    pendingSpectrum = spectrum
    (this as Object).notify()
  }

  private fun threadLoop() {
    Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND)

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
        noiseService.updatePercentAsync(state.percent)
      } else if (waitMs == -1L) {
        // Nothing changed. Keep waiting.
        continue
      }
      val startMs = SystemClock.elapsedRealtime()

      // Generate the next chunk of sound.
      val dctData = doIDCT(state.chunkSize, spectrum!!)
      if (sampleShuffler.handleChunk(dctData, state.stage)) {
        // Not dropped.
        state.advance()
        noiseService.updatePercentAsync(state.percent)
      }

      // Avoid burning the CPU while the user is scrubbing. For the first couple large chunks, the next chunk
      // should be ready when this one is ~75% finished playing.
      val sleepTargetMs = state.getSleepTargetMs(SAMPLE_RATE)
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      waitMs = (sleepTargetMs - elapsedMs).coerceIn(0, sleepTargetMs)
      if (state.done()) {  // No chunks left; save RAM.
        dct = null
        lastDctSize = -1
        waitMs = -1
      }
    }
  }

  @Synchronized
  @Throws(StopException::class)
  private fun popPendingSpectrum(waitMs: Long): SpectrumData? {
    if (waitMs != 0L && !isStopping && pendingSpectrum == null) {
      // Wait once. The retry loop is in the caller.
      try {
        if (waitMs < 0) {
          (this as Object).wait()
        } else {
          (this as Object).wait(waitMs)
        }
      } catch (e: InterruptedException) {
        // Ignore
      }
    }
    if (isStopping) {
      throw StopException()
    }
    return try {
      pendingSpectrum
    } finally {
      pendingSpectrum = null
    }
  }

  private fun doIDCT(dctSize: Int, spectrum: SpectrumData): FloatArray {
    if (dctSize != lastDctSize) {
      dct = FloatDCT_1D(dctSize.toLong())
      lastDctSize = dctSize
    }
    val dctData = FloatArray(dctSize)
    spectrum.fill(dctData, SAMPLE_RATE)

    // Multiply by a block of white noise.
    var i = 0
    while (i < dctSize) {
      var rand = xorShiftRandom.nextLong()
      for (b in 0..7) {
        dctData[i++] *= rand.toByte() / 128f
        rand = rand shr 8
      }
    }
    dct!!.inverse(dctData, false)
    return dctData
  }

  private class StopException : Exception()
}
