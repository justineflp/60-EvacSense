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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DashboardActivity : AppCompatActivity() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private lateinit var authService: AuthService
    private var currentDrillId: Int? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 888

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                // Update UI frame
                val photoFrame: ImageView = findViewById(R.id.studentPhotoFrame)
                val statusText: TextView = findViewById(R.id.photoStatusText)
                val photoBtn: Button = findViewById(R.id.registerPhotoButton)
                
                photoFrame.setImageBitmap(imageBitmap)
                photoFrame.visibility = View.VISIBLE
                statusText.visibility = View.GONE
                photoBtn.text = "Change Photo"

                val baos = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                
                val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
                val token = sharedPref.getString("auth_token", null)
                if (token != null) {
                    Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show()
                    authService.registerStudentPhoto("Bearer $token", PhotoRegistrationRequest(base64)).enqueue(object : Callback<AuthResponse> {
                        override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                            if (response.isSuccessful && response.body()?.status == "success") {
                                sharedPref.edit().putBoolean("has_photo", true).apply()
                                Toast.makeText(this@DashboardActivity, "Facial biometric registered successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@DashboardActivity, "Failed to register photo.", Toast.LENGTH_LONG).show()
                            }
                        }
                        override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                            Toast.makeText(this@DashboardActivity, "Connection error.", Toast.LENGTH_LONG).show()
                        }
                    })
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Read extras
        val name = intent.getStringExtra("USER_NAME") ?: "User"
        val role = intent.getStringExtra("USER_ROLE") ?: "Student"
        val email = intent.getStringExtra("USER_EMAIL") ?: ""

        // Find views
        val titleText: TextView = findViewById(R.id.titleText)
        val detailsText: TextView = findViewById(R.id.detailsText)
        val actionText: TextView = findViewById(R.id.actionText)
        val logoutButton: Button = findViewById(R.id.logoutButton)
        val checkInButton: Button = findViewById(R.id.checkInButton)
        val navigationButton: Button = findViewById(R.id.navigationButton)
        val registerPhotoButton: Button = findViewById(R.id.registerPhotoButton)
        val photoContainer: LinearLayout = findViewById(R.id.photoContainer)

        // Only show Photo Container for Students
        if (role == "Student") {
            photoContainer.visibility = View.VISIBLE
            val statusText: TextView = findViewById(R.id.photoStatusText)
            // Show loading state while fetching from backend
            statusText.text = "Checking photo status..."
            statusText.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        } else {
            photoContainer.visibility = View.GONE
        }

        // Bind data
        titleText.text = "EvacSense Mobile Portal"
        detailsText.text = "Identity: $name\nRole: $role\nEmail: $email"

        // Set role-based instructions
        actionText.text = when(role) {
            "Student" -> "MOBILE PRIVILEGES ACTIVE\nAuthorized for evacuation routing and assembly facial recognition."
            "Teacher" -> "TEACHER PRIVILEGES ACTIVE\nAuthorized for pre-drill occupancy overrides and group check-ins."
            "Drill Coordinator" -> "WEB COORDINATION PRIVILEGES REQUIRED\nPlease access the system via the Web Dashboard for active live monitors."
            "System Admin" -> "FULL ADMINISTRATION PRIVILEGES\nAuthorized for security updates and policy controls."
            else -> "Mobile authentication complete. Standard navigation access allowed."
        }

        // Initialize dynamic API Service
        authService = ApiClient.getService(this)

        // For Student role, automatically activate active drill polling detector
        if (role == "Student") {
            startDrillPolling()
            fetchPhotoStatusFromBackend()
        }



        checkInButton.setOnClickListener {
            val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
            val hasPhoto = sharedPref.getBoolean("has_photo", false)
            if (!hasPhoto) {
                Toast.makeText(this, "Please register your facial photo first before check-in.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CheckInActivity::class.java))
        }

        navigationButton.setOnClickListener {
            startAutoRSSIEnrollment()
        }
        
        registerPhotoButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 999)
            } else {
                launchCamera()
            }
        }

        logoutButton.setOnClickListener {
            // Clear credentials and return to login screen
            val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
            sharedPref.edit().clear().apply()

            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun fetchPhotoStatusFromBackend() {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null) ?: return

        val statusText: TextView = findViewById(R.id.photoStatusText)
        val photoBtn: Button = findViewById(R.id.registerPhotoButton)

        authService.getPhotoStatus("Bearer $token").enqueue(object : Callback<PhotoStatusResponse> {
            override fun onResponse(call: Call<PhotoStatusResponse>, response: Response<PhotoStatusResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    val hasPhoto = body.hasPhoto
                    sharedPref.edit().putBoolean("has_photo", hasPhoto).apply()

                    if (hasPhoto) {
                        photoBtn.text = "Change Photo"
                        statusText.text = "Baseline facial photo registered."
                        statusText.setTextColor(android.graphics.Color.parseColor("#34d399"))
                        
                        // Decode and show photo
                        if (body.photoBase64 != null) {
                            try {
                                val b64String = if (body.photoBase64.contains(",")) body.photoBase64.split(",")[1] else body.photoBase64
                                val decodedString = android.util.Base64.decode(b64String, android.util.Base64.DEFAULT)
                                val decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                val photoFrame = findViewById<android.widget.ImageView>(R.id.studentPhotoFrame)
                                photoFrame.setImageBitmap(decodedByte)
                                photoFrame.visibility = View.VISIBLE
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        photoBtn.text = "Register Student Photo"
                        statusText.text = "No baseline facial photo registered."
                        statusText.setTextColor(android.graphics.Color.parseColor("#ef4444"))
                    }
                }
            }
            override fun onFailure(call: Call<PhotoStatusResponse>, t: Throwable) {
                // Fallback to local cache
                val hasPhoto = sharedPref.getBoolean("has_photo", false)
                if (hasPhoto) {
                    photoBtn.text = "Change Photo"
                    statusText.text = "Baseline facial photo registered."
                    statusText.setTextColor(android.graphics.Color.parseColor("#34d399"))
                } else {
                    statusText.text = "No baseline facial photo registered."
                    statusText.setTextColor(android.graphics.Color.parseColor("#ef4444"))
                }
            }
        })
    }

    private fun startDrillPolling() {
        pollRunnable = object : Runnable {
            override fun run() {
                authService.getActiveDrill().enqueue(object : Callback<ActiveDrillResponse> {
                    override fun onResponse(call: Call<ActiveDrillResponse>, response: Response<ActiveDrillResponse>) {
                        val body = response.body()
                        if (response.isSuccessful && body?.status == "success") {
                            val activeDrill = body.activeDrill
                            if (activeDrill != null) {
                                // Dynamic drill trigger!
                                if (currentDrillId != activeDrill.id) {
                                    currentDrillId = activeDrill.id
                                    triggerEmergencyResponse(activeDrill.name)
                                }
                            } else {
                                currentDrillId = null
                            }
                        }
                        handler.postDelayed(pollRunnable!!, 3000)
                    }

                    override fun onFailure(call: Call<ActiveDrillResponse>, t: Throwable) {
                        handler.postDelayed(pollRunnable!!, 3000)
                    }
                })
            }
        }
        handler.post(pollRunnable!!)
    }

    private fun triggerEmergencyResponse(drillName: String) {
        AlertDialog.Builder(this)
            .setTitle("ACTIVE EMERGENCY DRILL")
            .setMessage("Active Drill: $drillName\n\nEvacSense is automatically scanning physical Wi-Fi AP signals to locate your current classroom position...")
            .setCancelable(false)
            .setPositiveButton("Locating...") { dialog, _ -> dialog.dismiss() }
            .show()

        startAutoRSSIEnrollment()
    }

    private fun startAutoRSSIEnrollment() {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null)
        if (token == null) return

        // Verify Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        performWifiScan(token)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun performWifiScan(token: String) {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }

        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                unregisterReceiver(this)
                processWifiScanResults(wifiManager, token)
            }
        }

        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        val success = wifiManager.startScan()
        if (!success) {
            unregisterReceiver(wifiScanReceiver)
            processWifiScanResults(wifiManager, token)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun processWifiScanResults(wifiManager: WifiManager, token: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            launchNavigationScreen("ROOM-101")
            return
        }

        val results = wifiManager.scanResults
        val wifiScans = mutableListOf<WifiScan>()
        for (result in results) {
            wifiScans.add(WifiScan(result.BSSID, result.level))
        }

        // Emulator/physical hardware sweep fallback coordination
        if (wifiScans.isEmpty()) {
            wifiScans.add(WifiScan("00:0a:95:9d:68:16", -55)) // Mock AP-01 -> Room 401
            wifiScans.add(WifiScan("00:0a:95:9d:68:17", -78))
            wifiScans.add(WifiScan("00:0a:95:9d:68:18", -82))
        }

        authService.scanPresence("Bearer $token", ScanPresenceRequest(wifiScans)).enqueue(object : Callback<PresenceResponse> {
            override fun onResponse(call: Call<PresenceResponse>, response: Response<PresenceResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.location != null) {
                    val roomId = body.location.roomId ?: "ROOM-101"
                    val roomName = body.location.name ?: "CS Lab 1 (Room 401)"
                    Toast.makeText(this@DashboardActivity, "Auto-localized at $roomName", Toast.LENGTH_LONG).show()
                    launchNavigationScreen(roomId, roomName)
                } else {
                    launchNavigationScreen("ROOM-101", "CS Lab 1 (Room 401)")
                }
            }

            override fun onFailure(call: Call<PresenceResponse>, t: Throwable) {
                launchNavigationScreen("ROOM-101", "CS Lab 1 (Room 401)")
            }
        })
    }

    private fun launchNavigationScreen(roomId: String, roomName: String = "CS Lab 1 (Room 401)") {
        val intent = Intent(this, NavigationActivity::class.java).apply {
            putExtra("DETECTED_ROOM_ID", roomId)
            putExtra("DETECTED_ROOM_NAME", roomName)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
            val token = sharedPref.getString("auth_token", null)
            if (token != null && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performWifiScan(token)
            } else {
                launchNavigationScreen("ROOM-101")
            }
        } else if (requestCode == 999) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCamera() {
        try {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            // Hint camera app to default to front camera
            takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
            takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            takePictureIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            takePhotoLauncher.launch(takePictureIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollRunnable?.let { handler.removeCallbacks(it) }
    }
}
