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

    private fun note(alert: String?): Notification {
        val ack = PendingIntent.getService(this, 0, Intent(this, AlertService::class.java).setAction("ACK"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "tm").setSmallIcon(R.drawable.ic_medical_shield)
            .setContentTitle(if (alert == null) "TASHIL MEDICAL • En Service" else "URGENCE")
            .setContentText(alert ?: "Système d'urgence actif et prêt à recevoir les alertes")
            .setContentIntent(open).setOngoing(alert == null)
            .setPriority(if (alert == null) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .apply { if (alert != null) addAction(0, "Acquitter", ack).setCategory(NotificationCompat.CATEGORY_ALARM) }.build()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("tm", "Urgences", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        if (i?.action == "ACK") {
            Alarm.stop(this); scope.launch { Db.get(this@AlertService).dao().ackAll() }
            nm.notify(1, note(null)); return START_STICKY
        }
        ServiceCompat.startForeground(this, 1, note(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (job?.isActive != true) job = scope.launch {
            var seen = System.currentTimeMillis(); var beat = 0L
            val dao = Db.get(this@AlertService).dao()
            while (isActive) {
                val p = dao.profile()
                if (p == null || !p.onDuty) { stopSelf(); break }
                if (p.dutyEnd > 0 && System.currentTimeMillis() >= p.dutyEnd) {  // auto-off timer
                    val n = p.copy(onDuty = false, dutyEnd = 0); dao.save(n)
                    runCatching { Bridge.setStatus(n) }; Alarm.stop(this@AlertService); stopSelf(); break
                }
                runCatching {
                    if (System.currentTimeMillis() - beat > 120000) { Bridge.setStatus(p); beat = System.currentTimeMillis() }
                    Bridge.newAlerts(p, seen).forEach { a ->
                        seen = maxOf(seen, a.ts)
                        if (a.uid != p.uid && a.target == p.role &&
                            dao.add(AlertRow(a.id, a.from, a.target, a.ts, a.sos)) != -1L) {
                            Alarm.start(this@AlertService)
                            nm.notify(1, note((if (a.sos) "🚨 SOS — " else "") + a.from))
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
