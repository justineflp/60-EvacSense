package com.evacsense.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class NavigationActivity : AppCompatActivity() {

    private lateinit var statusBanner: TextView
    private lateinit var offlineWarningBanner: TextView
    private lateinit var routeTitleText: TextView
    private lateinit var routeMetricsText: TextView
    private lateinit var directionsContainer: LinearLayout
    private lateinit var distressButton: Button
    private lateinit var backButton: Button

    private lateinit var authService: AuthService
    private val handler = Handler(Looper.getMainLooper())
    private var dynamicPollRunnable: Runnable? = null
    
    // Default fallback room if no baseline localized room exists
    private var detectedOriginRoomId = "ROOM-101" 
    private var detectedOriginRoomName = "CS Lab 1 (Room 401)"
    private lateinit var currentStudentId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        // Bind Views
        statusBanner = findViewById(R.id.statusBanner)
        offlineWarningBanner = findViewById(R.id.offlineWarningBanner)
        routeTitleText = findViewById(R.id.routeTitleText)
        routeMetricsText = findViewById(R.id.routeMetricsText)
        directionsContainer = findViewById(R.id.directionsContainer)
        distressButton = findViewById(R.id.distressButton)
        backButton = findViewById(R.id.backButton)

        // Setup dynamic API Service
        authService = ApiClient.getService(this)

        backButton.setOnClickListener { finish() }
        distressButton.setOnClickListener { handleDistressAlert() }

        // Read detected origin room extra if passed from dashboard auto-localization trigger
        detectedOriginRoomId = intent.getStringExtra("DETECTED_ROOM_ID") ?: "ROOM-101"
        detectedOriginRoomName = intent.getStringExtra("DETECTED_ROOM_NAME") ?: "CS Lab 1 (Room 401)"
        
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        currentStudentId = sharedPref.getString("user_id", "USR-001") ?: "USR-001"

        // Start route loading
        loadRouteDirections()

        // Set up repeating dynamic blockage/routing polling every 4 seconds
        setupDynamicRoutingPolling()
    }

    private fun loadRouteDirections() {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null)

        if (token == null) {
            Toast.makeText(this, "Session expired. Re-authentication required.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Verify internet connectivity
        if (!isNetworkConnected()) {
            activateOfflineMode()
            return
        }

        // Attempt to flush offline distress queue
        flushDistressQueue(token)

        // Fetch routing data
        authService.getEvacuationRoute("Bearer $token", detectedOriginRoomId).enqueue(object : Callback<RouteResponse> {
            override fun onResponse(call: Call<RouteResponse>, response: Response<RouteResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.route != null) {
                    offlineWarningBanner.visibility = View.GONE
                    renderEvacuationRoute(body.route)
                    
                    // Cache the route payload for offline fallback synchronization
                    cacheRoutePayload(body.route)
                } else if (response.code() == 404) {
                    handleAllRoutesBlocked()
                } else {
                    // Fallback to offline cached route if server rejected the request
                    activateOfflineMode()
                }
            }

            override fun onFailure(call: Call<RouteResponse>, t: Throwable) {
                // Gracefully handle connectivity suspend
                activateOfflineMode()
            }
        })
    }

    private fun renderEvacuationRoute(route: EvacuationRouteDetails) {
        routeTitleText.text = "Origin: $detectedOriginRoomName → Exit: ${route.destination}"
        routeMetricsText.text = "Est. Distance: ${route.totalDistance} meters | Floor Pathing: 4 → 1"

        directionsContainer.removeAllViews()

        if (route.instructions.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Direct evacuation required. Move to the nearest exit point."
            tv.setTextColor(resources.getColor(android.R.color.white, theme))
            tv.textSize = 15f
            tv.setPadding(8, 8, 8, 8)
            directionsContainer.addView(tv)
            return
        }

        for (inst in route.instructions) {
            val stepLayout = LinearLayout(this)
            stepLayout.orientation = LinearLayout.HORIZONTAL
            stepLayout.setPadding(0, 12, 0, 12)

            val bulletText = TextView(this)
            bulletText.text = "● "
            bulletText.setTextColor(resources.getColor(android.R.color.holo_orange_light, theme))
            bulletText.textSize = 16f
            stepLayout.addView(bulletText)

            val stepText = TextView(this)
            stepText.text = inst.text
            stepText.setTextColor(resources.getColor(android.R.color.white, theme))
            stepText.textSize = 15f
            stepText.setLineSpacing(0f, 1.2f)
            stepLayout.addView(stepText)

            directionsContainer.addView(stepLayout)
        }
    }

    private fun handleDistressAlert() {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val timestamp = sdf.format(Date())
        val locationSim = detectedOriginRoomName

        if (!isNetworkConnected()) {
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
                    Toast.makeText(this@NavigationActivity, "Distress beacon broadcasted successfully to safety marshals!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@NavigationActivity, body?.message ?: "Failed to trigger.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<DistressResponse>, t: Throwable) {
                distressButton.isEnabled = true
                cacheOfflineDistress(currentStudentId, locationSim, timestamp)
                Toast.makeText(this@NavigationActivity, "Signal queued locally.", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun cacheOfflineDistress(studentId: String, location: String, timestamp: String) {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val gson = Gson()

        val queueJson = sharedPref.getString("offline_distress_queue", "[]")
        val itemType = object : TypeToken<MutableList<CachedDistress>>() {}.type
        val queue: MutableList<CachedDistress> = gson.fromJson(queueJson, itemType)

        queue.add(CachedDistress(studentId, location, timestamp))
        sharedPref.edit().putString("offline_distress_queue", gson.toJson(queue)).apply()
    }

    private fun flushDistressQueue(token: String) {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val gson = Gson()

        val distressJson = sharedPref.getString("offline_distress_queue", "[]")
        val distressType = object : TypeToken<MutableList<CachedDistress>>() {}.type
        val distressQueue: MutableList<CachedDistress> = gson.fromJson(distressJson, distressType)

        if (distressQueue.isNotEmpty()) {
            val iterator = distressQueue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                val request = DistressRequest(item.studentId, item.location, item.timestamp)
                authService.submitDistressAlert("Bearer $token", request).enqueue(object : Callback<DistressResponse> {
                    override fun onResponse(call: Call<DistressResponse>, response: Response<DistressResponse>) {
                        if (response.isSuccessful) {
                            iterator.remove()
                            sharedPref.edit().putString("offline_distress_queue", gson.toJson(distressQueue)).apply()
                        }
                    }
                    override fun onFailure(call: Call<DistressResponse>, t: Throwable) {}
                })
            }
        }
    }

    private fun handleAllRoutesBlocked() {
        offlineWarningBanner.visibility = View.VISIBLE
        offlineWarningBanner.text = "ALL ROUTES BLOCKED. AWAIT MARSHAL INSTRUCTIONS."
        offlineWarningBanner.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, theme))
        offlineWarningBanner.setTextColor(resources.getColor(android.R.color.white, theme))

        routeTitleText.text = "Origin: $detectedOriginRoomName → Exit: Blocked"
        routeMetricsText.text = "Est. Distance: N/A | Status: TRAPPED"

        directionsContainer.removeAllViews()

        val tv = TextView(this)
        tv.text = "No safe evacuation route available from your current location.\n\nPlease press the Distress Alert button below to notify safety marshals immediately."
        tv.setTextColor(resources.getColor(android.R.color.holo_red_light, theme))
        tv.textSize = 15f
        tv.setPadding(8, 8, 8, 8)
        directionsContainer.addView(tv)
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun activateOfflineMode() {
        offlineWarningBanner.visibility = View.VISIBLE
        
        // Retrieve offline cached route directions from SharedPreferences
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val cachedJson = sharedPref.getString("cached_evac_route", null)

        if (cachedJson != null) {
            try {
                val route = Gson().fromJson(cachedJson, EvacuationRouteDetails::class.java)
                renderEvacuationRoute(route)
            } catch (e: Exception) {
                renderDefaultOfflineInstructions()
            }
        } else {
            renderDefaultOfflineInstructions()
        }
    }

    private fun renderDefaultOfflineInstructions() {
        routeTitleText.text = "Emergency Guidance (Offline Mode)"
        routeMetricsText.text = "Distance: N/A | Floor: Unknown"
        
        directionsContainer.removeAllViews()

        val offlineWarningText = TextView(this)
        offlineWarningText.text = "WARNING: Offline caching is unpopulated.\n\nEvacuate immediately via the nearest fire exit or staircase.\nAvoid elevators, keep calm, and seek safety marshals."
        offlineWarningText.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
        offlineWarningText.textSize = 15f
        offlineWarningText.setTypeface(null, android.graphics.Typeface.BOLD)
        offlineWarningText.setPadding(8, 16, 8, 16)
        
        directionsContainer.addView(offlineWarningText)
    }

    private fun cacheRoutePayload(route: EvacuationRouteDetails) {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(route)
        sharedPref.edit().putString("cached_evac_route", json).apply()
    }

    private fun setupDynamicRoutingPolling() {
        dynamicPollRunnable = object : Runnable {
            override fun run() {
                loadRouteDirections()
                // Repeat every 4 seconds
                handler.postDelayed(this, 4000)
            }
        }
        handler.post(dynamicPollRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        dynamicPollRunnable?.let { handler.removeCallbacks(it) }
    }
}
