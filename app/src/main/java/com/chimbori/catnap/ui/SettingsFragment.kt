package com.chimbori.catnap.ui

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.MAX_VOLUME
import com.chimbori.catnap.PERIOD_MAX
import com.chimbori.catnap.R
import com.chimbori.catnap.asPercent
import com.chimbori.catnap.databinding.FragmentSettingsBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding


class SettingsFragment : Fragment(R.layout.fragment_settings) {
  private val binding by viewBinding(FragmentSettingsBinding::bind)
  private val viewModel: AppViewModel by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.fragmentSettingsMinimumVolumeSeekbar.apply {
      max = MAX_VOLUME
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener(viewModel::setMinimumVolume))
    }
    binding.fragmentSettingsPeriodSeekbar.apply {
      max = PERIOD_MAX
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener(viewModel::setPeriod))
    }
    binding.fragmentSettingsAutoPlayCheckbox.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setAutoPlay(isChecked)
    }
    binding.fragmentSettingsIgnoreAudioFocusCheckbox.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setIgnoreAudioFocus(isChecked)
    }
    binding.fragmentSettingsVolumeLimitCheckbox.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setVolumeLimitEnabled(isChecked)
    }
    binding.fragmentSettingsVolumeLimitSeekbar.apply {
      max = MAX_VOLUME
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener(viewModel::setVolumeLimit))
    }

    viewModel.activePreset.observe(viewLifecycleOwner) { preset ->
      binding.fragmentSettingsMinimumVolumeSeekbar.progress = preset.minimumVolume
      binding.fragmentSettingsMinimumVolumeText.text = preset.minimumVolume.asPercent()
      binding.fragmentSettingsPeriodSeekbar.isEnabled = preset.minimumVolume != MAX_VOLUME
      binding.fragmentSettingsPeriodSeekbar.progress = preset.period
      binding.fragmentSettingsPeriodText.text = preset.periodText
    }
    viewModel.autoPlay.observe(viewLifecycleOwner) { autoPlay ->
      binding.fragmentSettingsAutoPlayCheckbox.isChecked = autoPlay
    }
    viewModel.ignoreAudioFocus.observe(viewLifecycleOwner) { isChecked ->
      binding.fragmentSettingsIgnoreAudioFocusCheckbox.isChecked = isChecked
    }
    viewModel.volumeLimitEnabled.observe(viewLifecycleOwner) { isEnabled ->
      binding.fragmentSettingsVolumeLimitCheckbox.isChecked = isEnabled
      binding.fragmentSettingsVolumeLimitSeekbar.isVisible = isEnabled
    }
    viewModel.volumeLimit.observe(viewLifecycleOwner) { volumeLimit ->
      binding.fragmentSettingsVolumeLimitSeekbar.progress = volumeLimit
    }
  }
}

private fun interface SimpleSeekBarChangeListener : OnSeekBarChangeListener {
  fun onProgressChanged(progress: Int)
  override fun onStartTrackingTouch(seekBar: SeekBar?) {}
  override fun onStopTrackingTouch(seekBar: SeekBar?) {}
  override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
    if (fromUser) {
      onProgressChanged(progress)
    }
  }
}
