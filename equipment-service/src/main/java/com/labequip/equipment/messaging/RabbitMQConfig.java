package com.labequip.equipment.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${labequip.messaging.exchange}")
    private String exchangeName;

    @Value("${labequip.messaging.maintenance-requested-routing-key}")
    private String routingKey;

    @Value("${labequip.messaging.maintenance-requested-queue}")
    private String queueName;

    @Bean
    public TopicExchange labEquipmentExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue maintenanceRequestedQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding maintenanceRequestedBinding(Queue maintenanceRequestedQueue, TopicExchange labEquipmentExchange) {
        return BindingBuilder.bind(maintenanceRequestedQueue).to(labEquipmentExchange).with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Producer (booking-service) and consumer (equipment-service) each declare their own copy
        // of com.labequip.events.MaintenanceRequestedEvent with the same package+class name (not a
        // shared library), so the default class mapper resolves __TypeId__ identically on both sides.
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("com.labequip.events");

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }
}
