package com.labequip.equipment.service;

import com.labequip.equipment.domain.Equipment;
import com.labequip.equipment.domain.EquipmentStatus;
import com.labequip.equipment.domain.MaintenanceRecord;
import com.labequip.equipment.domain.MaintenanceStatus;
import com.labequip.equipment.dto.MaintenanceStatusUpdateRequest;
import com.labequip.events.MaintenanceRequestedEvent;
import com.labequip.equipment.repository.EquipmentRepository;
import com.labequip.equipment.repository.MaintenanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class MaintenanceRecordService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRecordService.class);

    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final EquipmentRepository equipmentRepository;

    public MaintenanceRecordService(MaintenanceRecordRepository maintenanceRecordRepository,
                                     EquipmentRepository equipmentRepository) {
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.equipmentRepository = equipmentRepository;
    }

    public List<MaintenanceRecord> findAll() {
        return maintenanceRecordRepository.findAll();
    }

    public MaintenanceRecord findById(Long id) {
        return maintenanceRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance record " + id + " not found"));
    }

    public MaintenanceRecord updateStatus(Long id, MaintenanceStatusUpdateRequest request) {
        MaintenanceRecord record = findById(id);
        record.setStatus(request.status());
        if (request.status() == MaintenanceStatus.RESOLVED) {
            record.setResolvedAt(Instant.now());
            Equipment equipment = equipmentRepository.findById(record.getEquipmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
            equipment.setStatus(EquipmentStatus.AVAILABLE);
            equipmentRepository.save(equipment);
        }
        return maintenanceRecordRepository.save(record);
    }

    /**
     * Consumer side of the async domain event: a completed booking reported equipment as faulty,
     * so this service updates its own state (equipment status) and opens a maintenance record,
     * without the Booking Service ever needing a direct, blocking dependency on this write.
     */
    @Transactional
    public void handleMaintenanceRequested(MaintenanceRequestedEvent event) {
        Equipment equipment = equipmentRepository.findById(event.equipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment " + event.equipmentId() + " not found for maintenance event"));

        equipment.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
        equipmentRepository.save(equipment);

        MaintenanceRecord record = new MaintenanceRecord();
        record.setEquipmentId(event.equipmentId());
        record.setReportedIssue(event.issueDescription());
        record.setReportedBy(event.reportedBy());
        record.setSourceBookingId(event.bookingId());
        record.setStatus(MaintenanceStatus.OPEN);
        maintenanceRecordRepository.save(record);

        log.info("Processed MaintenanceRequested event: equipmentId={} bookingId={} -> equipment set to UNDER_MAINTENANCE, maintenance record {} created",
                event.equipmentId(), event.bookingId(), record.getId());
    }
}
