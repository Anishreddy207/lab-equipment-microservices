package com.labequip.booking.service;

import com.labequip.booking.client.EquipmentDto;
import com.labequip.booking.domain.Booking;
import com.labequip.booking.domain.BookingStatus;
import com.labequip.booking.dto.BookingCompletionRequest;
import com.labequip.booking.dto.BookingRequest;
import com.labequip.booking.messaging.MaintenanceEventPublisher;
import com.labequip.booking.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EquipmentAvailabilityService equipmentAvailabilityService;
    private final MaintenanceEventPublisher maintenanceEventPublisher;

    public BookingService(BookingRepository bookingRepository,
                           EquipmentAvailabilityService equipmentAvailabilityService,
                           MaintenanceEventPublisher maintenanceEventPublisher) {
        this.bookingRepository = bookingRepository;
        this.equipmentAvailabilityService = equipmentAvailabilityService;
        this.maintenanceEventPublisher = maintenanceEventPublisher;
    }

    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    public Booking findById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking " + id + " not found"));
    }

    /**
     * Synchronously checks equipment availability with Equipment Service before confirming the
     * booking - this is the required sync inter-service call, protected by Resilience4J in
     * EquipmentAvailabilityService.
     */
    public Booking create(BookingRequest request, String requestedBy) {
        EquipmentDto equipment = equipmentAvailabilityService.fetchEquipment(request.equipmentId());

        if (!"AVAILABLE".equals(equipment.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment " + equipment.id() + " is not available (status=" + equipment.status() + ")");
        }

        Booking booking = new Booking();
        booking.setEquipmentId(request.equipmentId());
        booking.setRequestedBy(requestedBy);
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(booking);
    }

    /**
     * Marks a booking completed and, if the equipment was reported faulty, publishes the
     * MaintenanceRequested domain event asynchronously - this state change (equipment now needs
     * maintenance) doesn't need Booking Service to wait for Equipment Service to process it.
     */
    public Booking complete(Long id, BookingCompletionRequest request) {
        Booking booking = findById(id);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setFaultReported(request.faultReported());
        booking.setIssueDescription(request.issueDescription());
        Booking saved = bookingRepository.save(booking);

        if (request.faultReported()) {
            maintenanceEventPublisher.publishMaintenanceRequested(
                    saved.getEquipmentId(), saved.getId(), request.issueDescription(), saved.getRequestedBy());
        }

        return saved;
    }

    public void cancel(Long id) {
        Booking booking = findById(id);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    public void delete(Long id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking " + id + " not found");
        }
        bookingRepository.deleteById(id);
    }
}
