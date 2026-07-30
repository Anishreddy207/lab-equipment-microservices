package com.labequip.equipment.dto;

import com.labequip.equipment.domain.MaintenanceStatus;
import jakarta.validation.constraints.NotNull;

public record MaintenanceStatusUpdateRequest(
        @NotNull(message = "status is required") MaintenanceStatus status
) {
}
