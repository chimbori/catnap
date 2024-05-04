package com.chimbori.catnap

internal class SampleGeneratorState {
  private var chunkNumber = N_TOTAL_CHUNKS  // Begin in the "done" state.

  fun reset() {
    chunkNumber = 0
  }

  fun advance() {
    chunkNumber++
  }

  fun done() = chunkNumber >= N_TOTAL_CHUNKS

  val stage: Int
    get() = when {
      chunkNumber < N_SMALL_CHUNKS -> when (chunkNumber) {  // Small chunk.
        0 -> S_FIRST_SMALL
        else -> S_OTHER_SMALL
      }
      chunkNumber < N_SMALL_CHUNKS + N_VOLUME_CHUNKS -> when (chunkNumber) {  // Large chunk, with volume computation.
        N_SMALL_CHUNKS -> S_FIRST_VOLUME
        N_SMALL_CHUNKS + N_VOLUME_CHUNKS - 1 -> S_LAST_VOLUME
        else -> S_OTHER_VOLUME
      }
      else -> S_LARGE_NOCLIP  // Large chunk, volume already set.
    }

  val percent: Int
    get() = chunkNumber * 100 / N_TOTAL_CHUNKS

  val chunkSize: Int
    get() = if (chunkNumber < N_SMALL_CHUNKS) SMALL_CHUNK_SIZE else LARGE_CHUNK_SIZE

  // For the first couple large chunks, returns 75% of the chunk duration.
  fun getSleepTargetMs(sampleRate: Int) = if (chunkNumber - N_SMALL_CHUNKS in 0..1) {
    (750 * LARGE_CHUNK_SIZE / sampleRate).toLong()
  } else 0

  companion object {
    // List of possible chunk stages.
    const val S_FIRST_SMALL = 0
    const val S_OTHER_SMALL = 1
    const val S_FIRST_VOLUME = 2
    const val S_OTHER_VOLUME = 3
    const val S_LAST_VOLUME = 4
    const val S_LARGE_NOCLIP = 5

    /** How many small preview chunks to generate at first. */
    private const val N_SMALL_CHUNKS = 4

    /** How many final full-size chunks to generate. */
    private const val N_LARGE_CHUNKS = 20

    /** How many chunks overall. */
    private const val N_TOTAL_CHUNKS = N_SMALL_CHUNKS + N_LARGE_CHUNKS

    /** How many large chunks to use for estimating the global volume. */
    private const val N_VOLUME_CHUNKS = 4

    /** Size of small chunk, in samples. */
    private const val SMALL_CHUNK_SIZE = 8192

    /** Size of large chunk, in samples. */
    private const val LARGE_CHUNK_SIZE = 65536
  }
}
