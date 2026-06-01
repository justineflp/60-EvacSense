package com.evacsense.auth

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.graphics.Color
import android.view.View

class TeacherDashboardActivity : AppCompatActivity() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private lateinit var authService: AuthService
    private val LOCATION_PERMISSION_REQUEST_CODE = 888

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard)

        val userName = intent.getStringExtra("USER_NAME") ?: "Teacher"
        val tvTeacherName: TextView = findViewById(R.id.tvTeacherName)
        tvTeacherName.text = "Welcome, $userName"

        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null)

        authService = ApiClient.getService(this)

        val btnLogout: Button = findViewById(R.id.btnLogout)
        btnLogout.setOnClickListener {
            sharedPref.edit().remove("auth_token").apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        val btnRefresh: Button = findViewById(R.id.btnRefreshLocation)
        btnRefresh.setOnClickListener {
            performWifiScan()
        }

        requestLocationPermissions()
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            ), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            performWifiScan()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            performWifiScan()
        } else {
            Toast.makeText(this, "Location permissions required to map your room.", Toast.LENGTH_LONG).show()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun performWifiScan() {
        val tvCurrentRoom: TextView = findViewById(R.id.tvCurrentRoom)
        tvCurrentRoom.text = "Scanning Wi-Fi..."

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        var receiverFired = false

        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (receiverFired) return
                receiverFired = true
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    try { applicationContext.unregisterReceiver(this) } catch (_: Exception) {}
                    processTeacherWifiResults(wifiManager)
                }
            }
        }

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        applicationContext.registerReceiver(wifiScanReceiver, intentFilter)
        val scanStarted = wifiManager.startScan()

        if (!scanStarted) {
            // startScan() returned false (throttled on Android 9+), use cached results immediately
            receiverFired = true
            try { applicationContext.unregisterReceiver(wifiScanReceiver) } catch (_: Exception) {}
            processTeacherWifiResults(wifiManager)
        } else {
            // Set a 3-second timeout in case the broadcast never fires
            handler.postDelayed({
                if (!receiverFired) {
                    receiverFired = true
                    try { applicationContext.unregisterReceiver(wifiScanReceiver) } catch (_: Exception) {}
                    processTeacherWifiResults(wifiManager)
                }
            }, 3000)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun processTeacherWifiResults(wifiManager: WifiManager) {
        val tvCurrentRoom: TextView = findViewById(R.id.tvCurrentRoom)

        val results = try { wifiManager.scanResults } catch (_: Exception) { emptyList() }
        val wifiScans = mutableListOf<WifiScan>()
        for (result in results) {
            wifiScans.add(WifiScan(result.BSSID, result.level))
        }
        // Fallback mock data if no real scan results (emulator or throttled device)
        if (wifiScans.isEmpty()) {
            wifiScans.add(WifiScan("00:0a:95:9d:68:16", -55))
            wifiScans.add(WifiScan("00:0a:95:9d:68:17", -78))
            wifiScans.add(WifiScan("00:0a:95:9d:68:18", -82))
        }

        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null) ?: return

        authService.scanPresence("Bearer $token", ScanPresenceRequest(wifiScans)).enqueue(object : Callback<PresenceResponse> {
            override fun onResponse(call: Call<PresenceResponse>, response: Response<PresenceResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.location != null) {
                    val roomName = body.location.name ?: "Unknown Room"
                    tvCurrentRoom.text = roomName
                    startPollingRoster()
                } else {
                    tvCurrentRoom.text = "Room Not Found or No Drill Active"
                    // Still try polling roster in case teacher occupancy was set by a previous scan
                    startPollingRoster()
                }
            }
            override fun onFailure(call: Call<PresenceResponse>, t: Throwable) {
                tvCurrentRoom.text = "Network Error: ${t.message}"
                // Still try polling roster
                startPollingRoster()
            }
        })
    }

    private fun startPollingRoster() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        
        pollRunnable = object : Runnable {
            override fun run() {
                fetchRoomRoster()
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(pollRunnable!!)
    }

    private fun fetchRoomRoster() {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null) ?: return
        val teacherId = sharedPref.getString("user_id", "") ?: return

        authService.getTeacherRoster("Bearer $token", teacherId).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body["status"] == "success") {
                        val students = body["students"] as? List<Map<String, String>> ?: emptyList()
                        updateStudentList(students)
                    }
                }
            }
            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {}
        })
    }

    private fun updateStudentList(students: List<Map<String, String>>) {
        val llStudentList: LinearLayout = findViewById(R.id.llStudentList)
        llStudentList.removeAllViews()

        for (student in students) {
            val studentId = student["id"] ?: ""
            val name = student["name"] ?: "Unknown"
            val status = student["status"] ?: "Absent"

            val itemView = LinearLayout(this)
            itemView.orientation = LinearLayout.VERTICAL
            itemView.setPadding(0, 0, 0, 32)

            val tvName = TextView(this)
            tvName.text = name
            tvName.textSize = 16f
            tvName.setTextColor(Color.WHITE)

            val displayStatus = when {
                status.startsWith("Present") -> "Verified"
                status == "Missing" || status == "Pending Marshal Clearance" -> "Verification Failed"
                else -> "Unverified"
            }
            
            val tvStatus = TextView(this)
            tvStatus.text = "Status: $displayStatus"
            tvStatus.textSize = 14f

            when (displayStatus) {
                "Verified" -> tvStatus.setTextColor(Color.parseColor("#34d399")) // Green
                "Verification Failed" -> tvStatus.setTextColor(Color.parseColor("#ef4444")) // Red
                else -> tvStatus.setTextColor(Color.parseColor("#94A3B8")) // Lighter Gray for dark mode
            }

            itemView.addView(tvName)
            itemView.addView(tvStatus)

            if (status == "Missing") {
                val btnClearance = Button(this)
                btnClearance.text = "Review for Marshal Clear"
                btnClearance.setBackgroundColor(Color.parseColor("#f59e0b")) // Amber
                btnClearance.setTextColor(Color.WHITE)
                btnClearance.setOnClickListener {
                    requestMarshalClearance(studentId)
                }
                itemView.addView(btnClearance)
            }

            llStudentList.addView(itemView)
        }
    }

    private fun requestMarshalClearance(studentId: String) {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null) ?: return
        val teacherId = sharedPref.getString("user_id", "") ?: return

        val req = mapOf("studentId" to studentId, "teacherId" to teacherId)
        authService.requestClearance("Bearer $token", req).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@TeacherDashboardActivity, "Clearance Requested!", Toast.LENGTH_SHORT).show()
                    fetchRoomRoster()
                } else {
                    Toast.makeText(this@TeacherDashboardActivity, "Failed to request clearance.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                Toast.makeText(this@TeacherDashboardActivity, "Network Error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        pollRunnable?.let { handler.removeCallbacks(it) }
    }
}
