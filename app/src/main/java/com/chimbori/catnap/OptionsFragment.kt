package com.chimbori.catnap

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.databinding.FragmentOptionsBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding

class OptionsFragment : Fragment(R.layout.fragment_options) {
  private val binding by viewBinding(FragmentOptionsBinding::bind)
  private val viewModel: AppViewModel by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.fragmentOptionsMinimumVolumeSeekbar.apply {
      max = MAX_VOLUME
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener(viewModel::setMinimumVolume))
    }
    binding.fragmentOptionsPeriodSeekbar.apply {
      max = PERIOD_MAX
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener(viewModel::setPeriod))
    }
    binding.fragmentOptionsAutoPlayCheckbox.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setAutoPlay(isChecked)
    }
    binding.fragmentOptionsIgnoreAudioFocusCheckbox.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setIgnoreAudioFocus(isChecked)
    }
    binding.fragmentOptionsVolumeLimitCheckbox.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setVolumeLimitEnabled(isChecked)
    }
    binding.fragmentOptionsVolumeLimitSeekbar.apply {
      max = MAX_VOLUME
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener(viewModel::setVolumeLimit))
    }

    viewModel.activePhonon.observe(viewLifecycleOwner) { phonon ->
      binding.fragmentOptionsMinimumVolumeSeekbar.progress = phonon.minimumVolume
      binding.fragmentOptionsMinimumVolumeText.text = phonon.minimumVolume.asPercent()
      binding.fragmentOptionsPeriodSeekbar.isEnabled = phonon.minimumVolume != MAX_VOLUME
      binding.fragmentOptionsPeriodSeekbar.progress = phonon.period
      binding.fragmentOptionsPeriodText.text = phonon.periodText
    }
    viewModel.autoPlay.observe(viewLifecycleOwner) { autoPlay ->
      binding.fragmentOptionsAutoPlayCheckbox.isChecked = autoPlay
    }
    viewModel.ignoreAudioFocus.observe(viewLifecycleOwner) { isChecked ->
      binding.fragmentOptionsIgnoreAudioFocusCheckbox.isChecked = isChecked
    }
    viewModel.volumeLimitEnabled.observe(viewLifecycleOwner) { isEnabled ->
      binding.fragmentOptionsVolumeLimitCheckbox.isChecked = isEnabled
      binding.fragmentOptionsVolumeLimitSeekbar.isVisible = isEnabled
    }
    viewModel.volumeLimit.observe(viewLifecycleOwner) { volumeLimit ->
      binding.fragmentOptionsVolumeLimitSeekbar.progress = volumeLimit
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
