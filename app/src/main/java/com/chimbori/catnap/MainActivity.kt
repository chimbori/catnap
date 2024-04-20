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

  private val noisePercentListener = PercentListener { percent, _, _ ->
    val newServiceActive = percent >= 0
    if (mServiceActive != newServiceActive) {
      mServiceActive = newServiceActive
      supportInvalidateOptionsMenu()  // Redraw the "Play/Stop" button.
    }
  }

  private var mServiceActive = false

  public override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    DynamicColors.applyToActivityIfAvailable(this)
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    binding.mainBottomNav.setupWithNavController(navController)

    val pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    uIState.loadState(pref)
    setSupportActionBar(binding.mainToolbar)

    // Redraw the lock icon for both event types.
    uIState.isLocked.observe(this) { supportInvalidateOptionsMenu() }
    uIState.interactedWhileLocked.observe(this) { supportInvalidateOptionsMenu() }
  }

  public override fun onResume() {
    super.onResume()
    // Start receiving progress events.
    NoiseService.addPercentListener(noisePercentListener)
    if (uIState.autoPlay) {
      uIState.startService()
    }
  }

  override fun onPause() {
    super.onPause()

    // If the equalizer is silent, stop the service.
    // This makes it harder to leave running accidentally.
    if (mServiceActive && uIState.phonon!!.isSilent) {
      stopNoiseService(R.string.stop_reason_silent)
    }
    val pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
    pref.clear()
    uIState.saveState(pref)
    pref.commit()
    BackupManager(this).dataChanged()

    // Stop receiving progress events.
    NoiseService.removePercentListener(noisePercentListener)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menu.add(0, MENU_PLAY_STOP, 0, getString(R.string.play_stop)).setShowAsAction(SHOW_AS_ACTION_ALWAYS)
    menu.add(0, MENU_LOCK, 0, getString(R.string.lock_unlock)).setShowAsAction(SHOW_AS_ACTION_ALWAYS)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    menu.findItem(MENU_PLAY_STOP).setIcon(if (mServiceActive) R.drawable.stop else R.drawable.play)
    val mi = menu.findItem(MENU_LOCK)
    mi?.setIcon(lockIcon)
    return super.onPrepareOptionsMenu(menu)
  }

  private val lockIcon: Drawable?
    // Get the lock icon which reflects the current action.
    get() {
      val d = ContextCompat.getDrawable(
        this,
        if (uIState.isLocked.nonNullValue) R.drawable.lock else R.drawable.lock_open
      )
      if (uIState.interactedWhileLocked.nonNullValue) {
        setColorFilterCompat(d, -0xbbbc, PorterDuff.Mode.SRC_IN)
      } else {
        d!!.clearColorFilter()
      }
      return d
    }

  override fun onSupportNavigateUp(): Boolean {
    supportFragmentManager.popBackStack(null, POP_BACK_STACK_INCLUSIVE)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      MENU_PLAY_STOP -> {  // Force the service into its expected state.
        if (!mServiceActive) {
          uIState.startService()
        } else {
          stopNoiseService(R.string.stop_reason_toolbar)
        }
        return true
      }
      MENU_LOCK -> {
        uIState.toggleLocked()
        supportInvalidateOptionsMenu()
        return true
      }
    }
    return false
  }

  companion object {
    // The name to use when accessing our SharedPreferences.
    const val PREF_NAME = "app"
    private const val MENU_PLAY_STOP = 1
    private const val MENU_LOCK = 2

    @Suppress("deprecation")
    private fun setColorFilterCompat(drawable: Drawable?, color: Int, mode: PorterDuff.Mode) {
      if (SDK_INT >= Q) {
        drawable!!.colorFilter = BlendModeColorFilter(color, BlendMode.valueOf(mode.name))
      } else {
        drawable!!.setColorFilter(color, mode)
      }
    }
  }
}
