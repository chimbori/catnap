package com.chimbori.catnap

import android.app.Notification
import android.app.Notification.CATEGORY_SERVICE
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_LAUNCHER
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.graphics.BitmapFactory
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.content.ContextCompat
import com.chimbori.catnap.SampleShuffler.Companion.makeAudioAttributes
import com.chimbori.catnap.SampleShuffler.VolumeListener.DuckLevel.DUCK
import com.chimbori.catnap.SampleShuffler.VolumeListener.DuckLevel.NORMAL
import com.chimbori.catnap.SampleShuffler.VolumeListener.DuckLevel.SILENT
import java.util.Date

class NoiseService : Service() {
  private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

  private val wakeLock by lazy {
    (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PARTIAL_WAKE_LOCK, "catnap:NoiseService")
  }

  private var audioFocusRequest = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
    .setAudioAttributes(makeAudioAttributes())
    .setOnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        // For example, a music player or a sleep timer stealing focus.
        AUDIOFOCUS_LOSS -> stopNoiseService(R.string.stop_reason_audiofocus)
        // For example, an alarm or phone call.
        AUDIOFOCUS_LOSS_TRANSIENT -> mSampleShuffler!!.volumeListener.setDuckLevel(SILENT)
        // For example, an email notification.
        AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mSampleShuffler!!.volumeListener.setDuckLevel(DUCK)
        // Resume the default volume level.
        AUDIOFOCUS_GAIN -> mSampleShuffler!!.volumeListener.setDuckLevel(NORMAL)
      }
    }
    .build()

  private var mSampleShuffler: SampleShuffler? = null
  private var mSampleGenerator: SampleGenerator? = null
  private var lastStartId = -1
  private var mPercentHandler: Handler? = null

  override fun onCreate() {
    super.onCreate()

    // Set up a message handler in the main thread.
    mPercentHandler = PercentHandler()
    mSampleShuffler = SampleShuffler()
    mSampleGenerator = SampleGenerator(this, mSampleShuffler!!)

    // TODO: Set a timeout for the WakeLock.
    wakeLock.acquire()

    NotificationManagerCompat.from(this).createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, IMPORTANCE_DEFAULT)
        .setName(getString(R.string.channel_name))
        .setDescription(getString(R.string.channel_description))
        .build()
    )
    if (SDK_INT >= Q) {
      startForeground(NOTIFICATION_ID, createNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
      startForeground(NOTIFICATION_ID, createNotification())
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)

    // When multiple spectra arrive, only the latest should remain active.
    if (lastStartId >= 0) {
      stopSelf(lastStartId)
      lastStartId = -1
    }

    // Handle the Stop intent.
    val stopReasonId = intent!!.getIntExtra(EXTRA_STOP_REASON_ID, 0)
    if (stopReasonId != 0) {
      saveStopReason(stopReasonId)
      stopSelf(startId)
      return START_NOT_STICKY
    }

    // Notify the user that the OS restarted the process.
    if (flags and START_FLAG_REDELIVERY != 0) {
      saveStopReason(R.string.stop_reason_restarted)
    }
    val spectrum = getParcelableExtraCompat(intent, EXTRA_SPECTRUM_BARS, SpectrumData::class.java)!!

    // Synchronous updates.
    mSampleShuffler!!.setAmpWave(
      intent.getFloatExtra(EXTRA_MINIMUM_VOLUME, -1f),
      intent.getFloatExtra(EXTRA_PERIOD, -1f)
    )
    mSampleShuffler!!.volumeListener.setVolumeLevel(intent.getFloatExtra(EXTRA_VOLUME_LIMIT, -1f))

    if (intent.getBooleanExtra(EXTRA_IGNORE_AUDIO_FOCUS, false)) {
      audioManager.abandonAudioFocusRequest(audioFocusRequest)
    } else {
      audioManager.requestAudioFocus(audioFocusRequest)
    }

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
      // $ am stopservice com.chimbori.catnap/.NoiseService
      saveStopReason(R.string.stop_reason_mysterious)
    }
    mSampleGenerator!!.stopThread()
    mSampleShuffler!!.stopThread()
    mPercentHandler!!.removeMessages(PERCENT_MSG)
    updatePercent(-1)

    audioManager.abandonAudioFocusRequest(audioFocusRequest)

    stopForeground(STOP_FOREGROUND_REMOVE)
    wakeLock!!.release()
  }

  override fun onBind(intent: Intent) = null

  private fun createNotification(): Notification {
    val context = applicationContext
    return NotificationCompat.Builder(this, CHANNEL_ID).apply {
      setContentTitle(getString(R.string.channel_name))
      setSmallIcon(R.drawable.power_sleep_black)
      setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.power_sleep_black))
      setWhen(System.currentTimeMillis())
      setCategory(CATEGORY_SERVICE)
      setVisibility(VISIBILITY_PUBLIC)
      setLocalOnly(true)
      setContentIntent(
        PendingIntent.getActivity(
          context, 0, Intent(context, MainActivity::class.java)
            .setAction(ACTION_MAIN)
            .addCategory(CATEGORY_LAUNCHER),
          FLAG_IMMUTABLE
        )
      )
      addAction(
        NotificationCompat.Action(
          R.drawable.stop, getString(R.string.stop), PendingIntent.getService(
            context, 0, createStopIntent(R.string.stop_reason_notification),
            FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
          )
        )
      )
    }.build()
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
    private val sPercentListeners = ArrayList<PercentListener>()

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
      Intent(this, NoiseService::class.java).putExtra(EXTRA_STOP_REASON_ID, stopReasonId)

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
        putPhonon(phonon)
        putExtra(EXTRA_VOLUME_LIMIT, volumeLimit)
        putExtra(EXTRA_IGNORE_AUDIO_FOCUS, ignoreAudioFocus)
      })
    }

    private fun Intent.putPhonon(phonon: Phonon) {
      putExtra(EXTRA_SPECTRUM_BARS, SpectrumData(phonon.bars))
      putExtra(EXTRA_MINIMUM_VOLUME, phonon.minimumVolume / 100f)
      putExtra(EXTRA_PERIOD, phonon.periodSeconds)
    }

    private const val PERCENT_MSG = 1
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "default"

    private const val EXTRA_SPECTRUM_BARS = "spectrum"
    private const val EXTRA_MINIMUM_VOLUME = "minimum_volume"
    private const val EXTRA_PERIOD = "period"
    private const val EXTRA_VOLUME_LIMIT = "volume_limit"
    private const val EXTRA_IGNORE_AUDIO_FOCUS = "ignore_audio_focus"
    private const val EXTRA_STOP_REASON_ID = "stop_reason_id"
  }
}
