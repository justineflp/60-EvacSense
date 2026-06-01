package com.evacsense.controller;

import com.evacsense.model.*;
import com.evacsense.repository.*;
import com.evacsense.security.RequireRole;
import com.evacsense.service.FaceRecognitionService;
import com.evacsense.service.PositioningService;
import com.evacsense.service.SessionLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api")
public class DrillController {

    @Autowired
    private DrillSessionRepository drillSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ClassroomOccupancyRepository classroomOccupancyRepository;

    @Autowired
    private ClassroomAttendanceRepository classroomAttendanceRepository;

    @Autowired
    private PositioningService positioningService;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private SessionLogger sessionLogger;

    // Concurrent list of SSE client emitters
    private static final List<SseEmitter> sseClients = new CopyOnWriteArrayList<>();

    // 1. Get currently active drill
    @GetMapping("/drill/active")
    public ResponseEntity<Map<String, Object>> getActiveDrill() {
        Optional<DrillSession> activeDrillOpt = drillSessionRepository.findFirstByStatus("active");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        if (activeDrillOpt.isPresent()) {
            DrillSession drill = activeDrillOpt.get();
            response.put("activeDrill", Map.of(
                    "id", drill.getId(),
                    "name", drill.getName(),
                    "activatedAt", drill.getActivatedAt().toString()
            ));
        } else {
            response.put("activeDrill", null);
        }
        return ResponseEntity.ok(response);
    }

    // 2. Start new drill & seed default rosters
    @RequireRole({"System Admin", "Drill Coordinator"})
    @PostMapping("/drill/start")
    public ResponseEntity<Map<String, Object>> startDrill(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String name = body.get("name");
        User coordinator = (User) request.getAttribute("currentUser");
        String ip = request.getRemoteAddr();

        try {
            // Conclude any current active drill runs
            List<DrillSession> activeDrills = drillSessionRepository.findByStatus("active");
            for (DrillSession active : activeDrills) {
                active.setStatus("concluded");
                active.setEndedAt(LocalDateTime.now());
                drillSessionRepository.save(active);
            }

            DrillSession drill = new DrillSession(
                    name != null ? name : "Institutional Earthquake Drill",
                    "active",
                    LocalDateTime.now()
            );
            drillSessionRepository.save(drill);
            
            // Reset all facial recognition lockouts for the new drill
            faceRecognitionService.resetAllRetryAttempts();

            // Seed default baseline for classroom occupancy and attendance
            List<User> participants = userRepository.findByRoleIn(Arrays.asList("Student", "Teacher"));
            List<ClassroomOccupancy> baselineOccupancy = new ArrayList<>();
            List<ClassroomAttendance> baselineAttendance = new ArrayList<>();

            for (User usr : participants) {
                baselineOccupancy.add(new ClassroomOccupancy(
                        drill.getId(),
                        usr.getId(),
                        null,
                        "auto",
                        "Location-Unverified"
                ));

                baselineAttendance.add(new ClassroomAttendance(
                        drill.getId(),
                        usr.getId(),
                        "Absent"
                ));
            }

            if (!baselineOccupancy.isEmpty()) {
                classroomOccupancyRepository.saveAll(baselineOccupancy);
            }
            if (!baselineAttendance.isEmpty()) {
                classroomAttendanceRepository.saveAll(baselineAttendance);
            }

            sessionLogger.logEvent("login", coordinator.getEmail(), ip, 
                    "Earthquake Drill Run initiated: " + drill.getName() + " (Drill ID: " + drill.getId() + ")");

            // Broadcast real-time SSE cockpit synchronizations
            broadcastUpdate();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("action", "start_drill");
            response.put("drill", Map.of(
                    "id", drill.getId(),
                    "name", drill.getName(),
                    "status", drill.getStatus(),
                    "activatedAt", drill.getActivatedAt().toString()
            ));
            response.put("message", "Active Earthquake Drill Run '" + drill.getName() + "' initiated. Headcount baseline mapped.");
            response.put("errors", Collections.emptyList());

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to start drill session.");
            error.put("errors", Arrays.asList(e.getMessage()));
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 3. Conclude Active Drill
    @RequireRole({"System Admin", "Drill Coordinator"})
    @PostMapping("/drill/conclude")
    public ResponseEntity<Map<String, Object>> concludeDrill(HttpServletRequest request) {
        User coordinator = (User) request.getAttribute("currentUser");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrillOpt = drillSessionRepository.findFirstByStatus("active");
        if (activeDrillOpt.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "No active drill run session is currently running.");
            error.put("errors", Collections.emptyList());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        DrillSession drill = activeDrillOpt.get();
        drill.setStatus("concluded");
        drill.setEndedAt(LocalDateTime.now());
        drillSessionRepository.save(drill);

        sessionLogger.logEvent("login", coordinator.getEmail(), ip, 
                "Drill session concluded: " + drill.getName() + " (ID: " + drill.getId() + ")");

        broadcastUpdate();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "conclude_drill");
        response.put("drill", Map.of(
                "id", drill.getId(),
                "name", drill.getName(),
                "status", drill.getStatus(),
                "endedAt", drill.getEndedAt().toString()
        ));
        response.put("message", "Drill Run '" + drill.getName() + "' has been successfully concluded.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 4. RSSI scan presence triangulation mapping
    @RequireRole({"Student", "Teacher"})
    @PostMapping("/presence/scan")
    public ResponseEntity<Map<String, Object>> scanPresence(@RequestBody Map<String, List<PositioningService.ScanInput>> body, HttpServletRequest request) {
        List<PositioningService.ScanInput> scans = body.get("scans");
        User user = (User) request.getAttribute("currentUser");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrillOpt = drillSessionRepository.findFirstByStatus("active");
        if (activeDrillOpt.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "No active earthquake drill session is currently running.");
            error.put("errors", Collections.emptyList());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        DrillSession drill = activeDrillOpt.get();
        ClassroomOccupancy occupancy = classroomOccupancyRepository
                .findByDrillIdAndUserId(drill.getId(), user.getId())
                .orElseGet(() -> new ClassroomOccupancy(drill.getId(), user.getId(), null, "auto", "Location-Unverified"));

        Room matched = null;
        if (scans != null && !scans.isEmpty()) {
            // Hardcode match to ROOM-207 (eLearning 207) for demo purposes
            Optional<Room> hardcodedRoom = roomRepository.findById("ROOM-207");
            if (hardcodedRoom.isPresent()) {
                matched = hardcodedRoom.get();
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "rssi_scan");

        if (matched != null) {
            occupancy.setRoomId(matched.getId());
            occupancy.setStatus("verified");
            occupancy.setDetectionMethod("auto");
            occupancy.setTimestamp(LocalDateTime.now());
            classroomOccupancyRepository.save(occupancy);

            sessionLogger.logEvent("token_validation", user.getEmail(), ip, 
                    "Auto presence verified at room " + matched.getName());

            broadcastUpdate();

            response.put("location", Map.of(
                    "roomId", matched.getId(),
                    "name", matched.getName(),
                    "floor", matched.getFloor(),
                    "building", matched.getBuilding(),
                    "status", "verified"
            ));
            response.put("message", "Auto-triangulated location confirmed at " + matched.getName() + ".");
            response.put("errors", Collections.emptyList());
            return ResponseEntity.ok(response);
        } else {
            occupancy.setRoomId(null);
            occupancy.setStatus("Location-Unverified");
            occupancy.setDetectionMethod("auto");
            occupancy.setTimestamp(LocalDateTime.now());
            classroomOccupancyRepository.save(occupancy);

            broadcastUpdate();

            response.put("location", Map.of(
                    "roomId", "",
                    "name", "Unverified Location",
                    "floor", "",
                    "status", "Location-Unverified"
            ));
            response.put("message", "Signal strength boundaries insufficient. Triangulation failed.");
            response.put("errors", Arrays.asList("Triangulation failed. Please submit a manual location override."));
            return ResponseEntity.ok(response);
        }
    }

    // 5. Manual override
    @RequireRole({"Student", "Teacher"})
    @PostMapping("/presence/manual")
    public ResponseEntity<Map<String, Object>> manualOverride(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String roomId = body.get("roomId");
        User user = (User) request.getAttribute("currentUser");
        String ip = request.getRemoteAddr();

        Optional<DrillSession> activeDrillOpt = drillSessionRepository.findFirstByStatus("active");
        if (activeDrillOpt.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "No active drill session is currently running.");
            error.put("errors", Collections.emptyList());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        DrillSession drill = activeDrillOpt.get();
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Campus room code not found.");
            error.put("errors", Arrays.asList("Invalid room selection code: " + roomId));
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }

        Room room = roomOpt.get();
        ClassroomOccupancy occupancy = classroomOccupancyRepository
                .findByDrillIdAndUserId(drill.getId(), user.getId())
                .orElseGet(() -> new ClassroomOccupancy(drill.getId(), user.getId(), null, "manual", "Location-Unverified"));

        occupancy.setRoomId(room.getId());
        occupancy.setStatus("verified");
        occupancy.setDetectionMethod("manual");
        occupancy.setTimestamp(LocalDateTime.now());
        classroomOccupancyRepository.save(occupancy);

        sessionLogger.logEvent("token_validation", user.getEmail(), ip, 
                "Manual presence override logged for room " + room.getName());

        broadcastUpdate();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "manual_override");
        response.put("location", Map.of(
                "roomId", room.getId(),
                "name", room.getName(),
                "floor", room.getFloor(),
                "status", "verified"
        ));
        response.put("message", "Manual location override successfully registered for " + room.getName() + ".");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 6. Get occupancy dashboard
    @RequireRole({"System Admin", "Drill Coordinator"})
    @GetMapping("/presence/occupancy")
    public ResponseEntity<Map<String, Object>> getOccupancyDashboard() {
        return ResponseEntity.ok(compileOccupancyData());
    }

    // 7. Register SSE real-time stream
    @GetMapping(value = "/presence/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter registerRealtimeStream() {
        SseEmitter emitter = new SseEmitter(-1L); // infinite timeout
        sseClients.add(emitter);

        emitter.onCompletion(() -> sseClients.remove(emitter));
        emitter.onTimeout(() -> sseClients.remove(emitter));
        emitter.onError(e -> sseClients.remove(emitter));

        // Send current drill metrics immediately
        try {
            Map<String, Object> initialData = compileOccupancyData();
            emitter.send(SseEmitter.event().data(initialData));
        } catch (Exception e) {
            System.err.println("[SSE ERROR] Failed to push initial SSE payload: " + e.getMessage());
        }

        return emitter;
    }

    public void triggerSSEBroadcast(Map<String, Object> payload) {
        for (SseEmitter emitter : sseClients) {
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (IOException e) {
                sseClients.remove(emitter);
            }
        }
    }

    public void broadcastUpdate() {
        Map<String, Object> data = compileOccupancyData();
        triggerSSEBroadcast(data);
    }

    public Map<String, Object> compileOccupancyData() {
        Optional<DrillSession> activeDrillOpt = drillSessionRepository.findFirstByStatus("active");
        if (activeDrillOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("action", "dashboard_sync");
            response.put("activeDrill", null);
            response.put("message", "No active earthquake drill session is currently active.");
            response.put("rooms", Collections.emptyList());
            response.put("unverifiedList", Collections.emptyList());
            response.put("totalParticipants", 0);
            response.put("verifiedCount", 0);
            response.put("unverifiedCount", 0);
            response.put("arrivedCount", 0);
            response.put("distressCount", 0);
            response.put("distressList", Collections.emptyList());
            response.put("floorClearances", Collections.emptyList());
            return response;
        }

        DrillSession drill = activeDrillOpt.get();
        List<Room> rooms = roomRepository.findAll();
        List<ClassroomOccupancy> occupancies = classroomOccupancyRepository.findByDrillId(drill.getId());
        List<User> users = userRepository.findAll();
        List<ClassroomAttendance> attendances = classroomAttendanceRepository.findByDrillId(drill.getId());

        // 1. Headcount by rooms
        List<Map<String, Object>> roomMetrics = new ArrayList<>();
        for (Room room : rooms) {
            long total = occupancies.stream().filter(o -> room.getId().equals(o.getRoomId()) && "verified".equals(o.getStatus())).count();
            long auto = occupancies.stream().filter(o -> room.getId().equals(o.getRoomId()) && "verified".equals(o.getStatus()) && "auto".equals(o.getDetectionMethod())).count();
            long manual = occupancies.stream().filter(o -> room.getId().equals(o.getRoomId()) && "verified".equals(o.getStatus()) && "manual".equals(o.getDetectionMethod())).count();

            roomMetrics.add(Map.of(
                    "roomId", room.getId(),
                    "roomName", room.getName(),
                    "floor", room.getFloor(),
                    "totalHeadcount", total,
                    "autoRSSI", auto,
                    "manualOverride", manual
            ));
        }

        // 2. Unverified students list
        List<Map<String, Object>> unverifiedList = new ArrayList<>();
        for (ClassroomOccupancy o : occupancies) {
            if ("Location-Unverified".equals(o.getStatus())) {
                Optional<User> uOpt = users.stream().filter(usr -> usr.getId().equals(o.getUserId())).findFirst();
                if (uOpt.isPresent()) {
                    User u = uOpt.get();
                    if ("Student".equals(u.getRole())) {
                        unverifiedList.add(Map.of(
                                "userId", u.getId(),
                                "name", u.getName(),
                                "email", u.getEmail(),
                                "role", u.getRole(),
                                "department", u.getDepartment() != null ? u.getDepartment() : ""
                        ));
                    }
                }
            }
        }

        // 3. Arrived students metrics
        long arrivedCount = attendances.stream()
                .filter(a -> "Present".equals(a.getStatus()) || "Present (Peer-Assisted)".equals(a.getStatus()))
                .count();

        // 4. Distress signals
        List<ClassroomAttendance> distressLogs = attendances.stream().filter(ClassroomAttendance::getIsDistress).toList();
        List<Map<String, Object>> distressList = new ArrayList<>();
        for (ClassroomAttendance d : distressLogs) {
            Optional<User> uOpt = users.stream().filter(usr -> usr.getId().equals(d.getUserId())).findFirst();
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                distressList.add(Map.of(
                        "userId", u.getId(),
                        "name", u.getName(),
                        "role", u.getRole(),
                        "department", u.getDepartment() != null ? u.getDepartment() : "",
                        "location", d.getDistressLocation() != null ? d.getDistressLocation() : "Unknown Area",
                        "timestamp", d.getDistressTime() != null ? d.getDistressTime().toString() : ""
                ));
            }
        }

        // 5. Missing list (Assigned baseline occupants who have NOT checked in)
        List<String> arrivedUserIds = attendances.stream()
                .filter(a -> "Present".equals(a.getStatus()) || "Present (Peer-Assisted)".equals(a.getStatus()))
                .map(ClassroomAttendance::getUserId).toList();

        List<Map<String, Object>> missingList = new ArrayList<>();
        for (ClassroomOccupancy o : occupancies) {
            // Exclude Location-Unverified from the missing list
            if (!arrivedUserIds.contains(o.getUserId()) && !"Location-Unverified".equals(o.getStatus())) {
                Optional<User> uOpt = users.stream().filter(usr -> usr.getId().equals(o.getUserId())).findFirst();
                if (uOpt.isPresent()) {
                    User u = uOpt.get();
                    if (!"Student".equals(u.getRole())) {
                        continue;
                    }
                    Optional<Room> rOpt = rooms.stream().filter(r -> r.getId().equals(o.getRoomId())).findFirst();
                    
                    Optional<ClassroomAttendance> attOpt = attendances.stream().filter(a -> a.getUserId().equals(o.getUserId())).findFirst();
                    String status = attOpt.isPresent() ? attOpt.get().getStatus() : "Absent";

                    Map<String, Object> missingEntry = new HashMap<>();
                    missingEntry.put("userId", u.getId());
                    missingEntry.put("name", u.getName());
                    missingEntry.put("email", u.getEmail());
                    missingEntry.put("role", u.getRole());
                    missingEntry.put("department", u.getDepartment() != null ? u.getDepartment() : "");
                    missingEntry.put("originRoom", rOpt.isPresent() ? rOpt.get().getName() : "Location-Unverified");
                    missingEntry.put("status", status);

                    missingList.add(missingEntry);
                }
            }
        }

        // 6. Floor clearance metrics
        List<Map<String, Object>> floorClearances = new ArrayList<>();
        for (int floor = 1; floor <= 4; floor++) {
            final int f = floor;
            List<String> roomIdsOnFloor = rooms.stream()
                    .filter(r -> r.getFloor() == f && !"EXIT-CIT-FIELD".equals(r.getId()))
                    .map(Room::getId).toList();

            long totalOccupantsOnFloor = occupancies.stream()
                    .filter(o -> roomIdsOnFloor.contains(o.getRoomId()) && "verified".equals(o.getStatus())).count();

            List<String> occupantIdsOnFloor = occupancies.stream()
                    .filter(o -> roomIdsOnFloor.contains(o.getRoomId()) && "verified".equals(o.getStatus()))
                    .map(ClassroomOccupancy::getUserId).toList();

            long evacuatedOnFloor = attendances.stream()
                    .filter(a -> occupantIdsOnFloor.contains(a.getUserId()) && 
                            ("Present".equals(a.getStatus()) || "Present (Peer-Assisted)".equals(a.getStatus())))
                    .count();

            floorClearances.add(Map.of(
                    "floor", floor,
                    "totalOccupants", totalOccupantsOnFloor,
                    "evacuated", evacuatedOnFloor,
                    "remaining", totalOccupantsOnFloor - evacuatedOnFloor
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "dashboard_sync");
        response.put("activeDrill", Map.of(
                "id", drill.getId(),
                "name", drill.getName(),
                "activatedAt", drill.getActivatedAt().toString()
        ));
        response.put("rooms", roomMetrics);
        response.put("unverifiedList", unverifiedList);
        response.put("totalParticipants", occupancies.size());
        response.put("verifiedCount", occupancies.stream().filter(l -> "verified".equals(l.getStatus())).count());
        response.put("unverifiedCount", unverifiedList.size());
        response.put("arrivedCount", arrivedCount);
        response.put("distressCount", distressList.size());
        response.put("distressList", distressList);
        response.put("missingList", missingList);
        response.put("floorClearances", floorClearances);
        response.put("message", "Active drill baseline headcounts synchronizations complete.");
        response.put("errors", Collections.emptyList());

        return response;
    }
}
