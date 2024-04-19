package com.chimbori.catnap

import android.os.Bundle
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.PhononMutable.Companion.PERIOD_MAX
import com.chimbori.catnap.UIState.Companion.MAX_VOLUME
import com.chimbori.catnap.databinding.FragmentOptionsBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding

class OptionsFragment : Fragment(R.layout.fragment_options), OnSeekBarChangeListener,
  CompoundButton.OnCheckedChangeListener {
  private val binding by viewBinding(FragmentOptionsBinding::bind)
  private val mUiState: UIState by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val ph = mUiState.phonon!!
    binding.fragmentOptionsMinimumVolumeText.text = ph.minVolText

    binding.fragmentOptionsMinimumVolumeSeekbar.progress = ph.minVol
    binding.fragmentOptionsMinimumVolumeSeekbar.setOnSeekBarChangeListener(this)

    binding.fragmentOptionsPeriodText.text = ph.periodText

    binding.fragmentOptionsPeriodSeekbar.progress = ph.period
    binding.fragmentOptionsPeriodSeekbar.setEnabled(ph.minVol != 100)  // When the volume is at 100%, disable the period bar.
    binding.fragmentOptionsPeriodSeekbar.setMax(PERIOD_MAX)
    binding.fragmentOptionsPeriodSeekbar.setOnSeekBarChangeListener(this)

    binding.fragmentOptionsAutoPlayCheckbox.setChecked(mUiState.autoPlay)
    binding.fragmentOptionsAutoPlayCheckbox.setOnCheckedChangeListener(this)

    binding.fragmentOptionsIgnoreAudioFocusCheckbox.setChecked(mUiState.ignoreAudioFocus)
    binding.fragmentOptionsIgnoreAudioFocusCheckbox.setOnCheckedChangeListener(this)

    binding.fragmentOptionsVolumeLimitCheckbox.setOnCheckedChangeListener(this)

    binding.fragmentOptionsMinimumVolumeSeekbar.setMax(MAX_VOLUME)
    binding.fragmentOptionsMinimumVolumeSeekbar.setOnSeekBarChangeListener(this)
    redrawVolumeLimit()
  }

  override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
    if (!fromUser) {
      return
    }
    if (seekBar === binding.fragmentOptionsVolumeLimitSeekbar) {
      mUiState.volumeLimit = progress
      redrawVolumeLimit()
    } else {
      val phm = mUiState.phononMutable!!
      if (seekBar === binding.fragmentOptionsMinimumVolumeSeekbar) {
        phm.minVol = progress
        binding.fragmentOptionsMinimumVolumeText.text = phm.minVolText
        binding.fragmentOptionsPeriodSeekbar.setEnabled(progress != 100)
      } else if (seekBar === binding.fragmentOptionsPeriodSeekbar) {
        phm.period = progress
        binding.fragmentOptionsPeriodText.text = phm.periodText
      }
    }
    mUiState.restartServiceIfRequired()
  }

  override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
    if (buttonView === binding.fragmentOptionsAutoPlayCheckbox) {
      mUiState.setAutoPlay(isChecked, true)
    } else if (buttonView === binding.fragmentOptionsIgnoreAudioFocusCheckbox) {
      mUiState.ignoreAudioFocus = isChecked
    } else if (buttonView === binding.fragmentOptionsVolumeLimitCheckbox) {
      mUiState.volumeLimitEnabled = isChecked
      redrawVolumeLimit()
    }
    mUiState.restartServiceIfRequired()
  }

  override fun onStartTrackingTouch(seekBar: SeekBar) {}
  override fun onStopTrackingTouch(seekBar: SeekBar) {}
  private fun redrawVolumeLimit() {
    val enabled = mUiState.volumeLimitEnabled
    binding.fragmentOptionsVolumeLimitCheckbox.setChecked(enabled)
    binding.fragmentOptionsVolumeLimitSeekbar.visibility = if (enabled) VISIBLE else INVISIBLE
    binding.fragmentOptionsVolumeLimitSeekbar.progress = mUiState.volumeLimit
  }
}
