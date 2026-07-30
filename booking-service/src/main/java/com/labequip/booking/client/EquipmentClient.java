package com.labequip.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Synchronous, typed HTTP client to Equipment Service (Service B), resolved via Eureka.
 * Wrapped with Resilience4J in EquipmentAvailabilityService - this interface stays a plain
 * declarative client.
 */
@FeignClient(name = "equipment-service", configuration = com.labequip.booking.config.FeignHeaderForwardingConfig.class)
public interface EquipmentClient {

    @GetMapping("/api/equipment/{id}")
    EquipmentDto getEquipment(@PathVariable("id") Long id);
}
