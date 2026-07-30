package com.labequip.equipment.dto;

import com.labequip.equipment.domain.MaintenanceRecord;
import com.labequip.equipment.domain.MaintenanceStatus;

import java.time.Instant;

public record MaintenanceRecordResponse(
        Long id,
        Long equipmentId,
        String reportedIssue,
        String reportedBy,
        MaintenanceStatus status,
        Long sourceBookingId,
        Instant createdAt,
        Instant resolvedAt
) {
    public static MaintenanceRecordResponse from(MaintenanceRecord m) {
        return new MaintenanceRecordResponse(m.getId(), m.getEquipmentId(), m.getReportedIssue(),
                m.getReportedBy(), m.getStatus(), m.getSourceBookingId(), m.getCreatedAt(), m.getResolvedAt());
    }
}
