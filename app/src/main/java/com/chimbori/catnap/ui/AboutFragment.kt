package com.chimbori.catnap.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.chimbori.catnap.R
import com.chimbori.catnap.databinding.FragmentAboutBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding

class AboutFragment : Fragment(R.layout.fragment_about) {
  private val binding by viewBinding(FragmentAboutBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.fragmentAboutVersion.text = try {
      requireContext().packageManager.getPackageInfo(requireActivity().packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }?.versionName ?: "__"
  }
}
