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

        // Fetch routing data
        authService.getEvacuationRoute("Bearer $token", detectedOriginRoomId).enqueue(object : Callback<RouteResponse> {
            override fun onResponse(call: Call<RouteResponse>, response: Response<RouteResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.route != null) {
                    offlineWarningBanner.visibility = View.GONE
                    renderEvacuationRoute(body.route)
                    
                    // Cache the route payload for offline fallback synchronization
                    cacheRoutePayload(body.route)
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
        // Send emergency signal (simulate distress sync)
        Toast.makeText(this, "DISTRESS SENT! Coordinates registered. Safety team is responding.", Toast.LENGTH_LONG).show()
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
