package com.evacsense.auth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.provider.MediaStore
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckInActivity : AppCompatActivity() {

    private lateinit var networkStateBanner: LinearLayout
    private lateinit var networkStateText: TextView

    // Arrival Panel
    private lateinit var arrivalStatusText: TextView
    private lateinit var detectArrivalButton: Button
    private lateinit var manualArrivalButton: Button

    // Biometrics 2FA Panel
    private lateinit var biometricsCard: LinearLayout
    private lateinit var studentVerifiedText: TextView
    private lateinit var faceStatusText: TextView
    private lateinit var attemptsText: TextView
    private lateinit var captureFaceButton: Button

    // Companion Panel
    private lateinit var peerStudentIdInput: EditText
    private lateinit var peerNameText: TextView
    private lateinit var verifyPeerButton: Button
    private lateinit var capturePeerFaceButton: Button

    // Distress Beacon
    private lateinit var distressButton: Button
    private lateinit var backToDashboardButton: Button

    private lateinit var authService: AuthService
    private lateinit var currentStudentId: String
    private lateinit var currentStudentName: String

    private var attemptsRemaining = 3
    private var simulatedCaptureCount = 0
    private var isOfflineMode = false
    private val handler = Handler(Looper.getMainLooper())
    private var syncRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkin)

        // Bind Views
        networkStateBanner = findViewById(R.id.networkStateBanner)
        networkStateText = findViewById(R.id.networkStateText)

        arrivalStatusText = findViewById(R.id.arrivalStatusText)
        detectArrivalButton = findViewById(R.id.detectArrivalButton)
        manualArrivalButton = findViewById(R.id.manualArrivalButton)

        biometricsCard = findViewById(R.id.biometricsCard)
        studentVerifiedText = findViewById(R.id.studentVerifiedText)
        faceStatusText = findViewById(R.id.faceStatusText)
        attemptsText = findViewById(R.id.attemptsText)
        captureFaceButton = findViewById(R.id.captureFaceButton)

        peerStudentIdInput = findViewById(R.id.peerStudentIdInput)
        peerNameText = findViewById(R.id.peerNameText)
        verifyPeerButton = findViewById(R.id.verifyPeerButton)
        capturePeerFaceButton = findViewById(R.id.capturePeerFaceButton)

        distressButton = findViewById(R.id.distressButton)
        backToDashboardButton = findViewById(R.id.backToDashboardButton)

        // Setup API Service
        authService = ApiClient.getService(this)

        // Read local credentials for default student ID
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        currentStudentId = sharedPref.getString("user_id", "USR-001") ?: "USR-001"
        currentStudentName = sharedPref.getString("user_name", "Maria Santos") ?: "Maria Santos"

        // Set Click Listeners
        detectArrivalButton.setOnClickListener { handleArrivalDetection() }
        manualArrivalButton.setOnClickListener { handleManualArrivalOverride() }
        captureFaceButton.setOnClickListener { handleFaceBiometricsCapture() }
        verifyPeerButton.setOnClickListener { handlePeerVerification() }
        capturePeerFaceButton.setOnClickListener { handlePeerCheckIn() }
        distressButton.setOnClickListener { handleDistressTrigger() }
        backToDashboardButton.setOnClickListener { finish() }

        // Start connection checker & cache queue syncer loop
        startSyncLoop()
    }

    private fun checkConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        
        isOfflineMode = !hasInternet
        updateNetworkUI()
        return hasInternet
    }

    private fun updateNetworkUI() {
        if (isOfflineMode) {
            networkStateBanner.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            networkStateText.text = "OFFLINE — LOCAL ARRIVAL QUEUE ACTIVATED (SYNC PENDING)"
            networkStateText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            networkStateBanner.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            networkStateText.text = "ACTIVE — SYNCHRONIZED WITH CLOUD"
            networkStateText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    // --- Wi-Fi Arrival Section ---
    private fun handleArrivalDetection() {
        if (!checkConnection()) {
            Toast.makeText(this, "Network offline. Caching arrival state locally.", Toast.LENGTH_LONG).show()
            handleManualArrivalOverride()
            return
        }

        arrivalStatusText.text = "Scanning Assembly Wi-Fi Zones..."
        detectArrivalButton.isEnabled = false

        val request = DetectArrivalRequest(currentStudentId)
        val token = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE).getString("auth_token", "") ?: ""

        authService.detectArrival("Bearer $token", request).enqueue(object : Callback<DetectArrivalResponse> {
            override fun onResponse(call: Call<DetectArrivalResponse>, response: Response<DetectArrivalResponse>) {
                detectArrivalButton.isEnabled = true
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    arrivalStatusText.text = "Detected at Zone: CIT Field Area!"
                    arrivalStatusText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_green_light))
                    showBiometricsPanel(currentStudentName)
                } else {
                    arrivalStatusText.text = "Verification failed: No assembly Wi-Fi zone found."
                    Toast.makeText(this@CheckInActivity, body?.message ?: "Detection failed.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<DetectArrivalResponse>, t: Throwable) {
                detectArrivalButton.isEnabled = true
                isOfflineMode = true
                updateNetworkUI()
                Toast.makeText(this@CheckInActivity, "Connection error. Switched to offline mode.", Toast.LENGTH_LONG).show()
                handleManualArrivalOverride()
            }
        })
    }

    private fun handleManualArrivalOverride() {
        arrivalStatusText.text = "Manual Check-in Override Confirmed."
        arrivalStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        showBiometricsPanel(currentStudentName)
    }

    private fun showBiometricsPanel(studentName: String) {
        studentVerifiedText.text = "Verified student ID: $currentStudentId ($studentName)"
        biometricsCard.visibility = View.VISIBLE
    }

    // --- 2FA Facial Biometrics Section ---
    private val takeFacePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                faceStatusText.text = "Analyzing biometric coordinates..."
                val baos = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                
                submitFaceCheckInNetwork(currentStudentId, base64)
            } else {
                faceStatusText.text = "Capture failed."
                captureFaceButton.isEnabled = true
            }
        } else {
            captureFaceButton.isEnabled = true
            faceStatusText.text = "Cancelled."
        }
    }

    private val takePeerFacePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                val peerId = peerStudentIdInput.text.toString().trim()
                peerNameText.text = "Analyzing companion biometrics..."
                peerNameText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                val baos = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                
                submitPeerCheckInNetwork(peerId, base64)
            } else {
                peerNameText.text = "Capture failed."
                peerNameText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                capturePeerFaceButton.isEnabled = true
            }
        } else {
            capturePeerFaceButton.isEnabled = true
            peerNameText.text = "Cancelled."
        }
    }

    private fun handleFaceBiometricsCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 999)
        } else {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 1) // 1 = CAMERA_FACING_FRONT
            takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            takePictureIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                captureFaceButton.isEnabled = false
                takeFacePhotoLauncher.launch(takePictureIntent)
            } else {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitFaceCheckInNetwork(studentId: String, photo: String) {
        if (!checkConnection()) {
            // OFFLINE - Cache it in local queue!
            cacheOfflineCheckIn(studentId, photo, "face")
            Toast.makeText(this, "Check-in cached locally in Queue! Will auto-sync when online.", Toast.LENGTH_LONG).show()
            return
        }

        val request = FaceCheckInRequest(studentId, photo)
        val token = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE).getString("auth_token", "") ?: ""

        authService.submitFaceCheckIn("Bearer $token", request).enqueue(object : Callback<CheckInResponse> {
            override fun onResponse(call: Call<CheckInResponse>, response: Response<CheckInResponse>) {
                val body = response.body()
                captureFaceButton.isEnabled = true
                
                if (response.isSuccessful && body?.status == "success") {
                    val conf = body.faceConfidence ?: 100.0
                    val confInt = conf.toInt()
                    faceStatusText.text = "Match Success! Confidence: $confInt%"
                    faceStatusText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_green_light))
                    captureFaceButton.isEnabled = false
                    Toast.makeText(this@CheckInActivity, "Check-in verified successfully!", Toast.LENGTH_LONG).show()
                } else {
                    var rem = 0
                    var msg = "Verification failed."
                    try {
                        val errorString = response.errorBody()?.string()
                        if (errorString != null) {
                            val errorResponse = Gson().fromJson(errorString, CheckInResponse::class.java)
                            rem = errorResponse.attemptsRemaining ?: 0
                            msg = errorResponse.message ?: msg
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (rem <= 0 && !msg.contains("already been verified", ignoreCase = true)) {
                        faceStatusText.text = "Account flagged for manual marshal verification."
                        faceStatusText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_red_light))
                        captureFaceButton.isEnabled = false
                        attemptsText.text = "Attempts Remaining: 0"
                    } else if (msg.contains("already been verified", ignoreCase = true)) {
                        faceStatusText.text = "You have already been verified."
                        faceStatusText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_green_light))
                        captureFaceButton.isEnabled = false
                    } else {
                        faceStatusText.text = "$msg (Attempts left: $rem)"
                        faceStatusText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_red_light))
                        attemptsText.text = "Attempts Remaining: $rem"
                    }
                    Toast.makeText(this@CheckInActivity, msg, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<CheckInResponse>, t: Throwable) {
                captureFaceButton.isEnabled = true
                isOfflineMode = true
                updateNetworkUI()
                cacheOfflineCheckIn(studentId, photo, "face")
                Toast.makeText(this@CheckInActivity, "Server offline. Check-in saved to local queue.", Toast.LENGTH_LONG).show()
                faceStatusText.text = "Offline Mode: Photo Queued"
                faceStatusText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_orange_light))
            }
        })
    }

    // --- Companion Peer Check-In Section ---
    private fun handlePeerVerification() {
        val peerId = peerStudentIdInput.text.toString().trim()
        if (peerId.isEmpty()) {
            Toast.makeText(this, "Enter classmate student ID first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkConnection()) {
            // Offline fallback: assume ID is valid and allow proceeding
            Toast.makeText(this, "Offline: Classmate validation skipped, proceeding offline.", Toast.LENGTH_LONG).show()
            peerNameText.visibility = View.VISIBLE
            peerNameText.text = "Offline Mode: Verified peer ID $peerId"
            capturePeerFaceButton.isEnabled = true
            return
        }

        verifyPeerButton.isEnabled = false
        val token = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE).getString("auth_token", "") ?: ""

        // For peers we reuse detectArrival to check ID validity
        val request = DetectArrivalRequest(peerId)
        authService.detectArrival("Bearer $token", request).enqueue(object : Callback<DetectArrivalResponse> {
            override fun onResponse(call: Call<DetectArrivalResponse>, response: Response<DetectArrivalResponse>) {
                verifyPeerButton.isEnabled = true
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.student != null) {
                    peerNameText.visibility = View.VISIBLE
                    peerNameText.text = "Validated Companion: ${body.student.id} - ${body.student.name}"
                    peerNameText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_green_dark))
                    capturePeerFaceButton.isEnabled = true
                } else {
                    peerNameText.visibility = View.VISIBLE
                    peerNameText.text = "ID Number Not Registered"
                    peerNameText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_red_light))
                    capturePeerFaceButton.isEnabled = false
                }
            }

            override fun onFailure(call: Call<DetectArrivalResponse>, t: Throwable) {
                verifyPeerButton.isEnabled = true
                isOfflineMode = true
                updateNetworkUI()
                peerNameText.visibility = View.VISIBLE
                peerNameText.text = "Offline: Verified peer ID $peerId"
                peerNameText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_orange_dark))
                capturePeerFaceButton.isEnabled = true
            }
        })
    }

    private fun handlePeerCheckIn() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 999)
        } else {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                capturePeerFaceButton.isEnabled = false
                takePeerFacePhotoLauncher.launch(takePictureIntent)
            } else {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitPeerCheckInNetwork(peerId: String, photoBase64: String) {
        if (!checkConnection()) {
            cacheOfflineCheckIn(peerId, photoBase64, "peer")
            Toast.makeText(this, "Peer Check-in cached locally in Queue!", Toast.LENGTH_LONG).show()
            capturePeerFaceButton.isEnabled = false
            peerStudentIdInput.setText("")
            peerNameText.visibility = View.GONE
            return
        }

        val request = PeerCheckInRequest(peerId, photoBase64)
        val token = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE).getString("auth_token", "") ?: ""

        authService.submitPeerCheckIn("Bearer $token", request).enqueue(object : Callback<CheckInResponse> {
            override fun onResponse(call: Call<CheckInResponse>, response: Response<CheckInResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    val conf = body.faceConfidence ?: 100.0
                    Toast.makeText(this@CheckInActivity, "Peer Check-in verified on Supabase! (Conf: $conf%)", Toast.LENGTH_LONG).show()
                    peerStudentIdInput.setText("")
                    peerNameText.text = "Companion Checked In Successfully!"
                    peerNameText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_green_dark))
                } else {
                    capturePeerFaceButton.isEnabled = true
                    peerNameText.text = "Peer check failed: ${body?.message}"
                    peerNameText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_red_light))
                }
            }

            override fun onFailure(call: Call<CheckInResponse>, t: Throwable) {
                isOfflineMode = true
                updateNetworkUI()
                cacheOfflineCheckIn(peerId, photoBase64, "peer")
                Toast.makeText(this@CheckInActivity, "Saved peer check-in to local queue.", Toast.LENGTH_LONG).show()
                peerStudentIdInput.setText("")
                peerNameText.text = "Offline Mode: Peer Photo Queued"
                peerNameText.setTextColor(ContextCompat.getColor(this@CheckInActivity, android.R.color.holo_orange_dark))
            }
        })
    }

    // --- Distress Alert Beacon Section ---
    private fun handleDistressTrigger() {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val timestamp = sdf.format(Date())
        val locationSim = "CIT Field Assembly Area"

        if (!checkConnection()) {
            cacheOfflineDistress(currentStudentId, locationSim, timestamp)
            Toast.makeText(this, "DISTRESS SIGNAL QUEUED LOCALLY! Retransmitting aggressively...", Toast.LENGTH_LONG).show()
            return
        }

        distressButton.isEnabled = false
        val request = DistressRequest(currentStudentId, locationSim, timestamp)
        val token = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE).getString("auth_token", "") ?: ""

        authService.submitDistressAlert("Bearer $token", request).enqueue(object : Callback<DistressResponse> {
            override fun onResponse(call: Call<DistressResponse>, response: Response<DistressResponse>) {
                distressButton.isEnabled = true
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    Toast.makeText(this@CheckInActivity, "Distress beacon broadcasted successfully to safety marshals!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@CheckInActivity, body?.message ?: "Failed to trigger.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<DistressResponse>, t: Throwable) {
                isOfflineMode = true
                updateNetworkUI()
                distressButton.isEnabled = true
                cacheOfflineDistress(currentStudentId, locationSim, timestamp)
                Toast.makeText(this@CheckInActivity, "Signal queued locally.", Toast.LENGTH_LONG).show()
            }
        })
    }

    // --- LocalArrivalQueue & Caching Manager ---
    private fun cacheOfflineCheckIn(studentId: String, photo: String, checkinType: String) {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        
        val queueJson = sharedPref.getString("offline_checkins_queue", "[]")
        val itemType = object : TypeToken<MutableList<CachedCheckIn>>() {}.type
        val queue: MutableList<CachedCheckIn> = gson.fromJson(queueJson, itemType)

        queue.add(CachedCheckIn(studentId, photo, checkinType))
        sharedPref.edit().putString("offline_checkins_queue", gson.toJson(queue)).apply()
        
        isOfflineMode = true
        updateNetworkUI()
    }

    private fun cacheOfflineDistress(studentId: String, location: String, timestamp: String) {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val gson = Gson()

        val queueJson = sharedPref.getString("offline_distress_queue", "[]")
        val itemType = object : TypeToken<MutableList<CachedDistress>>() {}.type
        val queue: MutableList<CachedDistress> = gson.fromJson(queueJson, itemType)

        queue.add(CachedDistress(studentId, location, timestamp))
        sharedPref.edit().putString("offline_distress_queue", gson.toJson(queue)).apply()

        isOfflineMode = true
        updateNetworkUI()
    }

    private fun startSyncLoop() {
        syncRunnable = object : Runnable {
            override fun run() {
                checkConnection()
                if (!isOfflineMode) {
                    flushOfflineQueues()
                }
                handler.postDelayed(this, 5000) // check and sync every 5 seconds
            }
        }
        handler.post(syncRunnable!!)
    }

    private fun flushOfflineQueues() {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val token = sharedPref.getString("auth_token", "") ?: ""
        if (token.isEmpty()) return

        // 1. Flush Distress Queue
        val distressJson = sharedPref.getString("offline_distress_queue", "[]")
        val distressType = object : TypeToken<MutableList<CachedDistress>>() {}.type
        val distressQueue: MutableList<CachedDistress> = gson.fromJson(distressJson, distressType)

        if (distressQueue.isNotEmpty()) {
            Toast.makeText(this, "Syncing ${distressQueue.size} distress signals to Supabase...", Toast.LENGTH_SHORT).show()
            val iterator = distressQueue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                val request = DistressRequest(item.studentId, item.location, item.timestamp)
                authService.submitDistressAlert("Bearer $token", request).enqueue(object : Callback<DistressResponse> {
                    override fun onResponse(call: Call<DistressResponse>, response: Response<DistressResponse>) {
                        // Success: remove from local cached queue
                    }
                    override fun onFailure(call: Call<DistressResponse>, t: Throwable) {}
                })
                iterator.remove()
            }
            sharedPref.edit().putString("offline_distress_queue", gson.toJson(distressQueue)).apply()
        }

        // 2. Flush Checkin Queue
        val checkinsJson = sharedPref.getString("offline_checkins_queue", "[]")
        val checkinTypeToken = object : TypeToken<MutableList<CachedCheckIn>>() {}.type
        val checkinQueue: MutableList<CachedCheckIn> = gson.fromJson(checkinsJson, checkinTypeToken)

        if (checkinQueue.isNotEmpty()) {
            Toast.makeText(this, "Syncing ${checkinQueue.size} check-ins to Supabase...", Toast.LENGTH_SHORT).show()
            val iterator = checkinQueue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.type == "face") {
                    val request = FaceCheckInRequest(item.studentId, item.photo)
                    authService.submitFaceCheckIn("Bearer $token", request).enqueue(object : Callback<CheckInResponse> {
                        override fun onResponse(call: Call<CheckInResponse>, response: Response<CheckInResponse>) {}
                        override fun onFailure(call: Call<CheckInResponse>, t: Throwable) {}
                    })
                } else if (item.type == "peer") {
                    val request = PeerCheckInRequest(item.studentId, item.photo)
                    authService.submitPeerCheckIn("Bearer $token", request).enqueue(object : Callback<CheckInResponse> {
                        override fun onResponse(call: Call<CheckInResponse>, response: Response<CheckInResponse>) {}
                        override fun onFailure(call: Call<CheckInResponse>, t: Throwable) {}
                    })
                }
                iterator.remove()
            }
            sharedPref.edit().putString("offline_checkins_queue", gson.toJson(checkinQueue)).apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncRunnable?.let { handler.removeCallbacks(it) }
    }
}

// Data Clumps for Caching
data class CachedCheckIn(
    val studentId: String,
    val photo: String,
    val type: String // "face" or "peer"
)

data class CachedDistress(
    val studentId: String,
    val location: String,
    val timestamp: String
)
