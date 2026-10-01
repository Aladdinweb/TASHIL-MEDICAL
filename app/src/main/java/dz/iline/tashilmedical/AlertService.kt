package dz.iline.tashilmedical
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

/** Continuous alarm on the ALARM stream (audible in silent mode; DND may still mute it) + vibration. */
object Alarm {
    private var mp: MediaPlayer? = null
    fun start(c: Context) {
        if (mp != null) return
        val am = c.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            isLooping = true; prepare(); start()
        }
        c.getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0))
    }
    fun stop(c: Context) { mp?.release(); mp = null; c.getSystemService(Vibrator::class.java).cancel() }
}

class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    override fun onBind(i: Intent?): IBinder? = null

    private fun note(text: String, urgent: Boolean): Notification {
        val ack = PendingIntent.getService(this, 0, Intent(this, AlertService::class.java).setAction("ACK"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "tm").setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("TASHIL MEDICAL").setContentText(text).setContentIntent(open)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .apply { if (urgent) addAction(0, "Acquitter", ack).setCategory(NotificationCompat.CATEGORY_ALARM) }.build()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("tm", "Urgences", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        if (i?.action == "ACK") {
            Alarm.stop(this); scope.launch { Db.get(this@AlertService).dao().ackAll() }
            nm.notify(1, note("En service", false)); return START_STICKY
        }
        ServiceCompat.startForeground(this, 1, note("En service", false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (job?.isActive != true) job = scope.launch {
            var seen = System.currentTimeMillis(); var beat = 0L
            val dao = Db.get(this@AlertService).dao()
            while (isActive) {
                val p = dao.profile()
                if (p == null || !p.onDuty) { stopSelf(); break }
                runCatching {
                    if (System.currentTimeMillis() - beat > 120000) { Bridge.setStatus(p); beat = System.currentTimeMillis() }
                    Bridge.newAlerts(p, seen).forEach { a ->
                        seen = maxOf(seen, a.ts)
                        if (a.uid != p.uid && (a.target == p.role || a.target == "Tous") &&
                            dao.add(AlertRow(a.id, a.from, a.target, a.ts)) != -1L) {
                            Alarm.start(this@AlertService)
                            nm.notify(1, note("URGENCE — ${a.from}", true))
                        }
                    }
                }
                delay(15000)
            }
        }
        return START_STICKY
    }
    override fun onDestroy() { scope.cancel(); Alarm.stop(this) }
}
