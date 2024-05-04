package com.chimbori.catnap.ui

import android.view.View
import com.chimbori.catnap.Phonon
import com.chimbori.catnap.R
import com.chimbori.catnap.databinding.ItemPhononBinding
import com.xwray.groupie.viewbinding.BindableItem

class PhononItem(private val phonon: Phonon) : BindableItem<ItemPhononBinding>() {
  override fun getLayout() = R.layout.item_phonon
  override fun initializeViewBinding(view: View) = ItemPhononBinding.bind(view)
  override fun getId() = phonon.hashCode().toLong()

  override fun bind(viewBinding: ItemPhononBinding, position: Int) {
    viewBinding.itemPhononEqualizer.phonon = phonon
  }
}
