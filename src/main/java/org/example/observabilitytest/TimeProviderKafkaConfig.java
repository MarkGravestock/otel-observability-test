package org.example.observabilitytest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@Profile("time-provider")
public class TimeProviderKafkaConfig {

    @Autowired
    public void configureReplyTemplate(
            ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory,
            @Qualifier("kafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        kafkaListenerContainerFactory.setReplyTemplate(kafkaTemplate);
    }
}
