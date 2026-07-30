package com.labequip.booking.messaging;

import com.labequip.events.MaintenanceRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MaintenanceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public MaintenanceEventPublisher(RabbitTemplate rabbitTemplate,
                                      @Value("${labequip.messaging.exchange}") String exchange,
                                      @Value("${labequip.messaging.maintenance-requested-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publishMaintenanceRequested(Long equipmentId, Long bookingId, String issueDescription, String reportedBy) {
        MaintenanceRequestedEvent event = new MaintenanceRequestedEvent(
                equipmentId, bookingId, issueDescription, reportedBy, Instant.now());
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
        log.info("Published MaintenanceRequested event: equipmentId={} bookingId={}", equipmentId, bookingId);
    }
}
