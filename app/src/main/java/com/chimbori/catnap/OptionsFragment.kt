package com.chimbori.catnap

import android.os.Bundle
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.PhononMutable.Companion.PERIOD_MAX
import com.chimbori.catnap.UIState.Companion.MAX_VOLUME
import com.chimbori.catnap.databinding.FragmentOptionsBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding

class OptionsFragment : Fragment(R.layout.fragment_options) {
  private val binding by viewBinding(FragmentOptionsBinding::bind)
  private val mUiState: UIState by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val ph = mUiState.phonon!!

    binding.fragmentOptionsMinimumVolumeText.text = ph.minVolText
    binding.fragmentOptionsMinimumVolumeSeekbar.apply {
      progress = ph.minVol
      setMax(MAX_VOLUME)
      setOnSeekBarChangeListener(object : SimpleSeekBarChangeListener {
        override fun onProgressChanged(progress: Int) {
          mUiState.phononMutable!!.minVol = progress
          binding.fragmentOptionsMinimumVolumeText.text = mUiState.phononMutable!!.minVolText
          binding.fragmentOptionsPeriodSeekbar.setEnabled(progress != 100)
          mUiState.restartServiceIfRequired()
        }
      })
    }

    binding.fragmentOptionsPeriodText.text = ph.periodText

    binding.fragmentOptionsPeriodSeekbar.apply {
      progress = ph.period
      setEnabled(ph.minVol != 100)  // When the volume is at 100%, disable the period bar.
      setMax(PERIOD_MAX)
      setOnSeekBarChangeListener(object : SimpleSeekBarChangeListener {
        override fun onProgressChanged(progress: Int) {
          mUiState.phononMutable!!.period = progress
          binding.fragmentOptionsPeriodText.text = mUiState.phononMutable!!.periodText
          mUiState.restartServiceIfRequired()
        }
      })
    }

    binding.fragmentOptionsAutoPlayCheckbox.apply {
      setChecked(mUiState.autoPlay)
      setOnCheckedChangeListener { _, isChecked ->
        mUiState.setAutoPlay(isChecked, true)
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

private interface SimpleSeekBarChangeListener : OnSeekBarChangeListener {
  fun onProgressChanged(progress: Int)
  override fun onStartTrackingTouch(seekBar: SeekBar?) {}
  override fun onStopTrackingTouch(seekBar: SeekBar?) {}
  override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
    if (fromUser) {
      onProgressChanged(progress)
    }
  }
}
