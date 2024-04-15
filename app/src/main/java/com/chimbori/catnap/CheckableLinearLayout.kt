package com.chimbori.catnap

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import android.widget.LinearLayout

class CheckableLinearLayout(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs), Checkable {
  private var mChild: Checkable? = null
  override fun onFinishInflate() {
    super.onFinishInflate()
    for (i in 0 until childCount) {
      try {
        mChild = getChildAt(i) as Checkable
        return
      } catch (e: ClassCastException) {
      }
    }
  }

  override fun isChecked(): Boolean {
    return mChild!!.isChecked
  }

  override fun setChecked(checked: Boolean) {
    mChild!!.isChecked = checked
  }

  override fun toggle() {
    mChild!!.toggle()
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    for (i in 0 until childCount) {
      getChildAt(i).setEnabled(enabled)
    }
  }
}
