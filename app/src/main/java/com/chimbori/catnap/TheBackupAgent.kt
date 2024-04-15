package com.chimbori.catnap

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper
import com.chimbori.catnap.MainActivity.Companion.PREF_NAME

// This implements a BackupAgent, not because the data is particularly
// valuable, but because Android 6.0 will randomly kill the service in the
// middle of the night to perform a "fullbackup" if we don't offer an
// alternative (or disable backups entirely.)
class TheBackupAgent : BackupAgentHelper() {
  override fun onCreate() {
    addHelper(PREF_BACKUP_KEY, SharedPreferencesBackupHelper(this, PREF_NAME))
  }

  companion object {
    private const val PREF_BACKUP_KEY = "pref"
  }
}
