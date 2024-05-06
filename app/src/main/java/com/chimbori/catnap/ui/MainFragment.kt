package com.chimbori.catnap.ui

import android.os.Bundle
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.R
import com.chimbori.catnap.databinding.DialogPresetConfigBinding
import com.chimbori.catnap.databinding.FragmentMainBinding
import com.chimbori.catnap.utils.nonNullValue
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding

class MainFragment : Fragment(R.layout.fragment_main) {
  private val binding by viewBinding(FragmentMainBinding::bind)
  private val viewModel: AppViewModel by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel.playStopIconResId.observe(viewLifecycleOwner) { iconResId ->
      binding.fragmentMainPlayStopButtonIcon.setImageResource(iconResId)
    }
    viewModel.statusPercent.observe(viewLifecycleOwner) { statusPercent ->
      binding.fragmentMainIdctPercent.visibility = if (statusPercent in 1..99) VISIBLE else INVISIBLE
      binding.fragmentMainIdctPercent.progress = statusPercent
    }
    viewModel.statusText.observe(viewLifecycleOwner) { statusText ->
      binding.fragmentMainState.visibility = if (statusText != null) VISIBLE else INVISIBLE
      binding.fragmentMainState.text = statusText
    }

    binding.fragmentMainEqualizer.apply {
      addBarChangedListener { band, value -> viewModel.setBar(band, value) }
      addInteractedWhileLockedListener { viewModel.setInteractedWhileLocked(it) }
      addInteractionCompleteListener { viewModel.setPhononEditComplete() }
    }
    binding.fragmentMainPlayStopButton.setOnClickListener {
      viewModel.togglePlayStop()
    }
    binding.fragmentMainLockButton.setOnClickListener { viewModel.toggleLocked() }
    binding.fragmentMainSaveButton.setOnClickListener {
      val bottomSheet = BottomSheetDialog(requireActivity())
      val presetConfig = DialogPresetConfigBinding.inflate(layoutInflater).apply {
        dialogPresetTitleSaveButton.setOnClickListener {
          viewModel.savePhononAsPreset(name = dialogPresetTitleTitle.text.toString())
          bottomSheet.dismiss()
        }
        dialogPresetTitleCancelButton.setOnClickListener { bottomSheet.dismiss() }
        dialogPresetTitleTitle.setText(viewModel.getNextPresetName())
      }
      bottomSheet.apply {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED  // Needed for Tablets in Landscape Mode
        setContentView(presetConfig.root)
      }.show()
    }

    viewModel.activePhonon.observe(viewLifecycleOwner) { binding.fragmentMainEqualizer.phonon = it }
    viewModel.isLocked.observe(viewLifecycleOwner) { binding.fragmentMainEqualizer.isLocked = it }
    viewModel.isLocked.observe(viewLifecycleOwner) { isLocked ->
      binding.fragmentMainEqualizer.isLocked = isLocked
      binding.fragmentMainLockButtonIcon.setImageResource(if (viewModel.isLocked.nonNullValue) R.drawable.lock else R.drawable.lock_open)
    }
    viewModel.interactedWhileLocked.observe(viewLifecycleOwner) {
      binding.fragmentMainLockStatus.isVisible = it
    }
  }
}
