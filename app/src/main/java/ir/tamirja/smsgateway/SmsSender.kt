package ir.tamirja.smsgateway

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object SmsSender {

    data class Result(val ok: Boolean, val error: String)

    private val counter = AtomicInteger(0)

    @Suppress("DEPRECATION")
    private fun manager(ctx: Context, subId: Int): SmsManager {
        if (subId < 0) {
            return if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        }
        return if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    private fun errorText(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "خطای عمومی (اعتبار یا سیم‌کارت را بررسی کنید)"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "آنتن‌دهی وجود ندارد"
        SmsManager.RESULT_ERROR_NULL_PDU -> "پیام نامعتبر"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "حالت پرواز روشن است"
        else -> "کد خطا $code"
    }

    /** Sends the text (split into parts when it is long) and waits until the phone reports every part. */
    fun send(ctx: Context, to: String, text: String, subId: Int): Result {
        val sm = try {
            manager(ctx, subId)
        } catch (e: Exception) {
            return Result(false, "سیم‌کارت انتخاب‌شده در دسترس نیست: ${e.message}")
        }

        val parts = sm.divideMessage(text)
        val action = "ir.tamirja.smsgateway.SENT." + System.nanoTime()
        val latch = CountDownLatch(parts.size)
        val errors = ArrayList<String>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (resultCode != Activity.RESULT_OK) {
                    synchronized(errors) { errors.add(errorText(resultCode)) }
                }
                latch.countDown()
            }
        }

        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)

        try {
            val intents = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                val pi = PendingIntent.getBroadcast(
                    ctx,
                    counter.incrementAndGet(),
                    Intent(action).setPackage(ctx.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                )
                intents.add(pi)
            }

            if (parts.size == 1) {
                sm.sendTextMessage(to, null, text, intents[0], null)
            } else {
                sm.sendMultipartTextMessage(to, null, parts, intents, null)
            }

            val finished = latch.await(60, TimeUnit.SECONDS)
            return when {
                !finished -> Result(false, "پاسخ ارسال از سیستم اندروید دریافت نشد")
                errors.isNotEmpty() -> Result(false, errors.first())
                else -> Result(true, "")
            }
        } catch (e: SecurityException) {
            return Result(false, "اجازه ارسال پیامک داده نشده است")
        } catch (e: Exception) {
            return Result(false, e.message ?: "خطای نامشخص")
        } finally {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // already unregistered
            }
        }
    }
}
