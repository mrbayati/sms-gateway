package ir.tamirja.smsgateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var site: EditText
    private lateinit var key: EditText
    private lateinit var interval: EditText
    private lateinit var sim: Spinner
    private lateinit var boot: CheckBox
    private lateinit var toggle: Button
    private lateinit var logView: TextView

    private val simIds = ArrayList<Int>()

    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        status = findViewById(R.id.status)
        site = findViewById(R.id.site)
        key = findViewById(R.id.key)
        interval = findViewById(R.id.interval)
        sim = findViewById(R.id.sim)
        boot = findViewById(R.id.boot)
        toggle = findViewById(R.id.toggle)
        logView = findViewById(R.id.log)

        site.setText(prefs.baseUrl)
        key.setText(prefs.key)
        interval.setText(prefs.intervalSec.toString())
        boot.isChecked = prefs.startOnBoot

        toggle.setOnClickListener { if (prefs.running) stopGateway() else startGateway() }
        findViewById<Button>(R.id.save).setOnClickListener { save(); toast(getString(R.string.saved)) }
        findViewById<Button>(R.id.test).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.permissions).setOnClickListener { askPermissions() }
        findViewById<Button>(R.id.battery).setOnClickListener { askBatteryExemption() }

        askPermissions()
        loadSims()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadSims()
    }

    // ------------------------------------------------------------------ actions

    private fun save() {
        prefs.baseUrl = site.text.toString()
        prefs.key = key.text.toString()
        prefs.intervalSec = (interval.text.toString().toIntOrNull() ?: 45).coerceIn(15, 600)
        prefs.startOnBoot = boot.isChecked
        val pos = sim.selectedItemPosition
        // spinner position 0 = default SIM, position n = simIds[n - 1]
        prefs.simSubId = if (pos >= 1 && (pos - 1) in simIds.indices) simIds[pos - 1] else -1
    }

    private fun startGateway() {
        save()
        if (prefs.baseUrl.isBlank() || prefs.key.isBlank()) {
            toast(getString(R.string.need_settings))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.need_sms_permission))
            askPermissions()
            return
        }
        prefs.running = true
        ContextCompat.startForegroundService(this, Intent(this, GatewayService::class.java))
        updateStatus()
    }

    private fun stopGateway() {
        prefs.running = false
        stopService(Intent(this, GatewayService::class.java))
        updateStatus()
    }

    private fun testConnection() {
        save()
        val base = prefs.baseUrl
        val k = prefs.key
        if (base.isBlank() || k.isBlank()) {
            toast(getString(R.string.need_settings))
            return
        }
        toast(getString(R.string.testing))
        Thread {
            val msg = try {
                GatewayApi.ping(base, k)
                getString(R.string.test_ok)
            } catch (e: Exception) {
                getString(R.string.test_failed) + ": " + (e.message ?: "")
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun askPermissions() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.SEND_SMS)
        list.add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)

        val missing = list.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        } else {
            toast(getString(R.string.permissions_ok))
        }
    }

    private fun askBatteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast(getString(R.string.battery_ok))
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ------------------------------------------------------------------ SIM list

    private fun loadSims() {
        simIds.clear()
        val names = ArrayList<String>()
        names.add(getString(R.string.sim_default))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subs = getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
                subs?.sortedBy { it.simSlotIndex }?.forEach {
                    simIds.add(it.subscriptionId)
                    names.add("سیم ${it.simSlotIndex + 1} - ${it.carrierName}")
                }
            } catch (e: SecurityException) {
                // permission not granted
            }
        }

        // index 0 of the spinner is the default SIM, the others map to simIds[position - 1]
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sim.adapter = adapter

        val idx = simIds.indexOf(prefs.simSubId)
        sim.setSelection(if (idx >= 0) idx + 1 else 0)
    }

    // ------------------------------------------------------------------ helpers

    private fun updateStatus() {
        val running = prefs.running
        status.text = getString(if (running) R.string.status_running else R.string.status_stopped)
        toggle.text = getString(if (running) R.string.stop else R.string.start)
        logView.text = prefs.log().split('\n').reversed().joinToString("\n")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
