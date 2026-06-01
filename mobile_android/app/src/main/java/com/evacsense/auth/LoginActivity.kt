package com.evacsense.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordToggle: android.widget.ImageView
    private lateinit var loginButton: Button
    private lateinit var recoveryButton: TextView
    private lateinit var errorText: TextView
    private lateinit var loadingProgress: ProgressBar
    
    private lateinit var goToRegisterButton: TextView

    private lateinit var authService: AuthService
    private var networkDiscoveryManager: NetworkDiscoveryManager? = null
    
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize UI Elements
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        passwordToggle = findViewById(R.id.passwordToggle)
        loginButton = findViewById(R.id.loginButton)
        recoveryButton = findViewById(R.id.recoveryButton)
        errorText = findViewById(R.id.errorText)
        loadingProgress = findViewById(R.id.loadingProgress)
        goToRegisterButton = findViewById(R.id.goToRegisterButton)

        passwordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                passwordToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // simple icon for hide
            } else {
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordToggle.setImageResource(android.R.drawable.ic_menu_view)
            }
            passwordInput.setSelection(passwordInput.text.length)
        }

        // Initialize dynamic API Service
        authService = ApiClient.getService(this)

        // Start auto-discovery for local Wi-Fi EvacSense Hub
        networkDiscoveryManager = NetworkDiscoveryManager(
            context = this,
            onServiceFound = { serverUrl ->
                runOnUiThread {
                    val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
                    sharedPref.edit().putString("server_url", serverUrl).apply()
                    ApiClient.reset()
                    authService = ApiClient.getService(this)
                    Toast.makeText(this, "EvacSense Wi-Fi Detected!", Toast.LENGTH_SHORT).show()
                }
            },
            onServiceLost = {
                runOnUiThread {
                    // Toast.makeText(this, "Connection to Wi-Fi Hub lost", Toast.LENGTH_SHORT).show()
                }
            }
        )
        networkDiscoveryManager?.startDiscovery()

        // Check if token exists in SharedPreferences for session auto-login
        checkExistingSession()

        // Set Click Listeners
        loginButton.setOnClickListener { handleLogin() }
        recoveryButton.setOnClickListener { handleAccountRecovery() }

        // Both goToRegisterButton and brandTitle click open the registration screen
        goToRegisterButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        val brandTitle: TextView = findViewById(R.id.brandTitle)
        brandTitle.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }


    }

    private fun checkExistingSession() {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null)
        
        if (token != null) {
            // Fast validate session token
            showLoading(true)
            authService.validateToken("Bearer $token").enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    showLoading(false)
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val user = response.body()?.user
                        if (user != null) {
                            if (user.role != "Student" && user.role != "Teacher") {
                                sharedPref.edit().remove("auth_token").apply()
                            } else {
                                navigateToDashboard(user)
                            }
                        }
                    } else {
                        // Clear invalid local token
                        sharedPref.edit().remove("auth_token").apply()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    showLoading(false)
                    // If backend is offline, do not block app but clear loading state
                }
            })
        }
    }

    private fun handleLogin() {
        errorText.visibility = View.GONE
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Validation Checks
        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill out both email and password fields.")
            return
        }

        // Domain Security Filter
        if (!email.endsWith("@cit.edu") && !email.endsWith("@student.cit.edu")) {
            showError("Only institutional accounts (@cit.edu / @student.cit.edu) are accepted.")
            return
        }

        showLoading(true)
        val request = LoginRequest(email, password)

        authService.login(request).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                showLoading(false)
                val body = response.body()
                
                if (response.isSuccessful && body?.status == "success") {
                    // Persist JWT Token
                    val token = body.session?.token
                    val user = body.user
                    if (token != null && user != null) {
                        if (user.role != "Student" && user.role != "Teacher") {
                            showError("Access Denied: ${user.role} accounts must use the EvacSense Web Dashboard.")
                            return
                        }
                        saveSession(token, user)
                        navigateToDashboard(user)
                    }
                } else {
                    val errMsg = body?.message ?: "Invalid institutional credentials."
                    showError(errMsg)
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                showLoading(false)
                showError("Cannot connect to EvacSense API.\nError: ${t.message}\nTap 'Configure API Server URL' at the bottom to verify the IP.")
            }
        })
    }



    private fun handleAccountRecovery() {
        val email = emailInput.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Enter your email in the email field first.", Toast.LENGTH_LONG).show()
            return
        }

        if (!email.endsWith("@cit.edu") && !email.endsWith("@student.cit.edu")) {
            Toast.makeText(this, "Only CIT institutional emails can be recovered.", Toast.LENGTH_LONG).show()
            return
        }

        showLoading(true)
        authService.recoverAccount(RecoveryRequest(email)).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                showLoading(false)
                if (response.isSuccessful && response.body()?.status == "success") {
                    Toast.makeText(this@LoginActivity, "Simulated recovery link sent. Check your inbox.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@LoginActivity, "Email recovery failed. Account not found.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                showLoading(false)
                Toast.makeText(this@LoginActivity, "Recovery request failed.", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun saveSession(token: String, user: User) {
        val sharedPref = getSharedPreferences("evacsense_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("auth_token", token)
            putString("user_id", user.id)
            putString("user_name", user.name)
            putString("user_email", user.email)
            putString("user_role", user.role)
            putString("user_dept", user.department)
            putBoolean("has_photo", user.hasPhoto ?: false)
            apply()
        }
    }

    private fun navigateToDashboard(user: User) {
        val intent = if (user.role == "Teacher") {
            Intent(this, TeacherDashboardActivity::class.java)
        } else {
            Intent(this, DashboardActivity::class.java)
        }.apply {
            putExtra("USER_NAME", user.name)
            putExtra("USER_ROLE", user.role)
            putExtra("USER_EMAIL", user.email)
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun showLoading(isLoading: Boolean) {
        loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
    }



    override fun onDestroy() {
        super.onDestroy()
        networkDiscoveryManager?.stopDiscovery()
    }
}
