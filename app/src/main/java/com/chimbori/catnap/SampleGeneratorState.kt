package com.chimbori.catnap

internal class SampleGeneratorState {
  // Begin in the "done" state.
  private var mChunkNumber = N_TOTAL_CHUNKS
  fun reset() {
    mChunkNumber = 0
  }

  fun advance() {
    mChunkNumber++
  }

  fun done(): Boolean {
    return mChunkNumber >= N_TOTAL_CHUNKS
  }

  val stage: Int
    get() = if (mChunkNumber < N_SMALL_CHUNKS) {
      // Small chunk.
      when (mChunkNumber) {
        0 -> S_FIRST_SMALL
        else -> S_OTHER_SMALL
      }
    } else if (mChunkNumber < N_SMALL_CHUNKS + N_VOLUME_CHUNKS) {
      // Large chunk, with volume computation.
      when (mChunkNumber) {
        N_SMALL_CHUNKS -> S_FIRST_VOLUME
        N_SMALL_CHUNKS + N_VOLUME_CHUNKS - 1 -> S_LAST_VOLUME
        else -> S_OTHER_VOLUME
      }
    } else {
      // Large chunk, volume already set.
      S_LARGE_NOCLIP
    }
  val percent: Int
    get() = mChunkNumber * 100 / N_TOTAL_CHUNKS
  val chunkSize: Int
    get() = if (mChunkNumber < N_SMALL_CHUNKS) SMALL_CHUNK_SIZE else LARGE_CHUNK_SIZE

  // For the first couple large chunks, returns 75% of the chunk duration.
  fun getSleepTargetMs(sampleRate: Int): Long {
    val i = mChunkNumber - N_SMALL_CHUNKS
    return if (0 <= i && i < 2) {
      (750 * LARGE_CHUNK_SIZE / sampleRate).toLong()
    } else 0
  }

  companion object {
    // List of possible chunk stages.
    const val S_FIRST_SMALL = 0
    const val S_OTHER_SMALL = 1
    const val S_FIRST_VOLUME = 2
    const val S_OTHER_VOLUME = 3
    const val S_LAST_VOLUME = 4
    const val S_LARGE_NOCLIP = 5

    // How many small preview chunks to generate at first.
    private const val N_SMALL_CHUNKS = 4

    // How many final full-size chunks to generate.
    private const val N_LARGE_CHUNKS = 20

    // How many chunks overall.
    private const val N_TOTAL_CHUNKS = N_SMALL_CHUNKS + N_LARGE_CHUNKS

    // How many large chunks to use for estimating the global volume.
    private const val N_VOLUME_CHUNKS = 4

    // Size of small/large chunks, in samples.
    private const val SMALL_CHUNK_SIZE = 8192
    private const val LARGE_CHUNK_SIZE = 65536
  }
}
