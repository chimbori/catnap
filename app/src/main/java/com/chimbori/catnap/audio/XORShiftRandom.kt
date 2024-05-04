package com.chimbori.catnap.audio

// XORShift is supposedly better and faster than java.util.Random.
// This algorithm is from:
// http://www.javamex.com/tutorials/random_numbers/xorshift.shtml
internal class XORShiftRandom {
  private var mState = System.nanoTime()
  fun nextLong(): Long {
    var x = mState
    x = x xor (x shl 21)
    x = x xor (x ushr 35)
    x = x xor (x shl 4)
    mState = x
    return x
  }

  // Get a random number from [0, limit), for small values of limit.
  fun nextInt(limit: Int): Int {
    return (nextLong().toInt() and 0x7FFFFFFF) % limit
  }
}
