package com.chimbori.catnap

import android.app.backup.BackupManager
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.chimbori.catnap.NoiseService.PercentListener
import com.chimbori.catnap.UIState.LockListener
import com.chimbori.catnap.UIState.LockListener.LockEvent
import com.google.android.material.color.DynamicColors
import java.util.Date

class MainActivity : AppCompatActivity(), PercentListener, LockListener, OnItemSelectedListener {
  // Fragments can read this >= onActivityCreated().
  var uIState: UIState? = null
    private set

  private var mFragmentId = FragmentIndex.ID_MAIN
  private var mToolbarIcon: Drawable? = null
  private var mNavSpinner: Spinner? = null
  private var mServiceActive = false

  public override fun onCreate(savedInstanceState: Bundle?) {
    DynamicColors.applyToActivityIfAvailable(this)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    uIState = UIState(application)
    val pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    uIState!!.loadState(pref)
    val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
    setSupportActionBar(toolbar)
    val actionBar = supportActionBar
    actionBar!!.setDisplayHomeAsUpEnabled(true)
    actionBar.title = ""
    mNavSpinner = findViewById<View>(R.id.nav_spinner) as Spinner
    val adapter = ArrayAdapter(
      actionBar.themedContext, R.layout.spinner_title,
      FragmentIndex.getStrings(this)
    )
    adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
    mNavSpinner!!.setAdapter(adapter)
    mNavSpinner!!.onItemSelectedListener = this


    // Created a scaled-down icon for the Toolbar.
    run {
      // val tv = TypedValue()
      // getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)
      // val height = TypedValue.complexToDimensionPixelSize(tv.data, getResources().displayMetrics)
      // This originally used a scaled-down launcher icon, but I don't feel like figuring
      // out how to render R.mipmap.chromadoze_icon correctly.
      mToolbarIcon = ContextCompat.getDrawable(this, R.drawable.icon)
    }

    // When this Activity is first created, set up the initial fragment.
    // After a save/restore, the framework will drop in the last-used
    // fragment automatically.
    if (savedInstanceState == null) {
      changeFragment(MainFragment(), false)
    }
  }

  public override fun onResume() {
    super.onResume()
    // Start receiving progress events.
    NoiseService.addPercentListener(this)
    uIState!!.addLockListener(this)
    if (uIState!!.autoPlay) {
      uIState!!.sendToService()
    }
  }

  override fun onPause() {
    super.onPause()

    // If the equalizer is silent, stop the service.
    // This makes it harder to leave running accidentally.
    if (mServiceActive && uIState!!.phonon!!.isSilent) {
      NoiseService.stopNow(application, R.string.stop_reason_silent)
    }
    val pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
    pref.clear()
    uIState!!.saveState(pref)
    pref.commit()
    BackupManager(this).dataChanged()

    // Stop receiving progress events.
    NoiseService.removePercentListener(this)
    uIState!!.removeLockListener(this)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menu.add(0, MENU_PLAY_STOP, 0, getString(R.string.play_stop)).setShowAsAction(
      MenuItem.SHOW_AS_ACTION_ALWAYS
    )
    if (mFragmentId == FragmentIndex.ID_MAIN) {
      menu.add(0, MENU_LOCK, 0, getString(R.string.lock_unlock)).setShowAsAction(
        MenuItem.SHOW_AS_ACTION_ALWAYS
      )
    }
    return super.onCreateOptionsMenu(menu)
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    menu.findItem(MENU_PLAY_STOP).setIcon(if (mServiceActive) R.drawable.stop else R.drawable.play)
    val mi = menu.findItem(MENU_LOCK)
    mi?.setIcon(lockIcon)
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onLockStateChange(e: LockEvent) {
    // Redraw the lock icon for both event types.
    supportInvalidateOptionsMenu()
  }

  private val lockIcon: Drawable?
    // Get the lock icon which reflects the current action.
    get() {
      val d = ContextCompat.getDrawable(this, if (uIState!!.locked) R.drawable.lock else R.drawable.lock_open)
      if (uIState!!.lockBusy) {
        setColorFilterCompat(d, -0xbbbc, PorterDuff.Mode.SRC_IN)
      } else {
        d!!.clearColorFilter()
      }
      return d
    }

  override fun onSupportNavigateUp(): Boolean {
    // Rewind the back stack.
    supportFragmentManager.popBackStack(
      null,
      FragmentManager.POP_BACK_STACK_INCLUSIVE
    )
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      MENU_PLAY_STOP -> {
        // Force the service into its expected state.
        if (!mServiceActive) {
          uIState!!.sendToService()
        } else {
          NoiseService.stopNow(application, R.string.stop_reason_toolbar)
        }
        return true
      }
      MENU_LOCK -> {
        uIState!!.toggleLocked()
        supportInvalidateOptionsMenu()
        return true
      }
    }
    return false
  }

  override fun onNoiseServicePercentChange(percent: Int, stopTimestamp: Date, stopReasonId: Int) {
    val newServiceActive = percent >= 0
    if (mServiceActive != newServiceActive) {
      mServiceActive = newServiceActive

      // Redraw the "Play/Stop" button.
      supportInvalidateOptionsMenu()
    }
  }

  private fun changeFragment(f: Fragment, allowBack: Boolean) {
    val fragmentManager = supportFragmentManager

    // Prune the stack, so "back" always leads home.
    if (fragmentManager.backStackEntryCount > 0) {
      onSupportNavigateUp()
    }
    val transaction = fragmentManager.beginTransaction()
    transaction.replace(R.id.fragment_container, f)
    if (allowBack) {
      transaction.addToBackStack(null)
      transaction
        .setTransition(FragmentTransaction.TRANSIT_NONE)
    }
    transaction.commit()
  }

  // Each fragment calls this from onResume to tweak the ActionBar.
  fun setFragmentId(id: Int) {
    mFragmentId = id
    val enableUp = id != FragmentIndex.ID_MAIN
    val actionBar = supportActionBar
    supportInvalidateOptionsMenu()

    // Use the default left arrow, or a scaled-down Chroma Doze icon.
    actionBar!!.setHomeAsUpIndicator(if (enableUp) null else mToolbarIcon)

    // When we're on the main page, make the icon non-clickable.
    val navUp = findImageButton(findViewById(R.id.toolbar))
    if (navUp != null) {
      navUp.isClickable = enableUp
    }
    mNavSpinner!!.setSelection(id)
  }

  // Handle nav_spinner selection.
  override fun onItemSelected(
    parent: AdapterView<*>?, view: View, position: Int,
    id: Long
  ) {
    if (position == mFragmentId) {
      return
    }
    when (position) {
      FragmentIndex.ID_MAIN -> {
        onSupportNavigateUp()
        return
      }
      FragmentIndex.ID_OPTIONS -> {
        changeFragment(OptionsFragment(), true)
        return
      }
      FragmentIndex.ID_MEMORY -> {
        changeFragment(MemoryFragment(), true)
        return
      }
      FragmentIndex.ID_ABOUT -> {
        changeFragment(AboutFragment(), true)
        return
      }
    }
  }

  override fun onNothingSelected(parent: AdapterView<*>?) {}

  companion object {
    // The name to use when accessing our SharedPreferences.
    const val PREF_NAME = "app"
    private const val MENU_PLAY_STOP = 1
    private const val MENU_LOCK = 2

    @Suppress("deprecation")
    private fun setColorFilterCompat(drawable: Drawable?, color: Int, mode: PorterDuff.Mode) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        drawable!!.colorFilter = BlendModeColorFilter(color, BlendMode.valueOf(mode.name))
      } else {
        drawable!!.setColorFilter(color, mode)
      }
    }

    // Search a View for the first ImageButton.  We use it to locate the
    // home/up button in a Toolbar.
    private fun findImageButton(view: View): ImageButton? {
      if (view is ImageButton) {
        return view
      } else if (view is ViewGroup) {
        val vg = view
        for (i in 0 until vg.childCount) {
          val found = findImageButton(vg.getChildAt(i))
          if (found != null) {
            return found
          }
        }
      }
      return null
    }
  }
}
