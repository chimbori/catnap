package com.chimbori.catnap

import android.app.backup.BackupManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.chimbori.catnap.databinding.ActivityMainBinding
import com.chimbori.catnap.ui.AppViewModel
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val viewModel: AppViewModel by viewModels()

  private val navFragment by lazy { supportFragmentManager.findFragmentById(R.id.main_nav_host_container) as NavHostFragment }
  private val navController by lazy { navFragment.navController }

  public override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    DynamicColors.applyToActivityIfAvailable(this)
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.mainBottomNav.setupWithNavController(navController)
  }

  override fun onPause() {
    super.onPause()
    viewModel.saveState()
    BackupManager(this).dataChanged()
  }
}
