package com.labequip.booking.web;

import com.labequip.booking.domain.Booking;
import com.labequip.booking.dto.BookingCompletionRequest;
import com.labequip.booking.dto.BookingRequest;
import com.labequip.booking.dto.BookingResponse;
import com.labequip.booking.security.RoleGuard;
import com.labequip.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Read/create/complete/cancel are available to any authenticated user.
 * Hard delete is restricted to ADMIN - the other endpoint category required by the assignment.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final RoleGuard roleGuard;

    public BookingController(BookingService bookingService, RoleGuard roleGuard) {
        this.bookingService = bookingService;
        this.roleGuard = roleGuard;
    }

    @GetMapping
    public List<BookingResponse> findAll(@RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        return bookingService.findAll().stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BookingResponse findById(@PathVariable Long id,
                                     @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        return BookingResponse.from(bookingService.findById(id));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request,
                                                    @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        Booking created = bookingService.create(request, authUser);
        return ResponseEntity.created(URI.create("/api/bookings/" + created.getId()))
                .body(BookingResponse.from(created));
    }

    @PutMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable Long id,
                                     @Valid @RequestBody BookingCompletionRequest request,
                                     @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        return BookingResponse.from(bookingService.complete(id, request));
    }

    @PutMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id,
                                   @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        bookingService.cancel(id);
        return BookingResponse.from(bookingService.findById(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                        @RequestHeader(value = "X-Auth-User", required = false) String authUser,
                        @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
        roleGuard.requireRole(authUser, roles, "ADMIN");
        bookingService.delete(id);
    }
}
