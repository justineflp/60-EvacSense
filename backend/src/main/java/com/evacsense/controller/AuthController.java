package com.evacsense.controller;

import com.evacsense.model.User;
import com.evacsense.repository.UserRepository;
import com.evacsense.security.RequireRole;
import com.evacsense.service.JwtService;
import com.evacsense.service.SessionLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SessionLogger sessionLogger;

    private String getRoleRedirect(String role) {
        switch (role) {
            case "Student": return "/student-dashboard";
            case "Teacher": return "/teacher-dashboard";
            case "Drill Coordinator": return "/coordinator-dashboard";
            case "System Admin": return "/admin-dashboard";
            default: return "/";
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip : "127.0.0.1";
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message, String errorDetail) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("errors", Arrays.asList(errorDetail));
        return new ResponseEntity<>(response, status);
    }

    // 1. Password-Based Login
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        String password = body.get("password");
        String ip = getClientIp(request);

        if (email == null || password == null) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Missing email or password credentials.", 
                    "Both email and password are required.");
        }

        // Domain verification
        if (!email.endsWith("@cit.edu") && !email.endsWith("@student.cit.edu")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Access Restricted.", 
                    "Only institutional emails (@cit.edu or @student.cit.edu) are accepted.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            sessionLogger.logEvent("failed_attempt", email, ip, "Login failed: Account not found");
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password credentials.", 
                    "User account does not exist.");
        }

        User user = userOpt.get();

        // Check locked status
        if ("locked".equals(user.getStatus())) {
            sessionLogger.logEvent("failed_attempt", email, ip, "Login blocked: Account locked");
            Map<String, Object> response = new HashMap<>();
            response.put("status", "locked");
            response.put("action", "login");
            response.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole(), "department", user.getDepartment() != null ? user.getDepartment() : ""));
            response.put("session", null);
            response.put("redirect", null);
            response.put("message", "Your account has been locked due to 3 failed login attempts. Contact an Admin.");
            response.put("errors", Arrays.asList("Account status is locked."));
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        // Check Pending Approval
        if ("Pending Approval".equals(user.getStatus())) {
            sessionLogger.logEvent("failed_attempt", email, ip, "Login blocked: Account pending approval");
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("action", "login");
            response.put("message", "Account pending admin approval");
            response.put("errors", Arrays.asList("This account requires administrator review before activation."));
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        // Check Rejected
        if ("Rejected".equals(user.getStatus())) {
            sessionLogger.logEvent("failed_attempt", email, ip, "Login blocked: Account rejected");
            return buildErrorResponse(HttpStatus.FORBIDDEN, "Your registration request has been rejected by an administrator.", 
                    "Registration request rejected.");
        }

        // Verify password
        if (user.getPasswordHash() == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            String status = "active";
            String message = "Invalid email or password credentials.";

            if (attempts >= 3) {
                status = "locked";
                message = "Your account has been locked due to 3 failed login attempts. Please contact IT support.";
                user.setStatus("locked");
                user.setFailedAttempts(attempts);
                userRepository.save(user);
                sessionLogger.logEvent("failed_attempt", email, ip, "Account locked due to " + attempts + " failed attempts");
            } else {
                user.setFailedAttempts(attempts);
                userRepository.save(user);
                sessionLogger.logEvent("failed_attempt", email, ip, "Login failed: Incorrect password (attempt " + attempts + "/3)");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", status.equals("locked") ? "locked" : "error");
            response.put("action", "login");
            response.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole()));
            response.put("message", message);
            response.put("errors", Arrays.asList("Authentication failed. Attempt " + attempts + " of 3."));
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        // Success Login
        user.setFailedAttempts(0);
        userRepository.save(user);

        String token = jwtService.signToken(user);
        sessionLogger.logEvent("login", email, ip, "Successful login");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "login");
        response.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole(), "department", user.getDepartment() != null ? user.getDepartment() : ""));
        response.put("session", Map.of("token", token, "expiresIn", "1 hour", "isValid", true));
        response.put("redirect", getRoleRedirect(user.getRole()));
        response.put("message", "Welcome back, " + user.getName() + "!");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 2. Google SSO Simulation
    @PostMapping("/auth/google-sso")
    public ResponseEntity<Map<String, Object>> googleSSO(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        String name = body.get("name");
        String googleToken = body.get("googleToken");
        String ip = getClientIp(request);

        if (email == null || googleToken == null) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Google Auth signature is missing.", 
                    "Google verification token and email are required.");
        }

        if (!email.endsWith("@cit.edu") && !email.endsWith("@student.cit.edu")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Access Restricted.", 
                    "Only institutional Google accounts (@cit.edu or @student.cit.edu) are accepted.");
        }

        if ("EXPIRED_TOKEN".equals(googleToken)) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "SSO Login Failed.", 
                    "The Google Auth token has expired or is invalid.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;

        if (userOpt.isEmpty()) {
            // Dynamic Registration
            String defaultRole = email.endsWith("@student.cit.edu") ? "Student" : "Teacher";
            String defaultDept = email.endsWith("@student.cit.edu") ? "BS Computer Science" : "College of Computer Studies";
            int idSeed = 100 + new Random().nextInt(900);
            String newId = "USR-" + idSeed;

            user = new User(newId, name != null ? name : "Google SSO User", email, null, defaultRole, defaultDept, "active");
            userRepository.save(user);
            sessionLogger.logEvent("login", email, ip, "SSO dynamic registration completed for " + newId);
        } else {
            user = userOpt.get();
            if ("locked".equals(user.getStatus())) {
                sessionLogger.logEvent("failed_attempt", email, ip, "SSO login blocked: Account locked");
                return buildErrorResponse(HttpStatus.FORBIDDEN, "Your account is locked. Please contact your administrator.", "Account status is locked.");
            }
            if ("Pending Approval".equals(user.getStatus())) {
                sessionLogger.logEvent("failed_attempt", email, ip, "SSO login blocked: Account pending approval");
                return buildErrorResponse(HttpStatus.FORBIDDEN, "Account pending admin approval", "This account requires administrator review before activation.");
            }
        }

        String token = jwtService.signToken(user);
        sessionLogger.logEvent("login", email, ip, "Google SSO Login Successful");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "login");
        response.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole(), "department", user.getDepartment() != null ? user.getDepartment() : ""));
        response.put("session", Map.of("token", token, "expiresIn", "1 hour", "isValid", true));
        response.put("redirect", getRoleRedirect(user.getRole()));
        response.put("message", "Welcome back via Google SSO, " + user.getName() + "!");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 3. Microsoft SSO Simulation
    @PostMapping("/auth/microsoft-sso")
    public ResponseEntity<Map<String, Object>> microsoftSSO(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        String name = body.get("name");
        String microsoftToken = body.get("microsoftToken");
        String ip = getClientIp(request);

        if (email == null || microsoftToken == null) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Microsoft Auth signature is missing.", 
                    "Microsoft verification token and email are required.");
        }

        if (!email.endsWith("@cit.edu") && !email.endsWith("@student.cit.edu")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Access Restricted.", 
                    "Only institutional Outlook accounts (@cit.edu or @student.cit.edu) are accepted.");
        }

        if ("EXPIRED_TOKEN".equals(microsoftToken)) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Microsoft SSO Login Failed.", 
                    "The Microsoft Auth token has expired or is invalid.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;

        if (userOpt.isEmpty()) {
            // Dynamic Registration
            String defaultRole = email.endsWith("@student.cit.edu") ? "Student" : "Teacher";
            String defaultDept = email.endsWith("@student.cit.edu") ? "BS Computer Science" : "College of Computer Studies";
            int idSeed = 100 + new Random().nextInt(900);
            String newId = "USR-" + idSeed;

            user = new User(newId, name != null ? name : "Microsoft SSO User", email, null, defaultRole, defaultDept, "active");
            userRepository.save(user);
            sessionLogger.logEvent("login", email, ip, "Microsoft SSO dynamic registration completed for " + newId);
        } else {
            user = userOpt.get();
            if ("locked".equals(user.getStatus())) {
                sessionLogger.logEvent("failed_attempt", email, ip, "Microsoft SSO login blocked: Account locked");
                return buildErrorResponse(HttpStatus.FORBIDDEN, "Your account is locked. Please contact your administrator.", "Account status is locked.");
            }
            if ("Pending Approval".equals(user.getStatus())) {
                sessionLogger.logEvent("failed_attempt", email, ip, "Microsoft SSO login blocked: Account pending approval");
                return buildErrorResponse(HttpStatus.FORBIDDEN, "Account pending admin approval", "This account requires administrator review before activation.");
            }
        }

        String token = jwtService.signToken(user);
        sessionLogger.logEvent("login", email, ip, "Microsoft SSO Login Successful");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "login");
        response.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole(), "department", user.getDepartment() != null ? user.getDepartment() : ""));
        response.put("session", Map.of("token", token, "expiresIn", "1 hour", "isValid", true));
        response.put("redirect", getRoleRedirect(user.getRole()));
        response.put("message", "Welcome back via Microsoft SSO, " + user.getName() + "!");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 4. Student Self-Registration
    @PostMapping("/auth/register/student")
    public ResponseEntity<Map<String, Object>> registerStudent(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String name = body.get("name");
        String email = body.get("email");
        String studentId = body.get("studentId");
        String password = body.get("password");
        String deviceId = body.get("deviceId");
        String ip = getClientIp(request);

        if (name == null || email == null || studentId == null || password == null) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid submission.", 
                    "All registration fields (name, email, student ID, password) are required.");
        }

        if (!email.endsWith("@student.cit.edu") && !email.endsWith("@cit.edu")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Registration Filter Rejection.", 
                    "Students must register using a valid institutional email (@student.cit.edu or @cit.edu).");
        }

        Optional<User> exists = userRepository.findByIdOrEmail(studentId, email);
        if (exists.isPresent()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Duplicate Account Detected.", 
                    "An account with this email or Student ID has already been registered in EvacSense.");
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        User student = new User(studentId, name, email, hash, "Student", "BS Information Technology", "active");
        student.setDeviceId(deviceId);
        userRepository.save(student);

        sessionLogger.logEvent("login", email, ip, "Student account self-registration verified. ID: " + studentId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "registration");
        response.put("user", Map.of("id", student.getId(), "name", student.getName(), "email", student.getEmail(), "role", student.getRole()));
        response.put("session", null);
        response.put("redirect", "/");
        response.put("message", "Student Registration Successful! You can now log in securely.");
        response.put("errors", Collections.emptyList());

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 5. Staff Registration (Teacher/Coordinator)
    @PostMapping("/auth/register/staff")
    public ResponseEntity<Map<String, Object>> registerStaff(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String name = body.get("name");
        String email = body.get("email");
        String employeeId = body.get("employeeId");
        String password = body.get("password");
        String deviceId = body.get("deviceId");
        String role = body.get("role");
        String ip = getClientIp(request);

        if (name == null || email == null || employeeId == null || password == null || role == null) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid submission.", 
                    "All registration fields (name, email, employee ID, password, role) are required.");
        }

        if (!email.endsWith("@cit.edu") && !email.endsWith("@student.cit.edu")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Registration Filter Rejection.", 
                    "Staff/Faculty must register using a valid institutional email (@cit.edu or @student.cit.edu).");
        }

        if (!"Teacher".equals(role) && !"Drill Coordinator".equals(role)) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid role assignment.", 
                    "Requested role must be Teacher or Drill Coordinator.");
        }

        Optional<User> exists = userRepository.findByIdOrEmail(employeeId, email);
        if (exists.isPresent()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Duplicate Account Detected.", 
                    "An account with this email or Employee ID is already registered.");
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        String dept = "Teacher".equals(role) ? "College of Computer Studies" : "Safety Office";
        
        User staff = new User(employeeId, name, email, hash, role, dept, "Pending Approval");
        staff.setDeviceId(deviceId);
        userRepository.save(staff);

        sessionLogger.logEvent("recovery", email, ip, "Staff registration request submitted. Role: " + role + ", ID: " + employeeId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "pending");
        response.put("action", "registration");
        response.put("user", Map.of("id", staff.getId(), "name", staff.getName(), "email", staff.getEmail(), "role", staff.getRole(), "department", staff.getDepartment()));
        response.put("session", null);
        response.put("redirect", "/");
        response.put("message", "Account pending admin approval");
        response.put("errors", Collections.emptyList());

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 6. Token Validation Endpoint
    @RequireRole
    @GetMapping("/auth/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        String token = (String) request.getAttribute("token");
        String ip = getClientIp(request);

        sessionLogger.logEvent("token_validation", user.getEmail(), ip, "Token validation check successful");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "token_validation");
        response.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole(), "department", user.getDepartment() != null ? user.getDepartment() : ""));
        response.put("session", Map.of("token", token, "expiresIn", "1 hour", "isValid", true));
        response.put("redirect", getRoleRedirect(user.getRole()));
        response.put("message", "Security token is valid.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 7. Logout
    @RequireRole
    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        String ip = getClientIp(request);

        if (user != null) {
            sessionLogger.logEvent("logout", user.getEmail(), ip, "User logged out successfully");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "logout");
        response.put("user", null);
        response.put("session", null);
        response.put("redirect", "/");
        response.put("message", "Logged out successfully. Session invalidated.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 8. Recovery Request
    @PostMapping("/auth/recovery")
    public ResponseEntity<Map<String, Object>> recoverAccount(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        String ip = getClientIp(request);

        if (email == null) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Recovery email is required.", "Email parameter is missing.");
        }

        if (!email.endsWith("@cit.edu") && !email.endsWith("@student.cit.edu")) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Institutional Email.", "Only institutional emails are registered in EvacSense.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Account not found.", 
                    "The institutional email '" + email + "' is not registered in the system.");
        }

        User user = userOpt.get();
        sessionLogger.logEvent("recovery", email, ip, "Simulated password recovery link dispatched");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "recovery");
        response.put("user", Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole()));
        response.put("session", null);
        response.put("message", "Recovery email has been dispatched to " + email + ". Please check your institutional inbox.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 9. List Users (System Admin)
    @RequireRole("System Admin")
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> listUsers(HttpServletRequest request) {
        User admin = (User) request.getAttribute("currentUser");
        List<User> users = userRepository.findAll();

        List<Map<String, Object>> resolvedUsers = new ArrayList<>();
        for (User u : users) {
            resolvedUsers.add(Map.of(
                    "id", u.getId(),
                    "name", u.getName(),
                    "email", u.getEmail(),
                    "role", u.getRole(),
                    "department", u.getDepartment() != null ? u.getDepartment() : "",
                    "failedAttempts", u.getFailedAttempts(),
                    "status", u.getStatus()
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "list_users");
        response.put("user", Map.of("id", admin.getId(), "name", admin.getName(), "email", admin.getEmail(), "role", admin.getRole()));
        response.put("users", resolvedUsers);
        response.put("message", "All registered users fetched successfully.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 10. Update Role (System Admin)
    @RequireRole("System Admin")
    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(@PathVariable String id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = body.get("role");
        User admin = (User) request.getAttribute("currentUser");
        String ip = getClientIp(request);

        List<String> validRoles = Arrays.asList("Student", "Teacher", "Drill Coordinator", "System Admin");
        if (role == null || !validRoles.contains(role)) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid role assignment request.", 
                    "Role must be one of: " + String.join(", ", validRoles));
        }

        Optional<User> targetUserOpt = userRepository.findById(id);
        if (targetUserOpt.isEmpty()) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Target user not found.", "No user found matching ID: " + id);
        }

        User targetUser = targetUserOpt.get();
        String previousRole = targetUser.getRole();
        targetUser.setRole(role);
        userRepository.save(targetUser);

        sessionLogger.logEvent("role_update", targetUser.getEmail(), ip, 
                "Role updated from '" + previousRole + "' to '" + role + "' by " + admin.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "role_update");
        response.put("user", Map.of("id", targetUser.getId(), "name", targetUser.getName(), "email", targetUser.getEmail(), "role", targetUser.getRole()));
        response.put("message", "Successfully updated " + targetUser.getName() + "'s role from '" + previousRole + "' to '" + role + "'.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 11. List Pending Staff Registration Requests (System Admin)
    @RequireRole("System Admin")
    @GetMapping("/admin/requests")
    public ResponseEntity<Map<String, Object>> listPendingRequests(HttpServletRequest request) {
        User admin = (User) request.getAttribute("currentUser");
        List<User> pending = userRepository.findByStatus("Pending Approval");

        List<Map<String, Object>> resolvedPending = new ArrayList<>();
        for (User u : pending) {
            resolvedPending.add(Map.of(
                    "id", u.getId(),
                    "name", u.getName(),
                    "email", u.getEmail(),
                    "role", u.getRole(),
                    "department", u.getDepartment() != null ? u.getDepartment() : "",
                    "deviceId", u.getDeviceId() != null ? u.getDeviceId() : "",
                    "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "list_pending");
        response.put("user", Map.of("id", admin.getId(), "name", admin.getName(), "email", admin.getEmail(), "role", admin.getRole()));
        response.put("requests", resolvedPending);
        response.put("message", "Pending registration requests fetched successfully.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 12. Approve Staff Registration Request (System Admin)
    @RequireRole("System Admin")
    @PutMapping("/admin/requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveRequest(@PathVariable String id, HttpServletRequest request) {
        User admin = (User) request.getAttribute("currentUser");
        String ip = getClientIp(request);

        Optional<User> targetUserOpt = userRepository.findById(id);
        if (targetUserOpt.isEmpty() || !"Pending Approval".equals(targetUserOpt.get().getStatus())) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Pending request not found.", "No pending request matched ID: " + id);
        }

        User targetUser = targetUserOpt.get();
        targetUser.setStatus("active");
        userRepository.save(targetUser);

        sessionLogger.logEvent("role_update", targetUser.getEmail(), ip, 
                "Registration request approved by Admin: " + admin.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "approve_registration");
        response.put("user", Map.of("id", targetUser.getId(), "name", targetUser.getName(), "email", targetUser.getEmail(), "role", targetUser.getRole()));
        response.put("message", "Registration request for " + targetUser.getName() + " has been approved successfully.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 13. Reject Staff Registration Request (System Admin)
    @RequireRole("System Admin")
    @PutMapping("/admin/requests/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectRequest(@PathVariable String id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String reason = body.get("reason");
        User admin = (User) request.getAttribute("currentUser");
        String ip = getClientIp(request);

        if (reason == null || reason.trim().isEmpty()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Rejection reason is required.", 
                    "You must provide a clear validation reason to reject a registration.");
        }

        Optional<User> targetUserOpt = userRepository.findById(id);
        if (targetUserOpt.isEmpty() || !"Pending Approval".equals(targetUserOpt.get().getStatus())) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Pending request not found.", "No pending request matched ID: " + id);
        }

        User targetUser = targetUserOpt.get();
        targetUser.setStatus("Rejected");
        userRepository.save(targetUser);

        sessionLogger.logEvent("role_update", targetUser.getEmail(), ip, 
                "Registration request REJECTED by Admin: " + admin.getEmail() + ". Reason: " + reason);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "reject_registration");
        response.put("user", Map.of("id", targetUser.getId(), "name", targetUser.getName(), "email", targetUser.getEmail()));
        response.put("message", "Registration request for " + targetUser.getName() + " was rejected. Reason: " + reason);
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 14. Register Student Photo (Facial Verification Baseline)
    @RequireRole("Student")
    @PostMapping("/auth/register-photo")
    public ResponseEntity<Map<String, Object>> registerStudentPhoto(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String photoBase64 = body.get("photoBase64");
        User student = (User) request.getAttribute("currentUser");
        String ip = getClientIp(request);

        if (photoBase64 == null || photoBase64.trim().isEmpty()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Photo data is missing.", 
                    "You must provide a valid Base64 encoded image.");
        }

        student.setPhotoBase64(photoBase64);
        userRepository.save(student);

        sessionLogger.logEvent("photo_registration", student.getEmail(), ip, 
                "Student " + student.getId() + " registered facial biometric photo.");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("action", "register_photo");
        response.put("message", "Facial biometric registered successfully.");
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }

    // 15. Check if student has a registered baseline photo
    @RequireRole("Student")
    @GetMapping("/auth/photo-status")
    public ResponseEntity<Map<String, Object>> getPhotoStatus(HttpServletRequest request) {
        User student = (User) request.getAttribute("currentUser");

        boolean hasPhoto = student.getPhotoBase64() != null && !student.getPhotoBase64().trim().isEmpty();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("hasPhoto", hasPhoto);
        if (hasPhoto) {
            response.put("photoBase64", student.getPhotoBase64());
        }
        response.put("errors", Collections.emptyList());

        return ResponseEntity.ok(response);
    }
}
