package com.labequip.equipment.web;

import com.labequip.equipment.dto.MaintenanceRecordResponse;
import com.labequip.equipment.dto.MaintenanceStatusUpdateRequest;
import com.labequip.equipment.security.RoleGuard;
import com.labequip.equipment.service.MaintenanceRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance-records")
public class MaintenanceRecordController {

    private final MaintenanceRecordService maintenanceRecordService;
    private final RoleGuard roleGuard;

    public MaintenanceRecordController(MaintenanceRecordService maintenanceRecordService, RoleGuard roleGuard) {
        this.maintenanceRecordService = maintenanceRecordService;
        this.roleGuard = roleGuard;
    }

    @GetMapping
    public List<MaintenanceRecordResponse> findAll(@RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        return maintenanceRecordService.findAll().stream().map(MaintenanceRecordResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MaintenanceRecordResponse findById(@PathVariable Long id,
                                               @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        roleGuard.requireAuthenticated(authUser);
        return MaintenanceRecordResponse.from(maintenanceRecordService.findById(id));
    }

    @PutMapping("/{id}/status")
    public MaintenanceRecordResponse updateStatus(@PathVariable Long id,
                                                   @Valid @RequestBody MaintenanceStatusUpdateRequest request,
                                                   @RequestHeader(value = "X-Auth-User", required = false) String authUser,
                                                   @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
        roleGuard.requireRole(authUser, roles, "ADMIN");
        return MaintenanceRecordResponse.from(maintenanceRecordService.updateStatus(id, request));
    }
}
