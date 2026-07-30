package com.labequip.equipment.service;

import com.labequip.equipment.domain.Equipment;
import com.labequip.equipment.dto.EquipmentRequest;
import com.labequip.equipment.repository.EquipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    public EquipmentService(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    public List<Equipment> findAll() {
        return equipmentRepository.findAll();
    }

    public Equipment findById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment " + id + " not found"));
    }

    public Equipment create(EquipmentRequest request) {
        Equipment equipment = new Equipment();
        apply(equipment, request);
        return equipmentRepository.save(equipment);
    }

    public Equipment update(Long id, EquipmentRequest request) {
        Equipment equipment = findById(id);
        apply(equipment, request);
        return equipmentRepository.save(equipment);
    }

    public void delete(Long id) {
        if (!equipmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment " + id + " not found");
        }
        equipmentRepository.deleteById(id);
    }

    private void apply(Equipment equipment, EquipmentRequest request) {
        equipment.setName(request.name());
        equipment.setCategory(request.category());
        equipment.setLocation(request.location());
        equipment.setStatus(request.status());
        equipment.setConditionNotes(request.conditionNotes());
    }
}
