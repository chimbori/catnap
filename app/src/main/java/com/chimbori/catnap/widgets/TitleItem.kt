package com.chimbori.catnap.widgets

import android.view.View
import com.chimbori.catnap.R
import com.chimbori.catnap.databinding.ItemTitleBinding
import com.xwray.groupie.viewbinding.BindableItem

class TitleItem(private val title: String) : BindableItem<ItemTitleBinding>() {
  override fun getLayout() = R.layout.item_title
  override fun initializeViewBinding(view: View) = ItemTitleBinding.bind(view)
  override fun getId() = title.hashCode().toLong()

  override fun bind(viewBinding: ItemTitleBinding, position: Int) {
    viewBinding.itemTitleText.text = title
  }
}
