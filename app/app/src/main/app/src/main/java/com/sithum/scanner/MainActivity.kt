package com.sithum.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private var titleTapCount = 0
    private var isScanning = false
    private val discoveredDevices = LinkedHashMap<String, String>()

    // Admin Credentials (පසුව Firebase වලට මාරු කරන්න පුළුවන්)
    private val ADMIN_PASS = "admin123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark Theme UI Layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            setPadding(36, 48, 36, 36)
        }

        val titleView = TextView(this).apply {
            text = "SIGNAL SCANNER v1.0"
            textSize = 22f
            setTextColor(Color.parseColor("#58A6FF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // Hidden Admin Login Trigger (Title එක 5 පාරක් tap කළාම)
        titleView.setOnClickListener {
            titleTapCount++
            if (titleTapCount >= 5) {
                titleTapCount = 0
                showAdminLoginDialog()
            }
        }

        statusTextView = TextView(this).apply {
            text = "Status: Ready to scan"
            textSize = 14f
            setTextColor(Color.parseColor("#8B949E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val scanButton = Button(this).apply {
            text = "START SCAN"
            setBackgroundColor(Color.parseColor("#238636"))
            setTextColor(Color.WHITE)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = 24 }
        }

        logTextView = TextView(this).apply {
            text = "Press START SCAN to detect nearby Bluetooth & Wi-Fi signals...\n\n(Tip: Tap title 5 times for Admin Login)"
            textSize = 13f
            setTextColor(Color.parseColor("#3FB950"))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#161B22"))
        }

        scrollView.addView(logTextView)
        rootLayout.addView(titleView)
        rootLayout.addView(statusTextView)
        rootLayout.addView(scanButton)
        rootLayout.addView(scrollView)
        setContentView(rootLayout)

        scanButton.setOnClickListener {
            if (!checkPermissions()) {
                requestAppPermissions()
            } else {
                if (!isScanning) {
                    startScanning()
                    scanButton.text = "SCANNING... (TAP TO REFRESH)"
                } else {
                    scanWifi()
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return perms.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAppPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 101)
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        isScanning = true
        discoveredDevices.clear()
        statusTextView.text = "Status: Scanning BLE & Wi-Fi..."

        // 1. Bluetooth LE Scan
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = btManager.adapter?.bluetoothLeScanner
        scanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: "Unknown BLE"
                val mac = result.device.address
                val rssi = result.rssi
                discoveredDevices["BLE_$mac"] = "[BLE] $name\nMAC: $mac | Signal: $rssi dBm"
                updateUI()
            }
        })

        // 2. Wi-Fi Scan
        scanWifi()
    }

    @SuppressLint("MissingPermission")
    private fun scanWifi() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.startScan()
        val results = wifiManager.scanResults
        for (ap in results) {
            val ssid = if (ap.SSID.isNullOrEmpty()) "Hidden Network" else ap.SSID
            discoveredDevices["WIFI_${ap.BSSID}"] = "[Wi-Fi] $ssid\nBSSID: ${ap.BSSID} | Signal: ${ap.level} dBm"
        }
        updateUI()
    }

    private fun updateUI() {
        runOnUiThread {
            statusTextView.text = "Devices Found: ${discoveredDevices.size}"
            logTextView.text = discoveredDevices.values.joinToString("\n-------------------------\n")
        }
    }

    private fun showAdminLoginDialog() {
        val input = EditText(this).apply {
            hint = "Enter Admin Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Authentication")
            .setMessage("Enter password to access Admin Controls")
            .setView(input)
            .setPositiveButton("Login") { _, _ ->
                if (input.text.toString() == ADMIN_PASS) {
                    showAdminDashboard()
                } else {
                    Toast.makeText(this, "Invalid Admin Password!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminDashboard() {
        val info = """
            • App Version: 1.0 (Build 1)
            • Active Scanners: BLE + Wi-Fi
            • Detected Cache: ${discoveredDevices.size} devices
            
            (Next step: Connect Firebase for Remote Updates & Cloud Config)
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Admin Control Panel")
            .setMessage(info)
            .setPositiveButton("Clear Scan Logs") { _, _ ->
                discoveredDevices.clear()
                updateUI()
                Toast.makeText(this, "Logs Cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
