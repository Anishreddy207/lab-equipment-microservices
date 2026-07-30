package com.labequip.events;

import java.io.Serializable;
import java.time.Instant;

/**
 * Domain event published after a booking is completed and the equipment is reported faulty.
 * Deliberately duplicated (same package + class name, not a shared library dependency) in
 * equipment-service - this is the payload contract, not compile-time coupling between services.
 */
public record MaintenanceRequestedEvent(
        Long equipmentId,
        Long bookingId,
        String issueDescription,
        String reportedBy,
        Instant occurredAt
) implements Serializable {
}
