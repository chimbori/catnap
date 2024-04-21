package com.chimbori.catnap

import android.app.backup.BackupManager
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.SHOW_AS_ACTION_ALWAYS
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.chimbori.catnap.NoiseService.Companion.stopNoiseService
import com.chimbori.catnap.NoiseService.PercentListener
import com.chimbori.catnap.databinding.ActivityMainBinding
import com.chimbori.catnap.utils.nonNullValue
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val uIState: UIState by viewModels()

  private val navFragment by lazy { supportFragmentManager.findFragmentById(R.id.main_nav_host_container) as NavHostFragment }
  private val navController by lazy { navFragment.navController }

  public override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    DynamicColors.applyToActivityIfAvailable(this)
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    binding.mainBottomNav.setupWithNavController(navController)

    val pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    uIState.loadState(pref)
  }

  public override fun onResume() {
    super.onResume()
    if (uIState.autoPlay) {
      uIState.startService()
    }
  }

  override fun onPause() {
    super.onPause()

    val pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
    pref.clear()
    uIState.saveState(pref)
    pref.commit()
    BackupManager(this).dataChanged()
  }

  companion object {
    const val PREF_NAME = "app"  // The name to use when accessing our SharedPreferences.
  }
}
