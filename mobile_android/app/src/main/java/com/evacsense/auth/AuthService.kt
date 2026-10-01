package com.evacsense.auth

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Interface defining endpoints for EvacSense Authentication and Presence systems.
 */
interface AuthService {
    companion object {
        // TODO: Replace with your Render backend URL once deployed
        // e.g. "https://evacsense-backend.onrender.com/"
        const val BASE_URL = "https://evacsense-backend.onrender.com/"
    }

    @POST("api/auth/login")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    @POST("api/auth/google-sso")
    fun googleSSO(@Body request: GoogleSSORequest): Call<AuthResponse>

    @POST("api/auth/recovery")
    fun recoverAccount(@Body request: RecoveryRequest): Call<AuthResponse>

    @GET("api/auth/validate-token")
    fun validateToken(@Header("Authorization") bearerToken: String): Call<AuthResponse>

    @GET("api/drill/active")
    fun getActiveDrill(): Call<ActiveDrillResponse>

    @POST("api/auth/register/student")
    fun registerStudent(@Body request: StudentRegisterRequest): Call<AuthResponse>

    @POST("api/auth/register/staff")
    fun registerStaff(@Body request: StaffRegisterRequest): Call<AuthResponse>

    @POST("api/auth/register-photo")
    fun registerStudentPhoto(
        @Header("Authorization") bearerToken: String,
        @Body request: PhotoRegistrationRequest
    ): Call<AuthResponse>

    @GET("api/auth/photo-status")
    fun getPhotoStatus(
        @Header("Authorization") bearerToken: String
    ): Call<PhotoStatusResponse>

    // Module 2: Classroom Presence Recording endpoints
    @POST("api/presence/scan")
    fun scanPresence(
        @Header("Authorization") bearerToken: String,
        @Body request: ScanPresenceRequest
    ): Call<PresenceResponse>

    @POST("api/presence/manual")
    fun submitManualOverride(
        @Header("Authorization") bearerToken: String,
        @Body request: ManualOverrideRequest
    ): Call<PresenceResponse>

    // Module 3: Evacuation Routing endpoint
    @GET("api/nav/route")
    fun getEvacuationRoute(
        @Header("Authorization") bearerToken: String,
        @retrofit2.http.Query("origin") originRoomId: String
    ): Call<RouteResponse>

    // Module 4: Evacuation Area Attendance and Biometrics
    @POST("api/auth/microsoft-sso")
    fun microsoftSSO(@Body request: MicrosoftSSORequest): Call<AuthResponse>

    @POST("api/checkin/detect")
    fun detectArrival(
        @Header("Authorization") bearerToken: String,
        @Body request: DetectArrivalRequest
    ): Call<DetectArrivalResponse>

    @POST("api/checkin/face")
    fun submitFaceCheckIn(
        @Header("Authorization") bearerToken: String,
        @Body request: FaceCheckInRequest
    ): Call<CheckInResponse>

    @POST("api/checkin/peer")
    fun submitPeerCheckIn(
        @Header("Authorization") bearerToken: String,
        @Body request: PeerCheckInRequest
    ): Call<CheckInResponse>

    @POST("api/checkin/distress")
    fun submitDistressAlert(
        @Header("Authorization") bearerToken: String,
        @Body request: DistressRequest
    ): Call<DistressResponse>

    // Module 5: Teacher Dashboard endpoints
    @GET("api/teacher/roster")
    fun getTeacherRoster(
        @Header("Authorization") bearerToken: String,
        @retrofit2.http.Query("teacherId") teacherId: String
    ): Call<Map<String, Any>>

    @POST("api/teacher/request-clearance")
    fun requestClearance(
        @Header("Authorization") bearerToken: String,
        @Body request: Map<String, String>
    ): Call<Map<String, Any>>
}

// Request Data Clump DTOs
data class LoginRequest(
    val email: String,
    val password: String
)

data class GoogleSSORequest(
    val email: String,
    val name: String,
    val googleToken: String
)

data class MicrosoftSSORequest(
    val email: String,
    val name: String,
    val microsoftToken: String
)

data class RecoveryRequest(
    val email: String
)

data class StudentRegisterRequest(
    val name: String,
    val email: String,
    val studentId: String,
    val password: String,
    val deviceId: String
)

data class StaffRegisterRequest(
    val name: String,
    val email: String,
    val employeeId: String,
    val password: String,
    val deviceId: String,
    val role: String
)

data class PhotoRegistrationRequest(
    val photoBase64: String
)

data class PhotoStatusResponse(
    val status: String,
    val hasPhoto: Boolean,
    val photoBase64: String?
)

data class ActiveDrillResponse(
    val status: String,
    val activeDrill: ActiveDrillDetails?
)

data class ActiveDrillDetails(
    val id: Int,
    val name: String,
    val activatedAt: String
)

// Module 2 Request & Response DTOs
data class ScanPresenceRequest(
    val scans: List<WifiScan>
)

data class WifiScan(
    val macAddress: String,
    val rssi: Int
)

data class ManualOverrideRequest(
    val roomId: String
)

data class PresenceResponse(
    val status: String,
    val action: String?,
    val location: LocationDetails?,
    val message: String?,
    val errors: List<String>?
)

data class LocationDetails(
    val roomId: String?,
    val name: String?,
    val floor: Int?,
    val building: String?,
    val status: String
)

// Module 3 Pathfinding DTOs
data class RouteResponse(
    val status: String,
    val route: EvacuationRouteDetails?
)

data class EvacuationRouteDetails(
    val origin: String,
    val destination: String,
    val totalDistance: Double,
    val path: List<NodeDetails>,
    val instructions: List<InstructionStep>
)

data class NodeDetails(
    val id: String,
    val name: String,
    val floor: Int,
    val building: String
)

data class InstructionStep(
    val step: Int,
    val fromNode: String,
    val toNode: String,
    val text: String
)

// Module 4 Attendance & Distress DTOs
data class DetectArrivalRequest(
    val studentId: String
)

data class DetectArrivalResponse(
    val status: String,
    val action: String,
    val student: UserDetails,
    val message: String,
    val errors: List<String>
)

data class UserDetails(
    val id: String,
    val name: String,
    val department: String
)

data class FaceCheckInRequest(
    val studentId: String,
    val photo: String
)

data class PeerCheckInRequest(
    val classmateId: String,
    val photo: String
)

data class CheckInResponse(
    val status: String,
    val attendance: AttendanceDetails?,
    val faceConfidence: Double?,
    val attemptsRemaining: Int?,
    val message: String?
)

data class AttendanceDetails(
    val id: Int,
    val status: String,
    val arrivalTime: String
)

data class DistressRequest(
    val studentId: String,
    val location: String,
    val timestamp: String
)

data class DistressResponse(
    val status: String,
    val message: String
)
