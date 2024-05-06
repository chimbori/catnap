package com.chimbori.catnap.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chimbori.catnap.R
import com.chimbori.catnap.databinding.FragmentPresetsBinding
import com.chimbori.catnap.widgets.TitleItem
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding

class PresetsFragment : Fragment(R.layout.fragment_presets) {
  private val binding by viewBinding(FragmentPresetsBinding::bind)
  private val viewModel: AppViewModel by activityViewModels()

  private val titleSection by lazy {
    Section().apply {
      add(TitleItem(getString(R.string.presets)))
    }
  }
  private val presetsSection = Section()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.fragmentPresetsList.apply {
      layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false /* reverseLayout */)
      adapter = GroupAdapter<GroupieViewHolder>().apply {
        add(titleSection)
        add(presetsSection)
        setHasStableIds(true)
      }
    }

    viewModel.presets.observe(viewLifecycleOwner) { presets ->
      presetsSection.replaceAll(presets.map { PresetItem(it) })
    }
  }
}
