package com.evacsense.config;

import com.evacsense.model.*;
import com.evacsense.repository.*;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private AccessPointRepository accessPointRepository;

    @Autowired
    private EdgeRepository edgeRepository;

    @Autowired
    private ClassroomAttendanceRepository classroomAttendanceRepository;

    @Autowired
    private ClassroomOccupancyRepository classroomOccupancyRepository;

    @Override
    public void run(String... args) throws Exception {
        try {
            // Only seed if the database is empty — prevents data wipe on every restart
            if (roomRepository.count() > 0) {
                System.out.println("[DATABASE] Data already exists. Skipping seeding.");
                return;
            }

            System.out.println("[DATABASE] Beginning database auto-seeding process...");

            // 2. Mock Users Seeding
            String defaultPasswordHash = BCrypt.hashpw("CITCCS2026!", BCrypt.gensalt(10));

            List<User> mockUsers = Arrays.asList(
                    new User("USR-001", "Maria Santos", "m.santos@student.cit.edu", defaultPasswordHash, "Student", "BS Information Technology", "active"),
                    new User("USR-002", "Dr. Jose Reyes", "j.reyes@cit.edu", defaultPasswordHash, "Teacher", "College of Computer Studies", "active"),
                    new User("USR-003", "Engr. Ana Cruz", "a.cruz@cit.edu", defaultPasswordHash, "Drill Coordinator", "Safety Office", "active"),
                    new User("USR-004", "Mr. Carlo Lim", "c.lim@cit.edu", defaultPasswordHash, "System Admin", "IT Department", "active"),
                    new User("USR-005", "Juan dela Cruz", "j.delacruz@student.cit.edu", defaultPasswordHash, "Student", "BS Computer Science", "active")
            );

            userRepository.saveAll(mockUsers);
            System.out.println("[DATABASE] Seeded " + mockUsers.size() + " active CIT-U mock users.");

            // 3. Seed Rooms (NGE Building)
            List<Room> rooms = Arrays.asList(
                    // Floor 1
                    new Room("ROOM-101", "Computer Laboratory 101", 1, "NGE Building", 30.0f, 42.0f, 28.0f, 35.0f),
                    new Room("ROOM-102", "Computer Laboratory 102", 1, "NGE Building", 30.0f, 42.0f, 21.0f, 28.0f),
                    new Room("ROOM-103", "Computer Laboratory 103", 1, "NGE Building", 30.0f, 42.0f, 14.0f, 21.0f),
                    new Room("ROOM-104", "Computer Laboratory 104", 1, "NGE Building", 30.0f, 42.0f, 7.0f, 14.0f),
                    new Room("ROOM-105", "Computer Laboratory 105", 1, "NGE Building", 30.0f, 42.0f, 0.0f, 7.0f),
                    new Room("ROOM-106", "College of Computer Studies Office", 1, "NGE Building", 15.0f, 30.0f, 0.0f, 7.0f),
                    new Room("ROOM-107", "RDCO/ITSO Conference Room", 1, "NGE Building", 0.0f, 15.0f, 10.0f, 20.0f),
                    new Room("ROOM-108", "Case Room 108", 1, "NGE Building", 0.0f, 15.0f, 22.0f, 32.0f),
                    new Room("CORRIDOR-1A", "Ground Floor Main Hallway", 1, "NGE Building", 15.0f, 30.0f, 7.0f, 35.0f),
                    new Room("STAIR-MAIN-F1", "Main Staircase (Ground)", 1, "NGE Building", 18.0f, 22.0f, 33.0f, 37.0f),
                    new Room("STAIR-FIRE-F1", "NW Fire Exit Staircase (Ground)", 1, "NGE Building", 0.0f, 4.0f, 0.0f, 4.0f),

                    // Floor 2
                    new Room("ROOM-201", "Computer Laboratory 201", 2, "NGE Building", 30.0f, 42.0f, 28.0f, 35.0f),
                    new Room("ROOM-202", "Computer Laboratory 202", 2, "NGE Building", 30.0f, 42.0f, 21.0f, 28.0f),
                    new Room("ROOM-203", "Computer Laboratory 203", 2, "NGE Building", 30.0f, 42.0f, 14.0f, 21.0f),
                    new Room("ROOM-204", "Computer Laboratory 204", 2, "NGE Building", 30.0f, 42.0f, 7.0f, 14.0f),
                    new Room("ROOM-205", "Computer Laboratory 205", 2, "NGE Building", 30.0f, 42.0f, 0.0f, 7.0f),
                    new Room("ROOM-206", "CS Faculty Office", 2, "NGE Building", 15.0f, 30.0f, 0.0f, 7.0f),
                    new Room("ROOM-207", "eLearning Competency & Research Center", 2, "NGE Building", 0.0f, 15.0f, 10.0f, 22.0f),
                    new Room("CORRIDOR-2A", "2nd Floor Main Hallway", 2, "NGE Building", 15.0f, 30.0f, 7.0f, 35.0f),
                    new Room("STAIR-MAIN-F2", "Main Staircase (2nd Floor)", 2, "NGE Building", 18.0f, 22.0f, 33.0f, 37.0f),
                    new Room("STAIR-FIRE-F2", "NW Fire Exit Staircase (2nd Floor)", 2, "NGE Building", 0.0f, 4.0f, 0.0f, 4.0f),

                    // Floor 3
                    new Room("ROOM-301", "Lecture Room 301", 3, "NGE Building", 36.0f, 45.0f, 0.0f, 8.0f),
                    new Room("ROOM-302", "Nursing Laboratory 302", 3, "NGE Building", 27.0f, 36.0f, 0.0f, 8.0f),
                    new Room("ROOM-303", "Nursing Laboratory 303", 3, "NGE Building", 18.0f, 27.0f, 0.0f, 8.0f),
                    new Room("ROOM-304", "Technical Support Group Office", 3, "NGE Building", 9.0f, 18.0f, 0.0f, 8.0f),
                    new Room("ROOM-305", "Nursing Laboratory 305", 3, "NGE Building", 0.0f, 9.0f, 0.0f, 8.0f),
                    new Room("ROOM-306", "TSG / Network Operation Center", 3, "NGE Building", 0.0f, 10.0f, 10.0f, 22.0f),
                    new Room("ROOM-307", "CNAHS Faculty Room 2", 3, "NGE Building", 9.0f, 18.0f, 24.0f, 32.0f),
                    new Room("ROOM-308", "Microbiology & Parasitology Room", 3, "NGE Building", 18.0f, 30.0f, 24.0f, 32.0f),
                    new Room("ROOM-309", "Human Anatomy & Physiology Lab", 3, "NGE Building", 30.0f, 42.0f, 24.0f, 32.0f),
                    new Room("CORRIDOR-3A", "3rd Floor North Hallway", 3, "NGE Building", 0.0f, 45.0f, 8.0f, 12.0f),
                    new Room("CORRIDOR-3B", "3rd Floor South Hallway", 3, "NGE Building", 0.0f, 42.0f, 20.0f, 24.0f),
                    new Room("STAIR-MAIN-F3", "Main Staircase (3rd Floor)", 3, "NGE Building", 0.0f, 4.0f, 8.0f, 12.0f),

                    // Floor 4
                    new Room("ROOM-4-OR", "Operating Room", 4, "NGE Building", 0.0f, 12.0f, 0.0f, 10.0f),
                    new Room("ROOM-4-WARD", "Ward", 4, "NGE Building", 18.0f, 38.0f, 0.0f, 10.0f),
                    new Room("ROOM-4-AMPHITHEATER", "Amphitheater 1", 4, "NGE Building", 38.0f, 48.0f, 0.0f, 10.0f),
                    new Room("ROOM-4-CON", "College of Nursing Office", 4, "NGE Building", 0.0f, 10.0f, 12.0f, 22.0f),
                    new Room("ROOM-4-CNAHS", "CNAHS Faculty Room 1", 4, "NGE Building", 0.0f, 15.0f, 24.0f, 32.0f),
                    new Room("ROOM-4-SKILLS", "Nursing Skills Laboratory", 4, "NGE Building", 15.0f, 38.0f, 24.0f, 32.0f),
                    new Room("CORRIDOR-4A", "4th Floor Main Hallway", 4, "NGE Building", 0.0f, 48.0f, 10.0f, 14.0f),
                    new Room("STAIR-MAIN-F4", "Main Staircase (4th Floor)", 4, "NGE Building", 0.0f, 4.0f, 10.0f, 14.0f),

                    // Exit
                    new Room("EXIT-CIT-FIELD", "CIT Field Assembly Area", 1, "CIT-U Campus", -20.0f, -5.0f, 15.0f, 30.0f)
            );

            roomRepository.saveAll(rooms);
            System.out.println("[DATABASE] Seeded " + rooms.size() + " NGE Building floor zones.");

            // 4. Seed Edges
            List<Edge> edges = Arrays.asList(
                    // Floor 1
                    new Edge("ROOM-101", "CORRIDOR-1A", 3.0f, false),
                    new Edge("ROOM-102", "CORRIDOR-1A", 3.0f, false),
                    new Edge("ROOM-103", "CORRIDOR-1A", 3.0f, false),
                    new Edge("ROOM-104", "CORRIDOR-1A", 3.0f, false),
                    new Edge("ROOM-105", "CORRIDOR-1A", 3.0f, false),
                    new Edge("ROOM-106", "CORRIDOR-1A", 4.0f, false),
                    new Edge("ROOM-107", "CORRIDOR-1A", 5.0f, false),
                    new Edge("ROOM-108", "CORRIDOR-1A", 5.0f, false),
                    new Edge("CORRIDOR-1A", "STAIR-MAIN-F1", 8.0f, false),
                    new Edge("CORRIDOR-1A", "STAIR-FIRE-F1", 12.0f, false),
                    new Edge("STAIR-MAIN-F1", "EXIT-CIT-FIELD", 15.0f, false),
                    new Edge("STAIR-FIRE-F1", "EXIT-CIT-FIELD", 10.0f, false),

                    // Floor 2
                    new Edge("ROOM-201", "CORRIDOR-2A", 3.0f, false),
                    new Edge("ROOM-202", "CORRIDOR-2A", 3.0f, false),
                    new Edge("ROOM-203", "CORRIDOR-2A", 3.0f, false),
                    new Edge("ROOM-204", "CORRIDOR-2A", 3.0f, false),
                    new Edge("ROOM-205", "CORRIDOR-2A", 3.0f, false),
                    new Edge("ROOM-206", "CORRIDOR-2A", 4.0f, false),
                    new Edge("ROOM-207", "CORRIDOR-2A", 5.0f, false),
                    new Edge("CORRIDOR-2A", "STAIR-MAIN-F2", 8.0f, false),
                    new Edge("CORRIDOR-2A", "STAIR-FIRE-F2", 12.0f, false),

                    // Floor 3
                    new Edge("ROOM-301", "CORRIDOR-3A", 3.0f, false),
                    new Edge("ROOM-302", "CORRIDOR-3A", 3.0f, false),
                    new Edge("ROOM-303", "CORRIDOR-3A", 3.0f, false),
                    new Edge("ROOM-304", "CORRIDOR-3A", 3.0f, false),
                    new Edge("ROOM-305", "CORRIDOR-3A", 4.0f, false),
                    new Edge("ROOM-306", "CORRIDOR-3B", 4.0f, false),
                    new Edge("CORRIDOR-3A", "CORRIDOR-3B", 12.0f, false),
                    new Edge("ROOM-307", "CORRIDOR-3B", 3.0f, false),
                    new Edge("ROOM-308", "CORRIDOR-3B", 3.0f, false),
                    new Edge("ROOM-309", "CORRIDOR-3B", 3.0f, false),
                    new Edge("CORRIDOR-3A", "STAIR-MAIN-F3", 10.0f, false),

                    // Floor 4
                    new Edge("ROOM-4-OR", "CORRIDOR-4A", 5.0f, false),
                    new Edge("ROOM-4-WARD", "CORRIDOR-4A", 4.0f, false),
                    new Edge("ROOM-4-AMPHITHEATER", "CORRIDOR-4A", 4.0f, false),
                    new Edge("ROOM-4-CON", "CORRIDOR-4A", 4.0f, false),
                    new Edge("ROOM-4-CNAHS", "CORRIDOR-4A", 6.0f, false),
                    new Edge("ROOM-4-SKILLS", "CORRIDOR-4A", 5.0f, false),
                    new Edge("CORRIDOR-4A", "STAIR-MAIN-F4", 8.0f, false),

                    // Staircases
                    new Edge("STAIR-MAIN-F1", "STAIR-MAIN-F2", 4.0f, false),
                    new Edge("STAIR-MAIN-F2", "STAIR-MAIN-F3", 4.0f, false),
                    new Edge("STAIR-MAIN-F3", "STAIR-MAIN-F4", 4.0f, false),
                    new Edge("STAIR-FIRE-F1", "STAIR-FIRE-F2", 4.0f, false)
            );

            edgeRepository.saveAll(edges);
            System.out.println("[DATABASE] Seeded " + edges.size() + " NGE building evacuation route edges.");

            // 5. Seed Access Points
            List<AccessPoint> aps = Arrays.asList(
                    new AccessPoint("AP-NGE-1A", "00:1a:2b:3c:4d:01", "CITU_NGE_1F_East", 36.0f, 17.5f, 1),
                    new AccessPoint("AP-NGE-1B", "00:1a:2b:3c:4d:02", "CITU_NGE_1F_West", 8.0f, 15.0f, 1),
                    new AccessPoint("AP-NGE-2A", "00:1a:2b:3c:4d:03", "CITU_NGE_2F_East", 36.0f, 17.5f, 2),
                    new AccessPoint("AP-NGE-2B", "00:1a:2b:3c:4d:04", "CITU_NGE_2F_West", 8.0f, 15.0f, 2),
                    new AccessPoint("AP-NGE-3A", "00:1a:2b:3c:4d:05", "CITU_NGE_3F_North", 22.0f, 5.0f, 3),
                    new AccessPoint("AP-NGE-3B", "00:1a:2b:3c:4d:06", "CITU_NGE_3F_South", 22.0f, 28.0f, 3)
            );

            accessPointRepository.saveAll(aps);
            System.out.println("[DATABASE] Seeded " + aps.size() + " NGE Building Wi-Fi Access Points.");
            System.out.println("[DATABASE] Auto-seeding complete.");

        } catch (Exception e) {
            System.err.println("[DATABASE] Seeding process failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
