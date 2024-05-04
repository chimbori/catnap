package com.chimbori.catnap

import android.os.Bundle
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.Phonon.Companion.PERIOD_MAX
import com.chimbori.catnap.UIState.Companion.MAX_VOLUME
import com.chimbori.catnap.databinding.FragmentOptionsBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding

class OptionsFragment : Fragment(R.layout.fragment_options) {
  private val binding by viewBinding(FragmentOptionsBinding::bind)
  private val mUiState: UIState by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.fragmentOptionsMinimumVolumeText.text = mUiState.activePhonon.minimumVolume.asPercent()
    binding.fragmentOptionsMinimumVolumeSeekbar.apply {
      progress = mUiState.activePhonon.minimumVolume
      setMax(MAX_VOLUME)
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener { progress ->
        mUiState.setMinimumVolume(progress)
        binding.fragmentOptionsMinimumVolumeText.text = mUiState.activePhonon.minimumVolume.asPercent()
        binding.fragmentOptionsPeriodSeekbar.setEnabled(progress != 100)
        mUiState.restartServiceIfRequired()
      })
    }

    binding.fragmentOptionsPeriodText.text = mUiState.activePhonon.periodText

    binding.fragmentOptionsPeriodSeekbar.apply {
      progress = mUiState.activePhonon.period
      setEnabled(mUiState.activePhonon.minimumVolume != 100)  // When the volume is at 100%, disable the period bar.
      setMax(PERIOD_MAX)
      setOnSeekBarChangeListener(SimpleSeekBarChangeListener { progress ->
        mUiState.setPeriod(progress)
        binding.fragmentOptionsPeriodText.text = mUiState.activePhonon.periodText
        mUiState.restartServiceIfRequired()
      })
    }

    binding.fragmentOptionsAutoPlayCheckbox.apply {
      setChecked(mUiState.autoPlay)
      setOnCheckedChangeListener { _, isChecked ->
        mUiState.setAutoPlay(isChecked)
        mUiState.restartServiceIfRequired()
      }
    }

    binding.fragmentOptionsIgnoreAudioFocusCheckbox.apply {
      setChecked(mUiState.ignoreAudioFocus)
      setOnCheckedChangeListener { _, isChecked ->
        mUiState.ignoreAudioFocus = isChecked
        mUiState.restartServiceIfRequired()
      }
    }

    binding.fragmentOptionsVolumeLimitCheckbox.setOnCheckedChangeListener { _, isChecked ->
      mUiState.volumeLimitEnabled = isChecked
      redrawVolumeLimit()
      mUiState.restartServiceIfRequired()
    }

    redrawVolumeLimit()
  }

  private fun redrawVolumeLimit() {
    val enabled = mUiState.volumeLimitEnabled
    binding.fragmentOptionsVolumeLimitCheckbox.setChecked(enabled)
    binding.fragmentOptionsVolumeLimitSeekbar.apply {
      visibility = if (enabled) VISIBLE else INVISIBLE
      progress = mUiState.volumeLimit
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
