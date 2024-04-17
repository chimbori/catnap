package com.chimbori.catnap

import android.os.Bundle
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.NoiseService.PercentListener
import com.chimbori.catnap.databinding.FragmentMainBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import java.text.DateFormat
import java.util.Date

class MainFragment : Fragment(R.layout.fragment_main), PercentListener {
  private val binding by viewBinding(FragmentMainBinding::bind)
  private val mUiState: UIState by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.fragmentMainEqualizer.setUiState(mUiState)
  }

  override fun onResume() {
    super.onResume()
    // Start receiving progress events.
    NoiseService.addPercentListener(this)
    mUiState.addLockListener(binding.fragmentMainEqualizer)
  }

  override fun onPause() {
    super.onPause()
    // Stop receiving progress events.
    NoiseService.removePercentListener(this)
    mUiState.removeLockListener(binding.fragmentMainEqualizer)
  }

  override fun onNoiseServicePercentChange(percent: Int, stopTimestamp: Date, stopReasonId: Int) {
    var showGenerating = false
    var showStopReason = false
    if (percent < 0) {
      binding.fragmentMainIdctPercent.visibility = INVISIBLE
      // While the service is stopped, show what event caused it to stop.
      showStopReason = stopReasonId != 0
    } else if (percent < 100) {
      binding.fragmentMainIdctPercent.visibility = VISIBLE
      binding.fragmentMainIdctPercent.progress = percent
      showGenerating = true
    } else {
      binding.fragmentMainIdctPercent.visibility = INVISIBLE
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
      binding.fragmentMainState.setText(R.string.generating)
    } else if (showStopReason) {
      val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT).format(stopTimestamp)
      binding.fragmentMainState.text = "$timeFmt: ${getString(stopReasonId)}"
    } else {
      binding.fragmentMainState.text = ""
    }
  }
}
