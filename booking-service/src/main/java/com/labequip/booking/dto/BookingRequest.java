package com.labequip.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookingRequest(
        @NotNull(message = "equipmentId is required") Long equipmentId,
        @NotNull(message = "startTime is required") @Future(message = "startTime must be in the future") Instant startTime,
        @NotNull(message = "endTime is required") @Future(message = "endTime must be in the future") Instant endTime
) {
}
