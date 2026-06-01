package com.evacsense.repository;

import com.evacsense.model.ClassroomOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomOccupancyRepository extends JpaRepository<ClassroomOccupancy, Integer> {
    List<ClassroomOccupancy> findByDrillId(Integer drillId);
    Optional<ClassroomOccupancy> findByDrillIdAndUserId(Integer drillId, String userId);
    List<ClassroomOccupancy> findByDrillIdAndRoomId(Integer drillId, String roomId);
    void deleteByDrillId(Integer drillId);
}
