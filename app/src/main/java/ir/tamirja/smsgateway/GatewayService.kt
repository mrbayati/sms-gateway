package ir.tamirja.smsgateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs in the foreground (with a notification) and every few seconds:
 *   1. tells the CRM about messages whose result it could not report earlier,
 *   2. fetches the waiting messages,
 *   3. sends each one through the SIM and reports the result.
 */
class GatewayService : Service() {

    @Volatile
    private var alive = false
    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefs: Prefs

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(getString(R.string.notif_starting))
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        if (thread == null) {
            alive = true
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smsgateway:poll").apply {
                setReferenceCounted(false)
                acquire()
            }
            thread = Thread { loop() }.also { it.start() }
        }
        prefs.running = true
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        thread?.interrupt()
        thread = null
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }

    private fun loop() {
        prefs.addLog(stamp() + "  سرویس شروع شد")
        while (alive) {
            try {
                cycle()
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                prefs.addLog(stamp() + "  خطا: " + msg)
                updateNotification("خطا: $msg")
            }

            try {
                Thread.sleep(maxOf(15, prefs.intervalSec) * 1000L)
            } catch (e: InterruptedException) {
                break
            }
        }
        prefs.addLog(stamp() + "  سرویس متوقف شد")
    }

    private fun cycle() {
        val base = prefs.baseUrl
        val key = prefs.key
        if (base.isBlank() || key.isBlank()) {
            updateNotification("آدرس سایت یا کلید تنظیم نشده است")
            return
        }

        flushUnreported(base, key)

        val messages = GatewayApi.pull(base, key)
        for (m in messages) {
            if (!alive) return

            if (!prefs.isDone(m.id)) {
                val r = SmsSender.send(this, m.to, m.text, prefs.simSubId)
                prefs.setDone(m.id, r.ok, r.error)
                prefs.addLog(
                    stamp() + (if (r.ok) "  ✓ ارسال شد به " + m.to else "  ✗ ناموفق به " + m.to + ": " + r.error)
                )
            }
            flushUnreported(base, key)
        }

        updateNotification(
            "آخرین بررسی: " + stamp() + (if (messages.isNotEmpty()) " — ${messages.size} پیام" else "")
        )
    }

    /** Reports to the CRM every handled message it has not been told about yet (e.g. when the internet was down). */
    private fun flushUnreported(base: String, key: String) {
        for (u in prefs.unreported()) {
            try {
                GatewayApi.report(base, key, u.id, u.sent, u.error)
                prefs.markReported(u.id)
            } catch (e: java.io.IOException) {
                prefs.addLog(stamp() + "  گزارش پیام ${u.id} به سرور ارسال نشد: " + (e.message ?: ""))
                return
            }
        }
    }

    private fun stamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (e: SecurityException) {
            // notifications not allowed - the service keeps running
        }
    }

    companion object {
        private const val CHANNEL_ID = "gateway"
        private const val NOTIF_ID = 1
    }
}
