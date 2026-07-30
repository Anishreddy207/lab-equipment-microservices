package com.labequip.equipment.messaging;

import com.labequip.equipment.service.MaintenanceRecordService;
import com.labequip.events.MaintenanceRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRequestedListener.class);

    private final MaintenanceRecordService maintenanceRecordService;

    public MaintenanceRequestedListener(MaintenanceRecordService maintenanceRecordService) {
        this.maintenanceRecordService = maintenanceRecordService;
    }

    @RabbitListener(queues = "${labequip.messaging.maintenance-requested-queue}")
    public void onMaintenanceRequested(MaintenanceRequestedEvent event) {
        log.info("Received MaintenanceRequested event for equipmentId={}", event.equipmentId());
        maintenanceRecordService.handleMaintenanceRequested(event);
    }
}
