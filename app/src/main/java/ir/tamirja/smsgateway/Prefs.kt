package ir.tamirja.smsgateway

import android.content.Context
import org.json.JSONObject

/** Settings, the activity log and the list of messages that were already handled. */
class Prefs(context: Context) {

    private val p = context.applicationContext.getSharedPreferences("gateway", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = p.getString("base", "") ?: ""
        set(v) = p.edit().putString("base", v.trim().trimEnd('/')).apply()

    var key: String
        get() = p.getString("key", "") ?: ""
        set(v) = p.edit().putString("key", v.trim()).apply()

    var intervalSec: Int
        get() = p.getInt("interval", 45)
        set(v) = p.edit().putInt("interval", v).apply()

    /** -1 = default SIM */
    var simSubId: Int
        get() = p.getInt("sim", -1)
        set(v) = p.edit().putInt("sim", v).apply()

    var startOnBoot: Boolean
        get() = p.getBoolean("boot", true)
        set(v) = p.edit().putBoolean("boot", v).apply()

    var running: Boolean
        get() = p.getBoolean("running", false)
        set(v) = p.edit().putBoolean("running", v).apply()

    // ------------------------------------------------------------ log (last 80 lines)

    @Synchronized
    fun addLog(line: String) {
        val old = (p.getString("log", "") ?: "").split('\n').filter { it.isNotBlank() }
        val all = (old + line).takeLast(80)
        p.edit().putString("log", all.joinToString("\n")).apply()
    }

    fun log(): String = p.getString("log", "") ?: ""

    // ------------------------------------------------------------ handled messages
    // id -> {"s": true/false (sent), "e": error text, "r": true when the CRM has been told}

    @Synchronized
    private fun doneObject(): JSONObject = try {
        JSONObject(p.getString("done", "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    @Synchronized
    private fun saveDone(o: JSONObject) {
        // keep only the newest 300 entries
        val ids = o.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()
        if (ids.size > 300) {
            ids.take(ids.size - 300).forEach { o.remove(it.toString()) }
        }
        p.edit().putString("done", o.toString()).apply()
    }

    @Synchronized
    fun isDone(id: Int): Boolean = doneObject().has(id.toString())

    @Synchronized
    fun setDone(id: Int, sent: Boolean, error: String) {
        val o = doneObject()
        o.put(id.toString(), JSONObject().put("s", sent).put("e", error).put("r", false))
        saveDone(o)
    }

    @Synchronized
    fun markReported(id: Int) {
        val o = doneObject()
        val e = o.optJSONObject(id.toString()) ?: return
        e.put("r", true)
        saveDone(o)
    }

    data class Unreported(val id: Int, val sent: Boolean, val error: String)

    @Synchronized
    fun unreported(): List<Unreported> {
        val o = doneObject()
        val list = ArrayList<Unreported>()
        for (k in o.keys()) {
            val id = k.toIntOrNull() ?: continue
            val e = o.optJSONObject(k) ?: continue
            if (!e.optBoolean("r", false)) {
                list.add(Unreported(id, e.optBoolean("s", false), e.optString("e", "")))
            }
        }
        return list.sortedBy { it.id }
    }
}
