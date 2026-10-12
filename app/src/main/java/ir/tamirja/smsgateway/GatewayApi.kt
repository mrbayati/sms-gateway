package ir.tamirja.smsgateway

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The three CRM endpoints (plain GET requests, protected by the secret key). */
object GatewayApi {

    data class Msg(val id: Int, val to: String, val text: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.requestMethod = "GET"
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code == 401) throw IOException("کلید اشتباه است یا ارسال از گوشی در CRM غیرفعال است")
            if (code !in 200..299) throw IOException("خطای سرور ($code)")
            return body
        } finally {
            c.disconnect()
        }
    }

    private fun json(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: Exception) {
        throw IOException("پاسخ سرور نامعتبر است (آدرس سایت را بررسی کنید)")
    }

    fun ping(base: String, key: String) {
        val j = json(get("$base/sms_gateway/ping?key=${enc(key)}"))
        if (!j.optBoolean("ok")) throw IOException(j.optString("error", "error"))
    }

    fun pull(base: String, key: String): List<Msg> {
        val j = json(get("$base/sms_gateway/pull?key=${enc(key)}&limit=5"))
        if (!j.optBoolean("ok")) throw IOException(j.optString("error", "error"))

        val arr = j.optJSONArray("messages") ?: return emptyList()
        val list = ArrayList<Msg>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            list.add(Msg(m.getInt("id"), m.getString("to"), m.getString("text")))
        }
        return list
    }

    fun report(base: String, key: String, id: Int, sent: Boolean, error: String) {
        var url = "$base/sms_gateway/report?key=${enc(key)}&id=$id&status=${if (sent) "sent" else "failed"}"
        if (!sent && error.isNotBlank()) url += "&error=${enc(error.take(200))}"
        val j = json(get(url))
        if (!j.optBoolean("ok")) throw IOException(j.optString("error", "error"))
    }
}
