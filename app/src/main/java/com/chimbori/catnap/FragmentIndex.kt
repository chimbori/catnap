package com.chimbori.catnap

import android.content.Context

internal object FragmentIndex {
  const val ID_MAIN = 0
  const val ID_OPTIONS = 1
  const val ID_MEMORY = 2
  const val ID_ABOUT = 3
  const val ID_COUNT = 4

  fun getStrings(context: Context): Array<String?> {
    val out = arrayOfNulls<String>(ID_COUNT)
    out[ID_MAIN] = getPaddedString(context, R.string.app_name)
    out[ID_OPTIONS] = getPaddedString(context, R.string.options)
    out[ID_MEMORY] = getPaddedString(context, R.string.memory)
    out[ID_ABOUT] = getPaddedString(context, R.string.about_menu)
    return out
  }

  private fun getPaddedString(context: Context, resId: Int): String {
    return context.getString(resId) + "  "
  }
}
