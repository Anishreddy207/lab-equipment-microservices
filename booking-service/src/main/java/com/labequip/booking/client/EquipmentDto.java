package com.labequip.booking.client;

/**
 * Local mirror of equipment-service's EquipmentResponse - only the fields Booking Service needs
 * from the synchronous call. Deliberately not a shared library dependency, so each service stays
 * independently deployable.
 */
public record EquipmentDto(
        Long id,
        String name,
        String category,
        String location,
        String status
) {
}
