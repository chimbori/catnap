package com.chimbori.catnap

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.chimbori.catnap.FragmentIndex.ID_ABOUT

class AboutFragment : Fragment() {
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val v = inflater.inflate(R.layout.about_fragment, container, false)
    val packageInfo = try {
      val context: Context? = activity
      context!!.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
      throw RuntimeException("Can't find package?")
    }

    // Evaluate the format string in VersionText.
    val versionText = v.findViewById<View>(R.id.VersionText) as TextView
    val versionFormat = versionText.getText().toString()
    versionText.text = String.format(versionFormat, packageInfo.versionName)
    return v
  }

  override fun onResume() {
    super.onResume()
    (activity as MainActivity?)!!.setFragmentId(ID_ABOUT)
  }
}
