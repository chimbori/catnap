package com.chimbori.catnap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.chimbori.catnap.NoiseService.PercentListener
import java.text.DateFormat
import java.util.Date

class MainFragment : Fragment(), PercentListener {
  private var mEqualizer: EqualizerView? = null
  private var mStateText: TextView? = null
  private var mPercentBar: ProgressBar? = null
  private var mUiState: UIState? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val v = inflater.inflate(R.layout.main_fragment, container, false)
    mEqualizer = v.findViewById<View>(R.id.EqualizerView) as EqualizerView
    mStateText = v.findViewById<View>(R.id.StateText) as TextView
    mPercentBar = v.findViewById<View>(R.id.PercentBar) as ProgressBar
    return v
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    mUiState = (activity as MainActivity).uIState
    mEqualizer!!.setUiState(mUiState)
  }

  override fun onResume() {
    super.onResume()
    // Start receiving progress events.
    NoiseService.addPercentListener(this)
    mUiState!!.addLockListener(mEqualizer)
    (activity as MainActivity?)!!.setFragmentId(FragmentIndex.ID_MAIN)
  }

  override fun onPause() {
    super.onPause()
    // Stop receiving progress events.
    NoiseService.removePercentListener(this)
    mUiState!!.removeLockListener(mEqualizer)
  }

  override fun onNoiseServicePercentChange(percent: Int, stopTimestamp: Date, stopReasonId: Int) {
    var showGenerating = false
    var showStopReason = false
    if (percent < 0) {
      mPercentBar!!.visibility = View.INVISIBLE
      // While the service is stopped, show what event caused it to stop.
      showStopReason = stopReasonId != 0
    } else if (percent < 100) {
      mPercentBar!!.visibility = View.VISIBLE
      mPercentBar!!.progress = percent
      showGenerating = true
    } else {
      mPercentBar!!.visibility = View.INVISIBLE
      // While the service is active, only the restart event is worth showing.
      showStopReason = stopReasonId == R.string.stop_reason_restarted
    }
    if (showStopReason) {
      // Expire the message after 12 hours, to avoid date ambiguity.
      val diff = Date().time - stopTimestamp.time
      if (diff > 12 * 3600 * 1000L) {
        showStopReason = false
      }
    }
    if (showGenerating) {
      mStateText!!.setText(R.string.generating)
    } else if (showStopReason) {
      val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT).format(stopTimestamp)
      mStateText!!.text = "$timeFmt: ${getString(stopReasonId)}"
    } else {
      mStateText!!.text = ""
    }
  }
}
