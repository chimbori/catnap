package com.chimbori.catnap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class OptionsFragment : Fragment(), OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
  private var mUiState: UIState? = null
  private var mMinVolSeek: SeekBar? = null
  private var mMinVolText: TextView? = null
  private var mPeriodSeek: SeekBar? = null
  private var mPeriodText: TextView? = null
  private var mAutoPlayCheck: SwitchCompat? = null
  private var mIgnoreAudioFocusCheck: SwitchCompat? = null
  private var mVolumeLimitCheck: SwitchCompat? = null
  private var mVolumeLimitSeek: SeekBar? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val v = inflater.inflate(R.layout.options_fragment, container, false)
    mMinVolSeek = v.findViewById<View>(R.id.MinVolSeek) as SeekBar
    mMinVolText = v.findViewById<View>(R.id.MinVolText) as TextView
    mPeriodSeek = v.findViewById<View>(R.id.PeriodSeek) as SeekBar
    mPeriodText = v.findViewById<View>(R.id.PeriodText) as TextView
    mAutoPlayCheck = v.findViewById<View>(R.id.AutoPlayCheck) as SwitchCompat
    mIgnoreAudioFocusCheck = v.findViewById<View>(R.id.IgnoreAudioFocusCheck) as SwitchCompat
    mVolumeLimitCheck = v.findViewById<View>(R.id.VolumeLimitCheck) as SwitchCompat
    mVolumeLimitSeek = v.findViewById<View>(R.id.VolumeLimitSeek) as SeekBar
    return v
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    mUiState = (activity as MainActivity?)!!.uIState
    val ph = mUiState!!.phonon!!
    mMinVolText!!.text = ph.minVolText
    mMinVolSeek!!.progress = ph.minVol
    mMinVolSeek!!.setOnSeekBarChangeListener(this)
    mPeriodText!!.text = ph.periodText
    mPeriodSeek!!.progress = ph.period
    // When the volume is at 100%, disable the period bar.
    mPeriodSeek!!.setEnabled(ph.minVol != 100)
    mPeriodSeek!!.setMax(PhononMutable.PERIOD_MAX)
    mPeriodSeek!!.setOnSeekBarChangeListener(this)
    mAutoPlayCheck!!.setChecked(mUiState!!.autoPlay)
    mAutoPlayCheck!!.setOnCheckedChangeListener(this)
    mIgnoreAudioFocusCheck!!.setChecked(mUiState!!.ignoreAudioFocus)
    mIgnoreAudioFocusCheck!!.setOnCheckedChangeListener(this)
    mVolumeLimitCheck!!.setOnCheckedChangeListener(this)
    mVolumeLimitSeek!!.setMax(UIState.MAX_VOLUME)
    mVolumeLimitSeek!!.setOnSeekBarChangeListener(this)
    redrawVolumeLimit()
  }

  override fun onResume() {
    super.onResume()
    (activity as MainActivity?)!!.setFragmentId(FragmentIndex.ID_OPTIONS)
  }

  override fun onProgressChanged(
    seekBar: SeekBar, progress: Int,
    fromUser: Boolean
  ) {
    if (!fromUser) {
      return
    }
    if (seekBar === mVolumeLimitSeek) {
      mUiState!!.volumeLimit = progress
      redrawVolumeLimit()
    } else {
      val phm = mUiState!!.phononMutable!!
      if (seekBar === mMinVolSeek) {
        phm.minVol = progress
        mMinVolText!!.text = phm.minVolText
        mPeriodSeek!!.setEnabled(progress != 100)
      } else if (seekBar === mPeriodSeek) {
        phm.period = progress
        mPeriodText!!.text = phm.periodText
      }
    }
    mUiState!!.sendIfDirty()
  }

  override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
    if (buttonView === mAutoPlayCheck) {
      mUiState!!.setAutoPlay(isChecked, true)
    } else if (buttonView === mIgnoreAudioFocusCheck) {
      mUiState!!.ignoreAudioFocus = isChecked
    } else if (buttonView === mVolumeLimitCheck) {
      mUiState!!.volumeLimitEnabled = isChecked
      redrawVolumeLimit()
    }
    mUiState!!.sendIfDirty()
  }

  override fun onStartTrackingTouch(seekBar: SeekBar) {}
  override fun onStopTrackingTouch(seekBar: SeekBar) {}
  private fun redrawVolumeLimit() {
    val enabled = mUiState!!.volumeLimitEnabled
    mVolumeLimitCheck!!.setChecked(enabled)
    mVolumeLimitSeek!!.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    mVolumeLimitSeek!!.progress = mUiState!!.volumeLimit
  }
}
