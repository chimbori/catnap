package com.chimbori.catnap.ui

import android.os.Bundle
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chimbori.catnap.NoiseService
import com.chimbori.catnap.NoiseService.Companion.stopNoiseService
import com.chimbori.catnap.NoiseService.PercentListener
import com.chimbori.catnap.R
import com.chimbori.catnap.databinding.FragmentMainBinding
import com.chimbori.catnap.utils.nonNullValue
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import java.text.DateFormat
import java.util.Date

class MainFragment : Fragment(R.layout.fragment_main) {
  private val binding by viewBinding(FragmentMainBinding::bind)
  private val viewModel: AppViewModel by activityViewModels()

  private var isServiceActive = false

  private val noisePercentListener = PercentListener { percent, stopTimestamp, stopReasonId ->
    var showGenerating = false
    var showStopReason = false
    isServiceActive = percent >= 0

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

    binding.fragmentMainPlayStopButtonIcon.setImageResource(
      if (isServiceActive) R.drawable.stop else R.drawable.play
    )

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

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.fragmentMainEqualizer.apply {
      addBarChangedListener { band, value -> viewModel.setBar(band, value) }
      addInteractedWhileLockedListener { viewModel.setInteractedWhileLocked(it) }
      addInteractionCompleteListener { viewModel.setPhononEditComplete() }
    }
    binding.fragmentMainPlayStopButton.setOnClickListener {
      if (!isServiceActive) {
        viewModel.startService()
      } else {
        context?.stopNoiseService(R.string.stop_reason_toolbar)
      }
    }
    binding.fragmentMainLockButton.setOnClickListener { viewModel.toggleLocked() }
    binding.fragmentMainSaveButton.setOnClickListener { viewModel.savePhononAsPreset() }

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

  override fun onStart() {
    super.onStart()
    NoiseService.addPercentListener(noisePercentListener)
  }

  override fun onStop() {
    super.onStop()
    NoiseService.removePercentListener(noisePercentListener)
  }
}