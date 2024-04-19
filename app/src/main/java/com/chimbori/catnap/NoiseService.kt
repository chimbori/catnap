package com.chimbori.catnap

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_LAUNCHER
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.Date

class NoiseService : Service() {
  private val wakeLock by lazy {
    (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PARTIAL_WAKE_LOCK, "catnap:NoiseService")
  }

  private var mSampleShuffler: SampleShuffler? = null
  private var mSampleGenerator: SampleGenerator? = null
  private var mAudioFocusHelper: AudioFocusHelper? = null
  private var lastStartId = -1
  private var mPercentHandler: Handler? = null

  override fun onCreate() {
    super.onCreate()

    // Set up a message handler in the main thread.
    mPercentHandler = PercentHandler()
    val params = AudioParams()
    mSampleShuffler = SampleShuffler(params)
    mSampleGenerator = SampleGenerator(this, params, mSampleShuffler!!)

    // TODO: Set a timeout for the WakeLock.
    wakeLock.acquire()

    NotificationManagerCompat.from(this).createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, IMPORTANCE_LOW)
        .setName(getString(R.string.channel_name))
        .setDescription(getString(R.string.channel_description))
        .build()
    )
    if (SDK_INT >= Q) {
      startForeground(NOTIFY_ID, makeNotify(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
      startForeground(NOTIFY_ID, makeNotify())
    }

    // Note: This leaks memory if I use "this" instead of "getApplicationContext()".
    mAudioFocusHelper = AudioFocusHelper(applicationContext, mSampleShuffler!!.volumeListener)
  }

  private fun stopForegroundCompat() {
    stopForeground(STOP_FOREGROUND_REMOVE)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)

    // When multiple spectra arrive, only the latest should remain active.
    if (lastStartId >= 0) {
      stopSelf(lastStartId)
      lastStartId = -1
    }

    // Handle the Stop intent.
    val stopReasonId = intent!!.getIntExtra("stopReasonId", 0)
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
    super.onDestroy()

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
    wakeLock!!.release()
  }

  override fun onBind(intent: Intent) = null

  // Create an icon for the notification bar.
  private fun makeNotify(): Notification {
    val b = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.power_sleep)
      .setWhen(0)
      .setVisibility(VISIBILITY_PUBLIC)
      .setContentIntent(
        PendingIntent.getActivity(
          this, 0,
          Intent(this, MainActivity::class.java)
            .setAction(ACTION_MAIN)
            .addCategory(CATEGORY_LAUNCHER),
          FLAG_IMMUTABLE
        )
      )

    val rv = RemoteViews(packageName, R.layout.notification_with_stop_button)
    rv.setOnClickPendingIntent(
      R.id.stop_button, PendingIntent.getService(
        this, 0, createStopIntent(R.string.stop_reason_notification),
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

  fun interface PercentListener {
    fun onNoiseServicePercentChange(percent: Int, stopTimestamp: Date, @StringRes stopReasonId: Int)
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
      return if (SDK_INT >= TIRAMISU) {
        intent.getParcelableExtra(name, clazz)
      } else {
        intent.getParcelableExtra(name)
      }
    }

    // If connected, notify the main activity of our progress.
    // This must run in the main thread.
    private fun updatePercent(percent: Int) {
      sPercentListeners.forEach { it.onNoiseServicePercentChange(percent, sStopTimestamp, sStopReasonId) }
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

    private fun Context.createStopIntent(@StringRes stopReasonId: Int) =
      Intent(this, NoiseService::class.java).putExtra("stopReasonId", stopReasonId)

    fun Context.stopNoiseService(@StringRes stopReasonId: Int) {
      try {
        startService(createStopIntent(stopReasonId))
      } catch (e: IllegalStateException) {
        // This can be triggered by running "adb shell input keyevent 86" when the app
        // is not running. We ignore it, because in that case there's nothing to stop.
      }
    }

    private fun saveStopReason(stopReasonId: Int) {
      sStopTimestamp = Date()
      sStopReasonId = stopReasonId
    }

    fun Context.startNoiseService(phonon: Phonon, volumeLimit: Float, ignoreAudioFocus: Boolean) {
      ContextCompat.startForegroundService(this, Intent(this, NoiseService::class.java).apply {
        phonon.writeIntent(this)
        putExtra("volumeLimit", volumeLimit)
        putExtra("ignoreAudioFocus", ignoreAudioFocus)
      })
    }
  }
}
