package com.chimbori.catnap

import android.content.Intent

// Read-only view of a PhononMutable
interface Phonon {
  fun toJSON(): String?
  val isSilent: Boolean
  fun getBar(band: Int): Float
  val minVol: Int
  val minVolText: String?
  val period: Int
  val periodText: String?
  fun makeMutableCopy(): PhononMutable?
  fun writeIntent(intent: Intent?)
  fun fastEquals(other: Phonon?): Boolean
}
