package com.labequip.booking.messaging;

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

    @Bean
    public TopicExchange labEquipmentExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // See equipment-service's RabbitMQConfig: both sides declare com.labequip.events.MaintenanceRequestedEvent
        // with the same package+class name (not a shared library), so the default class mapper's
        // FQN-based __TypeId__ resolves identically on both sides.
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("com.labequip.events");

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }
}
