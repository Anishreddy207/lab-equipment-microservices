package com.labequip.equipment.web;

import com.labequip.equipment.domain.Equipment;
import com.labequip.equipment.dto.EquipmentRequest;
import com.labequip.equipment.dto.EquipmentResponse;
import com.labequip.equipment.security.RoleGuard;
import com.labequip.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Read endpoints are available to any authenticated user (USER or ADMIN).
 * Write/delete endpoints (POST/PUT/DELETE) are restricted to ADMIN, since only lab
 * administrators manage the equipment catalog.
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final RoleGuard roleGuard;

    public EquipmentController(EquipmentService equipmentService, RoleGuard roleGuard) {
        this.equipmentService = equipmentService;
        this.roleGuard = roleGuard;
    }

    @GetMapping
    public List<EquipmentResponse> findAll(@RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        return equipmentService.findAll().stream().map(EquipmentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public EquipmentResponse findById(@PathVariable Long id,
                                       @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        return EquipmentResponse.from(equipmentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<EquipmentResponse> create(@Valid @RequestBody EquipmentRequest request,
                                                     @RequestHeader(value = "X-Auth-User", required = false) String authUser,
                                                     @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
        roleGuard.requireRole(authUser, roles, "ADMIN");
        Equipment created = equipmentService.create(request);
        return ResponseEntity.created(URI.create("/api/equipment/" + created.getId()))
                .body(EquipmentResponse.from(created));
    }

    @PutMapping("/{id}")
    public EquipmentResponse update(@PathVariable Long id,
                                     @Valid @RequestBody EquipmentRequest request,
                                     @RequestHeader(value = "X-Auth-User", required = false) String authUser,
                                     @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
        roleGuard.requireRole(authUser, roles, "ADMIN");
        return EquipmentResponse.from(equipmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                        @RequestHeader(value = "X-Auth-User", required = false) String authUser,
                        @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
        roleGuard.requireRole(authUser, roles, "ADMIN");
        equipmentService.delete(id);
    }
}
