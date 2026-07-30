package com.labequip.equipment.dto;

import com.labequip.equipment.domain.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EquipmentRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "category is required") String category,
        @NotBlank(message = "location is required") String location,
        @NotNull(message = "status is required") EquipmentStatus status,
        String conditionNotes
) {
}
