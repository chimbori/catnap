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
    val view = convertView ?: inflater.inflate(R.layout.item_memory, parent, false)
    initListItem(view, getItem(position), Saved.NONE)
    return view
  }

  fun initListItem(view: View, ph: Phonon?, saved: Saved) {
    val buf = StringBuilder()
    if (ph!!.minVol != 100) {
      buf.append(ph.minVolText)
      buf.append('\n')
      buf.append(ph.periodText)
      if (saved != Saved.NONE) {
        buf.append('\n')
      }
    }
    if (saved == Saved.YES) {
      buf.append('\u21E9') // Down arrow.
    } else if (saved == Saved.NO) {
      buf.append(context.getString(R.string.unsaved))
    }
    (view.findViewById<View>(R.id.item_memory_name) as TextView).text = buf.toString()
    (view.findViewById<View>(R.id.item_memory_equalizer) as EqualizerViewLite).phonon = ph
  }

  enum class Saved {
    YES,
    NO,
    NONE
  }
}
