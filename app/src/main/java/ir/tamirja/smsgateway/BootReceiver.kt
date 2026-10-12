package ir.tamirja.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts the service again after the phone restarts (if it was running and "start on boot" is on). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = Prefs(context)
        if (prefs.running && prefs.startOnBoot) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
            } catch (e: Exception) {
                // Some Android versions do not allow this at boot - open the app once to start it.
                prefs.addLog("شروع خودکار پس از روشن شدن ممکن نبود؛ برنامه را یک بار باز کنید")
            }
        }
    }
}
