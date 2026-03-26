package org.example.observabilitytest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@Profile("time-provider")
public class TimeProviderKafkaConfig {

    // Spring Boot 4 splits Kafka auto-config into spring-boot-kafka module; the auto-configured
    // KafkaTemplate bean requires raw type injection here due to Java 25 generics resolution.
    @Autowired
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void configureReplyTemplate(
            ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory,
            KafkaTemplate kafkaTemplate) {
        kafkaListenerContainerFactory.setReplyTemplate(kafkaTemplate);
    }
}
