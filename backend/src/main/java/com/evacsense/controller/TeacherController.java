package com.evacsense.controller;

import com.evacsense.model.*;
import com.evacsense.repository.*;
import com.evacsense.security.RequireRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private DrillSessionRepository drillSessionRepository;

    @Autowired
    private ClassroomOccupancyRepository classroomOccupancyRepository;

    @Autowired
    private ClassroomAttendanceRepository classroomAttendanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DrillController drillController;

    @RequireRole({"Teacher"})
    @GetMapping("/roster")
    public ResponseEntity<Map<String, Object>> getRoomRoster(HttpServletRequest request) {
        // Get teacherId from the authenticated JWT token — prevents IDOR (teacher viewing another teacher's roster)
        User teacher = (User) request.getAttribute("currentUser");
        String teacherId = teacher.getId();
        Optional<DrillSession> activeDrillOpt = drillSessionRepository.findFirstByStatus("active");
        if (activeDrillOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "error", "message", "No active drill"));
        }
        DrillSession drill = activeDrillOpt.get();

        Optional<ClassroomOccupancy> teacherOcc = classroomOccupancyRepository.findByDrillIdAndUserId(drill.getId(), teacherId);
        if (teacherOcc.isEmpty() || teacherOcc.get().getRoomId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "error", "message", "Teacher location not verified"));
        }

        String roomId = teacherOcc.get().getRoomId();
        List<ClassroomOccupancy> occupants = classroomOccupancyRepository.findByDrillIdAndRoomId(drill.getId(), roomId);

        List<Map<String, Object>> roster = new ArrayList<>();
        for (ClassroomOccupancy occ : occupants) {
            Optional<User> userOpt = userRepository.findById(occ.getUserId());
            if (userOpt.isPresent() && "Student".equals(userOpt.get().getRole())) {
                User student = userOpt.get();
                Optional<ClassroomAttendance> attOpt = classroomAttendanceRepository.findByDrillIdAndUserId(drill.getId(), student.getId());
                
                String status = attOpt.isPresent() ? attOpt.get().getStatus() : "Absent";
                
                Map<String, Object> studentData = new HashMap<>();
                studentData.put("id", student.getId());
                studentData.put("name", student.getName());
                studentData.put("department", student.getDepartment());
                studentData.put("status", status);
                roster.add(studentData);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("roomId", roomId);
        response.put("students", roster);
        return ResponseEntity.ok(response);
    }

    @RequireRole({"Teacher"})
    @PostMapping("/request-clearance")
    public ResponseEntity<Map<String, Object>> requestClearance(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String studentId = body.get("studentId");
        // Get teacherId from JWT — prevents spoofing a different teacher's identity
        User teacher = (User) request.getAttribute("currentUser");
        String teacherId = teacher.getId();

        Optional<DrillSession> activeDrillOpt = drillSessionRepository.findFirstByStatus("active");
        if (activeDrillOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "error", "message", "No active drill"));
        }
        DrillSession drill = activeDrillOpt.get();

        ClassroomAttendance attendance = classroomAttendanceRepository
                .findByDrillIdAndUserId(drill.getId(), studentId)
                .orElseGet(() -> new ClassroomAttendance(drill.getId(), studentId, "Absent"));

        attendance.setStatus("Pending Marshal Clearance");
        attendance.setVerifiedBy(teacherId);
        attendance.setArrivalTime(LocalDateTime.now());
        attendance.setMethod("marshal_request");
        classroomAttendanceRepository.save(attendance);

        drillController.broadcastUpdate();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Clearance requested successfully.");
        return ResponseEntity.ok(response);
    }
}
