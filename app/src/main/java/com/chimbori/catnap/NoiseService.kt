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
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.content.ContextCompat
import com.chimbori.catnap.audio.SampleGenerator
import com.chimbori.catnap.audio.SampleShuffler
import com.chimbori.catnap.audio.SampleShuffler.Companion.makeAudioAttributes
import com.chimbori.catnap.audio.SampleShuffler.VolumeListener.DuckLevel.DUCK
import com.chimbori.catnap.audio.SampleShuffler.VolumeListener.DuckLevel.NORMAL
import com.chimbori.catnap.audio.SampleShuffler.VolumeListener.DuckLevel.SILENT
import com.chimbori.catnap.audio.SpectrumData
import java.util.Date

class NoiseService : Service() {
  private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

  private val percentHandler = PercentHandler()
  private val sampleShuffler = SampleShuffler()
  private val sampleGenerator = SampleGenerator(this, sampleShuffler)

  private var lastStartId = -1

  private val wakeLock: PowerManager.WakeLock by lazy {
    (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PARTIAL_WAKE_LOCK, "catnap:NoiseService")
  }

  private val audioFocusRequest = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
    .setAudioAttributes(makeAudioAttributes())
    .setOnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        // For example, a music player or a sleep timer stealing focus.
        AUDIOFOCUS_LOSS -> stopNoiseService(R.string.stop_reason_audiofocus)
        // For example, an alarm or phone call.
        AUDIOFOCUS_LOSS_TRANSIENT -> sampleShuffler.volumeListener.setDuckLevel(SILENT)
        // For example, an email notification.
        AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> sampleShuffler.volumeListener.setDuckLevel(DUCK)
        // Resume the default volume level.
        AUDIOFOCUS_GAIN -> sampleShuffler.volumeListener.setDuckLevel(NORMAL)
      }
    }
    .build()

  override fun onCreate() {
    super.onCreate()

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
    val stopReasonId = intent?.getIntExtra(EXTRA_STOP_REASON_ID, 0) ?: 0
    if (stopReasonId != 0) {
      saveStopReason(stopReasonId)
      stopSelf(startId)
      return START_NOT_STICKY
    }

    // Notify the user that the OS restarted the process.
    if (flags and START_FLAG_REDELIVERY != 0) {
      saveStopReason(R.string.stop_reason_restarted)
    }

    if (intent != null) {
      val spectrum = getParcelableExtraCompat(intent, EXTRA_SPECTRUM_BARS, SpectrumData::class.java)

      // Synchronous updates.
      sampleShuffler.setAmpWave(
        minVol = intent.getFloatExtra(EXTRA_MINIMUM_VOLUME, -1f),
        period = intent.getFloatExtra(EXTRA_PERIOD, -1f)
      )
      sampleShuffler.volumeListener.setVolumeLevel(intent.getFloatExtra(EXTRA_VOLUME_LIMIT, -1f))

      if (intent.getBooleanExtra(EXTRA_IGNORE_AUDIO_FOCUS, false)) {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
      } else {
        audioManager.requestAudioFocus(audioFocusRequest)
      }

      // Background updates.
      sampleGenerator.updateSpectrum(spectrum)

      // If the kernel decides to kill this process, let Android restart it using the most-recent spectrum.
      // It's important that we call stopSelf() with this startId when a replacement spectrum arrives, or if we're
      // stopping the service intentionally.
      lastStartId = startId
    }

    return START_REDELIVER_INTENT
  }

  override fun onDestroy() {
    super.onDestroy()

    if (lastStartId != -1) {
      // This condition can be triggered from adb shell:
      // $ am stopservice com.chimbori.catnap/.NoiseService
      saveStopReason(R.string.stop_reason_mysterious)
    }
    sampleGenerator.stopThread()
    sampleShuffler.stopThread()
    percentHandler.removeMessages(PERCENT_MSG)
    updatePercent(-1)

    audioManager.abandonAudioFocusRequest(audioFocusRequest)

    stopForeground(STOP_FOREGROUND_REMOVE)
    wakeLock.release()
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

  @AnyThread
  fun updatePercentAsync(percent: Int) {
    percentHandler.removeMessages(PERCENT_MSG)
    Message.obtain(percentHandler, PERCENT_MSG).apply { arg1 = percent }.sendToTarget()
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
    private val percentListeners = mutableListOf<PercentListener>()

    // These must be accessed only from the main thread.
    private var lastPercent = -1

    // Save the reason for the most recent stop/restart. In theory, it would be more correct to use persistent
    // storage, but the values should stick around in RAM long enough for practical purposes.
    private var stopTimestamp = Date()

    private var sStopReasonId = 0

    @Suppress("deprecation")
    private fun <T : Parcelable?> getParcelableExtraCompat(intent: Intent, name: String, clazz: Class<T>): T? =
      if (SDK_INT >= TIRAMISU) {
        intent.getParcelableExtra(name, clazz)
      } else {
        intent.getParcelableExtra(name)
      }

    /** If connected, notify the main activity of our progress. */
    @UiThread
    private fun updatePercent(percent: Int) {
      percentListeners.forEach { it.onNoiseServicePercentChange(percent, stopTimestamp, sStopReasonId) }
      lastPercent = percent
    }

    /** Connect the main activity so it receives progress updates. */
    @UiThread
    fun addPercentListener(listener: PercentListener) {
      percentListeners.add(listener)
      listener.onNoiseServicePercentChange(lastPercent, stopTimestamp, sStopReasonId)
    }

    fun removePercentListener(listener: PercentListener) {
      check(percentListeners.remove(listener))
    }

    private fun Context.createStopIntent(@StringRes stopReasonId: Int) =
      Intent(this, NoiseService::class.java).putExtra(EXTRA_STOP_REASON_ID, stopReasonId)

    fun Context.stopNoiseService(@StringRes stopReasonId: Int) {
      try {
        startService(createStopIntent(stopReasonId))
      } catch (e: IllegalStateException) {
        // This can be triggered by running "adb shell input keyevent 86" when the app is not running.
        // We ignore it, because in that case there's nothing to stop.
      }
    }

    private fun saveStopReason(stopReasonId: Int) {
      stopTimestamp = Date()
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
