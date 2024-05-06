package com.chimbori.catnap.ui

import android.view.View
import com.chimbori.catnap.Preset
import com.chimbori.catnap.R
import com.chimbori.catnap.databinding.ItemPresetBinding
import com.xwray.groupie.viewbinding.BindableItem

class PresetItem(private val preset: Preset) : BindableItem<ItemPresetBinding>() {
  override fun getLayout() = R.layout.item_preset
  override fun initializeViewBinding(view: View) = ItemPresetBinding.bind(view)
  override fun getId() = preset.hashCode().toLong()

  override fun bind(viewBinding: ItemPresetBinding, position: Int) {
    viewBinding.itemPresetEqualizer.preset = preset
    viewBinding.itemPresetName.text = preset.name
  }
}
