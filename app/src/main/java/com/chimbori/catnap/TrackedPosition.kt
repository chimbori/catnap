package com.chimbori.catnap

// TrackedPosition keeps track of a single position within
// an ArrayList, even as rows get rearranged by the DSLV.
class TrackedPosition {
  private var mPos = NOWHERE
  var pos: Int
    get() = mPos
    set(p) {
      require((MINVAL <= p && p < NOWHERE)) { "Out of range" }
      mPos = p
    }

  // Move some item in the list.
  // If this position moved to nowhere, throw Deleted.
  // Otherwise, return true if this position moved at all.
  @Throws(Deleted::class)
  fun move(from: Int, to: Int): Boolean {
    // from to result
    // ---- -- ------
    //   =  =  noop
    //   =  *  omg!
    //   <  >= -1
    //   <  <  noop
    //   >  <= +1
    //   >  >  noop
    check(mPos != NOWHERE)
    require(!(from < 0 || to < 0))
    if (from == mPos) {
      if (to != mPos) {
        mPos = to
        if (mPos == NOWHERE) {
          throw Deleted()
        }
        return true
      }
    } else if (from < mPos) {
      if (to >= mPos) {
        mPos -= 1
        check(!(mPos < MINVAL))
        return true
      }
    } else if (from > mPos) {
      if (to <= mPos) {
        mPos += 1
        if (mPos >= NOWHERE) {
          throw IllegalStateException()
        }
        return true
      }
    } else {
      throw RuntimeException()
    }
    return false
  }

  class Deleted : Exception()
  companion object {
    // NOWHERE must be larger than any other value, for the math to work.
    const val NOWHERE = Int.MAX_VALUE

    // Start at -1, so the scratch position can be tracked as well.
    private const val MINVAL = -1
  }
}
