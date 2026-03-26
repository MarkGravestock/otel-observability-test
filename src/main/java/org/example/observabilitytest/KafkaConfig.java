package org.example.observabilitytest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;

import java.time.Duration;

@Configuration
@Profile("salutation")
public class KafkaConfig {

    @Bean
    public ConcurrentMessageListenerContainer<String, String> replyContainer(
            ConsumerFactory<String, String> consumerFactory) {
        var containerProps = new ContainerProperties("time-reply");
        containerProps.setGroupId("salutation-reply-group");
        return new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory,
            ConcurrentMessageListenerContainer<String, String> replyContainer) {
        var template = new ReplyingKafkaTemplate<>(producerFactory, replyContainer);
        template.setDefaultReplyTimeout(Duration.ofSeconds(5));
        return template;
    }
}
