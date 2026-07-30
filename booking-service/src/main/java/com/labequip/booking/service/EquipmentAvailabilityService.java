package com.labequip.booking.service;

import com.labequip.booking.client.EquipmentClient;
import com.labequip.booking.client.EquipmentDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Wraps the synchronous call to Equipment Service with Resilience4J. If Equipment Service is
 * unavailable or slow (its own JVM down, network partition, etc.) the circuit breaker/retry
 * budget is exhausted and equipmentUnavailableFallback runs instead of the caller hanging or the
 * whole booking request crashing with a raw connection exception.
 */
@Service
public class EquipmentAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentAvailabilityService.class);

    private final EquipmentClient equipmentClient;

    public EquipmentAvailabilityService(EquipmentClient equipmentClient) {
        this.equipmentClient = equipmentClient;
    }

    @CircuitBreaker(name = "equipmentService", fallbackMethod = "equipmentUnavailableFallback")
    @Retry(name = "equipmentService")
    public EquipmentDto fetchEquipment(Long equipmentId) {
        return equipmentClient.getEquipment(equipmentId);
    }

    @SuppressWarnings("unused")
    private EquipmentDto equipmentUnavailableFallback(Long equipmentId, Throwable throwable) {
        log.warn("Equipment Service unavailable/slow while checking equipment {} - falling back. Cause: {}",
                equipmentId, throwable.toString());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Equipment Service is currently unavailable - could not verify equipment " + equipmentId
                        + ". Please try again shortly.");
    }
}
