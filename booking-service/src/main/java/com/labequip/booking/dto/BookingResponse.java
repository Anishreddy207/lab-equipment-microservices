package com.labequip.booking.dto;

import com.labequip.booking.domain.Booking;
import com.labequip.booking.domain.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long equipmentId,
        String requestedBy,
        Instant startTime,
        Instant endTime,
        BookingStatus status,
        boolean faultReported,
        String issueDescription,
        Instant createdAt,
        Instant updatedAt
) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getEquipmentId(), b.getRequestedBy(), b.getStartTime(),
                b.getEndTime(), b.getStatus(), b.isFaultReported(), b.getIssueDescription(),
                b.getCreatedAt(), b.getUpdatedAt());
    }
}
