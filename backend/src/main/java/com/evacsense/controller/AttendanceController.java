package com.evacsense.controller;

import com.evacsense.model.*;
import com.evacsense.repository.*;
import com.evacsense.security.RequireRole;
import com.evacsense.service.FaceRecognitionService;
import com.evacsense.service.SessionLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AttendanceController {

    @Autowired
    private ClassroomAttendanceRepository classroomAttendanceRepository;

    @Autowired
    private DrillSessionRepository drillSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private SessionLogger sessionLogger;

    @Autowired
    private DrillController drillController;

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message, String errorDetail) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("errors", Arrays.asList(errorDetail));
        return new ResponseEntity<>(response, status);
    }

    // 1. Detect Wi-Fi Zone Arrival
    @PostMapping("/checkin/detect")
    public ResponseEntity<Map<String, Object>> detectArrival(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String studentId = body.get("studentId");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrill = drillSessionRepository.findFirstByStatus("active");
        if (activeDrill.isEmpty()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "No active earthquake drill session is currently running.", 
                    "No active drill found.");
        }

        Optional<User> studentOpt = userRepository.findById(studentId);
        if (studentOpt.isEmpty() || !"Student".equals(studentOpt.get().getRole())) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Student ID not registered in safety registry.", 
                    "No student matches ID: " + studentId);
        }

        User student = studentOpt.get();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "detect_arrival");
        response.put("student", Map.of(
                "id", student.getId(),
                "name", student.getName(),
                "department", student.getDepartment() != null ? student.getDepartment() : ""
        ));
        response.put("message", "Assembly Area Wi-Fi presence detected. Please scan face to confirm authentication.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 2. Validate Student ID
    @PostMapping("/checkin/verify")
    public ResponseEntity<Map<String, Object>> verifyStudentId(@RequestBody Map<String, String> body) {
        String studentId = body.get("studentId");
        
        Optional<User> studentOpt = userRepository.findById(studentId);
        if (studentOpt.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Verification failed. Student ID does not exist in registry.");
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }

        User student = studentOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("student", Map.of(
                "id", student.getId(),
                "name", student.getName(),
                "role", student.getRole(),
                "department", student.getDepartment() != null ? student.getDepartment() : ""
        ));
        return ResponseEntity.ok(response);
    }

    // 3. Biometric Facial Recognition Check-In
    @PostMapping("/checkin/face")
    public ResponseEntity<Map<String, Object>> submitFaceCheckIn(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String studentId = body.get("studentId");
        String photo = body.get("photo");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrill = drillSessionRepository.findFirstByStatus("active");
        if (activeDrill.isEmpty()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "No active drill session running.", "No active drill found.");
        }

        Optional<User> studentOpt = userRepository.findById(studentId);
        if (studentOpt.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Student account not found.", "Invalid studentId: " + studentId);
        }

        User student = studentOpt.get();

        ClassroomAttendance attendance = classroomAttendanceRepository
                .findByDrillIdAndUserId(activeDrill.get().getId(), studentId)
                .orElseGet(() -> new ClassroomAttendance(activeDrill.get().getId(), studentId, "Absent"));

        if (attendance.getStatus() != null && attendance.getStatus().startsWith("Present")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "You have already been verified.", "Duplicate check-in");
        }

        // Call face recognition service
        FaceRecognitionService.FaceVerifyResult result = faceRecognitionService.verifyFace(studentId, photo);

        if (result.success) {
            attendance.setStatus("Present");
            attendance.setArrivalTime(LocalDateTime.now());
            attendance.setMethod("face");
            attendance.setFaceConfidence(result.confidence);
            attendance.setVerifiedBy("auto");
            attendance.setPhotoPath("/uploads/biometrics/" + studentId + ".jpg");
            classroomAttendanceRepository.save(attendance);

            sessionLogger.logEvent("token_validation", student.getEmail(), ip, 
                    "Face biometric check-in succeeded. Confidence: " + result.confidence + "%");

            // Broadcast updates
            drillController.broadcastUpdate();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("attendance", Map.of(
                    "id", attendance.getId(),
                    "status", attendance.getStatus(),
                    "arrivalTime", attendance.getArrivalTime().toString()
            ));
            response.put("faceConfidence", result.confidence);
            response.put("message", result.message);

            return ResponseEntity.ok(response);
        } else {
            // Failure
            if (result.attemptsRemaining == 0) {
                // Flag manual marshal override
                attendance.setStatus("Missing");
                attendance.setVerifiedBy("requires-marshal");
                classroomAttendanceRepository.save(attendance);

                sessionLogger.logEvent("failed_attempt", student.getEmail(), ip, 
                        "Face recognition lockout triggered after 3 failures");

                drillController.broadcastUpdate();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("attemptsRemaining", result.attemptsRemaining);
            response.put("message", result.message);

            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
    }

    // 4. Companion Peer-Assisted Check-In
    @RequireRole
    @PostMapping("/checkin/peer")
    public ResponseEntity<Map<String, Object>> submitPeerCheckIn(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String classmateId = body.get("classmateId");
        String photo = body.get("photo");
        User companion = (User) request.getAttribute("currentUser");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrill = drillSessionRepository.findFirstByStatus("active");
        if (activeDrill.isEmpty()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "No active drill session running.", "No active drill found.");
        }

        Optional<User> classmateOpt = userRepository.findById(classmateId);
        if (classmateOpt.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Classmate student record does not exist.", "Invalid classmateId: " + classmateId);
        }

        User classmate = classmateOpt.get();

        ClassroomAttendance attendance = classroomAttendanceRepository
                .findByDrillIdAndUserId(activeDrill.get().getId(), classmateId)
                .orElseGet(() -> new ClassroomAttendance(activeDrill.get().getId(), classmateId, "Absent"));

        if (attendance.getStatus() != null && attendance.getStatus().startsWith("Present")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "This classmate has already been verified.", "Duplicate peer check-in");
        }

        FaceRecognitionService.FaceVerifyResult result = faceRecognitionService.verifyFace(classmateId, photo);

        if (result.success) {
            attendance.setStatus("Present (Peer-Assisted)");
            attendance.setArrivalTime(LocalDateTime.now());
            attendance.setMethod("peer-assisted");
            attendance.setFaceConfidence(result.confidence);
            attendance.setVerifiedBy(companion.getId());
            attendance.setPhotoPath("/uploads/peer_checks/" + classmateId + ".jpg");
            classroomAttendanceRepository.save(attendance);

            sessionLogger.logEvent("token_validation", companion.getEmail(), ip, 
                    "Checked in peer classmate " + classmate.getName() + " (ID: " + classmateId + ")");

            // Broadcast SSE
            drillController.broadcastUpdate();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("attendance", Map.of(
                    "id", attendance.getId(),
                    "status", attendance.getStatus(),
                    "arrivalTime", attendance.getArrivalTime().toString()
            ));
            response.put("faceConfidence", result.confidence);
            response.put("message", "Peer-Assisted check-in verified successfully for " + classmate.getName() + "!");

            return ResponseEntity.ok(response);
        } else {
            if (result.attemptsRemaining == 0) {
                attendance.setStatus("Missing");
                attendance.setVerifiedBy("requires-marshal");
                classroomAttendanceRepository.save(attendance);

                sessionLogger.logEvent("failed_attempt", companion.getEmail(), ip, 
                        "Peer face recognition lockout triggered after 3 failures for classmate " + classmateId);

                drillController.broadcastUpdate();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("attemptsRemaining", result.attemptsRemaining);
            response.put("message", "Biometric verification failed for classmate: " + result.message);

            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
    }

    // 5. Emergency Distress Signal Trigger
    @PostMapping("/checkin/distress")
    public ResponseEntity<Map<String, Object>> submitDistressAlert(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String studentId = body.get("studentId");
        String location = body.get("location");
        String timestampStr = body.get("timestamp");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrill = drillSessionRepository.findFirstByStatus("active");
        if (activeDrill.isEmpty()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "No active drill session is currently running.", "No active drill found.");
        }

        Optional<User> studentOpt = userRepository.findById(studentId);
        if (studentOpt.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Student ID not found in registry.", "Invalid studentId: " + studentId);
        }

        User student = studentOpt.get();

        ClassroomAttendance attendance = classroomAttendanceRepository
                .findByDrillIdAndUserId(activeDrill.get().getId(), studentId)
                .orElseGet(() -> new ClassroomAttendance(activeDrill.get().getId(), studentId, "Absent"));

        LocalDateTime time = LocalDateTime.now();
        if (timestampStr != null && !timestampStr.isEmpty()) {
            try {
                time = LocalDateTime.parse(timestampStr);
            } catch (Exception ignored) {}
        }

        attendance.setIsDistress(true);
        attendance.setDistressLocation(location != null ? location : "Unknown Corridor");
        attendance.setDistressTime(time);
        classroomAttendanceRepository.save(attendance);

        sessionLogger.logEvent("failed_attempt", student.getEmail(), ip, 
                "EMERGENCY DISTRESS ALERT TRIGGERED. Location: " + location);

        // Broadcast distress SSE
        drillController.broadcastUpdate();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Emergency distress signal recorded. Safety marshals have been dispatched.");

        return ResponseEntity.ok(response);
    }

    // 6. Manual Marshal Clear Override (Drill coordinator clears from dashboard)
    @RequireRole({"System Admin", "Drill Coordinator"})
    @PutMapping("/checkin/clear/{userId}")
    public ResponseEntity<Map<String, Object>> manualMarshalClear(@PathVariable String userId, HttpServletRequest request) {
        User coordinator = (User) request.getAttribute("currentUser");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrill = drillSessionRepository.findFirstByStatus("active");
        if (activeDrill.isEmpty()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "No active drill session running.", "No active drill found.");
        }

        Optional<User> targetUserOpt = userRepository.findById(userId);
        if (targetUserOpt.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "User account not found.", "Invalid userId: " + userId);
        }

        User targetUser = targetUserOpt.get();

        ClassroomAttendance attendance = classroomAttendanceRepository
                .findByDrillIdAndUserId(activeDrill.get().getId(), userId)
                .orElseGet(() -> new ClassroomAttendance(activeDrill.get().getId(), userId, "Absent"));

        attendance.setStatus("Present");
        attendance.setArrivalTime(LocalDateTime.now());
        attendance.setMethod("manual-marshal");
        attendance.setIsDistress(false); // clear distress on checkout
        attendance.setVerifiedBy(coordinator.getName());
        classroomAttendanceRepository.save(attendance);

        // Reset biometric lockout cache
        faceRecognitionService.resetRetryAttempts(userId);

        sessionLogger.logEvent("role_update", targetUser.getEmail(), ip, 
                "Evacuation cleared manually by safety marshal: " + coordinator.getName());

        // Broadcast update
        drillController.broadcastUpdate();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Evacuee " + targetUser.getName() + " has been cleared manually and marked PRESENT.");

        return ResponseEntity.ok(response);
    }
}
