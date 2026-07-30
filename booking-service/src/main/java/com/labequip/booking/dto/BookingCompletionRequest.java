package com.labequip.booking.dto;

public record BookingCompletionRequest(
        boolean faultReported,
        String issueDescription
) {
}
