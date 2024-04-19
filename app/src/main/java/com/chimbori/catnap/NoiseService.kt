package com.chimbori.catnap

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Date

class NoiseService : Service() {
  private var mSampleShuffler: SampleShuffler? = null
  private var mSampleGenerator: SampleGenerator? = null
  private var mAudioFocusHelper: AudioFocusHelper? = null
  private var mWakeLock: WakeLock? = null
  private var lastStartId = -1
  private var mPercentHandler: Handler? = null

  override fun onCreate() {
    // Set up a message handler in the main thread.
    mPercentHandler = PercentHandler()
    val params = AudioParams()
    mSampleShuffler = SampleShuffler(params)
    mSampleGenerator = SampleGenerator(this, params, mSampleShuffler!!)
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "catnap:NoiseService")
    mWakeLock!!.acquire()
    val name: CharSequence = getString(R.string.channel_name)
    val description = getString(R.string.channel_description)
    val importance = NotificationManagerCompat.IMPORTANCE_LOW
    NotificationManagerCompat.from(this).createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, importance)
        .setName(name)
        .setDescription(description)
        .build()
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFY_ID, makeNotify(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
      startForeground(NOTIFY_ID, makeNotify())
    }

    // Note: This leaks memory if I use "this" instead of "getApplicationContext()".
    mAudioFocusHelper = AudioFocusHelper(
      applicationContext, mSampleShuffler!!.volumeListener
    )
  }

  @Suppress("deprecation")
  private fun stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      stopForeground(true)
    }
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    // When multiple spectra arrive, only the latest should remain active.
    if (lastStartId >= 0) {
      stopSelf(lastStartId)
      lastStartId = -1
    }

    // Handle the Stop intent.
    val stopReasonId = intent.getIntExtra("stopReasonId", 0)
    if (stopReasonId != 0) {
      saveStopReason(stopReasonId)
      stopSelf(startId)
      return START_NOT_STICKY
    }

    // Notify the user that the OS restarted the process.
    if (flags and START_FLAG_REDELIVERY != 0) {
      saveStopReason(R.string.stop_reason_restarted)
    }
    val spectrum = getParcelableExtraCompat(intent, "spectrum", SpectrumData::class.java)!!

    // Synchronous updates.
    mSampleShuffler!!.setAmpWave(
      intent.getFloatExtra("minvol", -1f),
      intent.getFloatExtra("period", -1f)
    )
    mSampleShuffler!!.volumeListener.setVolumeLevel(
      intent.getFloatExtra("volumeLimit", -1f)
    )
    mAudioFocusHelper!!.setActive(
      !intent.getBooleanExtra("ignoreAudioFocus", false)
    )

    // Background updates.
    mSampleGenerator!!.updateSpectrum(spectrum)

    // If the kernel decides to kill this process, let Android restart it
    // using the most-recent spectrum.  It's important that we call
    // stopSelf() with this startId when a replacement spectrum arrives,
    // or if we're stopping the service intentionally.
    lastStartId = startId
    return START_REDELIVER_INTENT
  }

  override fun onDestroy() {
    if (lastStartId != -1) {
      // This condition can be triggered from adb shell:
      // $ am stopservice net.pmarks.chromadoze/.NoiseService
      saveStopReason(R.string.stop_reason_mysterious)
    }
    mSampleGenerator!!.stopThread()
    mSampleShuffler!!.stopThread()
    mPercentHandler!!.removeMessages(PERCENT_MSG)
    updatePercent(-1)
    mAudioFocusHelper!!.setActive(false)
    stopForegroundCompat()
    mWakeLock!!.release()
  }

  override fun onBind(intent: Intent): IBinder? {
    // Don't use binding.
    return null
  }

  // Create an icon for the notification bar.
  private fun makeNotify(): Notification {
    val b = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.power_sleep)
      .setWhen(0)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentIntent(
        PendingIntent.getActivity(
          this,
          0,
          Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER),
          FLAG_IMMUTABLE
        )
      )
    val rv = RemoteViews(
      packageName, R.layout.notification_with_stop_button
    )
    rv.setOnClickPendingIntent(
      R.id.stop_button, PendingIntent.getService(
        this, 0, createStopIntent(this, R.string.stop_reason_notification),
        FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
      )
    )

    // Temporarily inflate the notification, to copy colors from its default style.
    val inflated = rv.apply(this, FrameLayout(this))
    val titleText = inflated.findViewById<TextView>(R.id.title)
    rv.setInt(R.id.divider, "setBackgroundColor", titleText.textColors.defaultColor)
    rv.setInt(R.id.stop_button_square, "setBackgroundColor", titleText.textColors.defaultColor)

    // It would be nice if there were some way to omit the "expander affordance",
    // but this seems good enough.
    b.setCustomContentView(rv)
    b.setStyle(NotificationCompat.DecoratedCustomViewStyle())
    return b.build()
  }

  // Call updatePercent() from any thread.
  fun updatePercentAsync(percent: Int) {
    mPercentHandler!!.removeMessages(PERCENT_MSG)
    val m = Message.obtain(mPercentHandler, PERCENT_MSG)
    m.arg1 = percent
    m.sendToTarget()
  }

  interface PercentListener {
    fun onNoiseServicePercentChange(percent: Int, stopTimestamp: Date, stopReasonId: Int)
  }

  private class PercentHandler : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
      if (msg.what != PERCENT_MSG) {
        throw AssertionError("Unexpected message: " + msg.what)
      }
      updatePercent(msg.arg1)
    }
  }

  companion object {
    private const val PERCENT_MSG = 1
    private val sPercentListeners = ArrayList<PercentListener>()
    private const val NOTIFY_ID = 1
    private const val CHANNEL_ID = "default"

    // These must be accessed only from the main thread.
    private var sLastPercent = -1

    // Save the reason for the most recent stop/restart.  In theory, it would
    // be more correct to use persistent storage, but the values should stick
    // around in RAM long enough for practical purposes.
    private var sStopTimestamp = Date()
    private var sStopReasonId = 0

    @Suppress("deprecation")
    private fun <T : Parcelable?> getParcelableExtraCompat(intent: Intent, name: String, clazz: Class<T>): T? {
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(name, clazz)
      } else {
        intent.getParcelableExtra(name)
      }
    }

    // If connected, notify the main activity of our progress.
    // This must run in the main thread.
    private fun updatePercent(percent: Int) {
      for (listener in sPercentListeners) {
        listener.onNoiseServicePercentChange(percent, sStopTimestamp, sStopReasonId)
      }
      sLastPercent = percent
    }

    // Connect the main activity so it receives progress updates.
    // This must run in the main thread.
    fun addPercentListener(listener: PercentListener) {
      sPercentListeners.add(listener)
      listener.onNoiseServicePercentChange(sLastPercent, sStopTimestamp, sStopReasonId)
    }

    fun removePercentListener(listener: PercentListener) {
      check(sPercentListeners.remove(listener))
    }

    private fun createStopIntent(context: Context, @StringRes stopReasonId: Int) =
      Intent(context, NoiseService::class.java).putExtra("stopReasonId", stopReasonId)

    fun stopService(context: Context, @StringRes stopReasonId: Int) {
      try {
        context.startService(createStopIntent(context, stopReasonId))
      } catch (e: IllegalStateException) {
        // This can be triggered by running "adb shell input keyevent 86" when the app
        // is not running. We ignore it, because in that case there's nothing to stop.
      }
    }

    private fun saveStopReason(stopReasonId: Int) {
      sStopTimestamp = Date()
      sStopReasonId = stopReasonId
    }
  }
}
