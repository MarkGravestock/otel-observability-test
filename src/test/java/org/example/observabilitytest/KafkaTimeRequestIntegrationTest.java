package org.example.observabilitytest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.docker.compose.enabled=false"
})
@ActiveProfiles({"salutation", "time-provider"})
@EmbeddedKafka(partitions = 1, topics = {"time-request", "time-reply"})
class KafkaTimeRequestIntegrationTest {

    @Autowired
    SalutationController salutationController;

    @Test
    void salutation_requestsTimeViaKafka_returnsGreeting() throws Exception {
        String result = salutationController.salutation();
        assertThat(result).isIn("Good Morning", "Good Afternoon", "Good Evening");
    }
}
