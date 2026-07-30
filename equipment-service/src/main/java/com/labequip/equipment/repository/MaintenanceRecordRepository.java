package com.labequip.equipment.repository;

import com.labequip.equipment.domain.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {
    List<MaintenanceRecord> findByEquipmentId(Long equipmentId);
}
