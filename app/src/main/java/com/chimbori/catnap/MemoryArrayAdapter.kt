package com.chimbori.catnap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class MemoryArrayAdapter(context: Context?, objects: List<Phonon?>?) : ArrayAdapter<Phonon?>(context!!, 0, objects!!) {
  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val view = convertView ?: inflater.inflate(R.layout.memory_list_item, parent, false)
    initListItem(view, getItem(position), Saved.NONE)
    return view
  }

  fun initListItem(view: View, ph: Phonon?, saved: Saved) {
    val buf = StringBuilder()
    if (ph!!.getMinVol() != 100) {
      buf.append(ph.getMinVolText())
      buf.append('\n')
      buf.append(ph.getPeriodText())
      if (saved != Saved.NONE) {
        buf.append('\n')
      }
    }
    if (saved == Saved.YES) {
      buf.append('\u21E9') // Down arrow.
    } else if (saved == Saved.NO) {
      buf.append(context.getString(R.string.unsaved))
    }
    (view.findViewById<View>(R.id.text) as TextView).text = buf.toString()
    (view.findViewById<View>(R.id.EqualizerView) as EqualizerViewLite).setPhonon(ph)
  }

  enum class Saved {
    YES,
    NO,
    NONE
  }
}
