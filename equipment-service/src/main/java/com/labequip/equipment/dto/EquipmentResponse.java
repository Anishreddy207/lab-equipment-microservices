package com.labequip.equipment.dto;

import com.labequip.equipment.domain.Equipment;
import com.labequip.equipment.domain.EquipmentStatus;

import java.time.Instant;

public record EquipmentResponse(
        Long id,
        String name,
        String category,
        String location,
        EquipmentStatus status,
        String conditionNotes,
        Instant createdAt,
        Instant updatedAt
) {
    public static EquipmentResponse from(Equipment e) {
        return new EquipmentResponse(e.getId(), e.getName(), e.getCategory(), e.getLocation(),
                e.getStatus(), e.getConditionNotes(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
