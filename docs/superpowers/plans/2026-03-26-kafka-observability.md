# Kafka Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `LocalTime.now()` in `SalutationController` with a Kafka request-reply to a new `time-provider` service, with full distributed tracing and broker metrics in Grafana LGTM.

**Architecture:** `ReplyingKafkaTemplate` on the salutation side sends to `time-request` topic and blocks (`.get(5, TimeUnit.SECONDS)`) on a `time-reply` response. `TimeProviderService` (profile: `time-provider`) consumes requests and replies with the current hour via `@SendTo`. `TimeProviderKafkaConfig` explicitly sets the reply template on the listener container factory so `@SendTo` has a transport. OTel Java agent auto-instruments all Kafka spans. `kafkametricsreceiver` scrapes broker metrics into the existing LGTM pipeline.

**Tech Stack:** Spring Boot 4.0.4, Spring Kafka 4.0.x (BOM-managed), `apache/kafka:4.0.0` (KRaft), OTel Collector contrib `kafkametricsreceiver`, `@EmbeddedKafka` for tests

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `docker-compose.yaml` | Modify | Add `kafka` service (KRaft, dual listeners, persistent volume) |
| `gradle/libs.versions.toml` | Modify | Add `spring-kafka`, `spring-kafka-test`, `h2` aliases |
| `build.gradle.kts` | Modify | Add deps; add `runTimeProvider` to task loop |
| `src/main/resources/application-time-provider.properties` | Create | Port 8083, service name, Kafka bootstrap |
| `src/main/resources/application-salutation.properties` | Modify | Add Kafka bootstrap servers |
| `src/main/java/.../TimeProviderService.java` | Create | `@Profile("time-provider")` — `@KafkaListener` + `@SendTo` |
| `src/main/java/.../TimeProviderKafkaConfig.java` | Create | `@Profile("time-provider")` — sets `replyTemplate` on container factory for `@SendTo` |
| `src/main/java/.../KafkaConfig.java` | Create | `@Profile("salutation")` — `ReplyingKafkaTemplate` + reply container |
| `src/main/java/.../SalutationController.java` | Modify | Replace `LocalTime.now()` (and unused `ObservationRegistry`) with `ReplyingKafkaTemplate` |
| `src/test/java/.../KafkaTimeRequestIntegrationTest.java` | Create | `@EmbeddedKafka` integration test for full request-reply flow |
| `config/otel-collector-config.yaml` | Modify | Add `kafkametricsreceiver`; wire into metrics pipeline |
| `CLAUDE.md` | Modify | Add `runTimeProvider` to commands section, update topology |
| `docs/diagrams/observability-system-container.puml` | Modify | Add time-provider, Kafka, broker metrics relations |
| `docs/open-telemetry.md` | Modify | Update demo section for Kafka observability |

---

## Task 1: Add Kafka to docker-compose and build

**Files:**
- Modify: `docker-compose.yaml`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `src/main/resources/application-time-provider.properties`
- Modify: `src/main/resources/application-salutation.properties`

- [ ] **Step 1.1: Replace docker-compose.yaml**

```yaml
services:
  otel-lgtm:
    image: grafana/otel-lgtm:0.22.0
    ports:
      - "3000:3000"  # Grafana UI
      - "4317:4317"  # OpenTelemetry gRPC
      - "4318:4318"  # OpenTelemetry HTTP

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.148.0
    environment:
      - MYSQL_PASSWORD=my-secret-password
    volumes:
      - ./config/otel-collector-config.yaml:/etc/otelcol-contrib/config.yaml
    ports:
      - 1888:1888   # pprof extension
      - 8888:8888   # Prometheus metrics exposed by the Collector
      - 8889:8889   # Prometheus exporter metrics
      - 13133:13133 # health_check extension
      - 4327:4317   # OTLP gRPC receiver
      - 4328:4318   # OTLP HTTP receiver
      - 55679:55679 # zpages extension
    depends_on:
      - kafka

  mysql:
    image: mysql:8.4.8
    container_name: my-mysql
    environment:
      MYSQL_ROOT_PASSWORD: my-secret-password
      MYSQL_DATABASE: visitors
      MYSQL_USER: myuser
      MYSQL_PASSWORD: myuserpassword
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  kafka:
    image: apache/kafka:4.0.0
    container_name: kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:29093'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_LISTENERS: 'CONTROLLER://:29093,PLAINTEXT://:19092,PLAINTEXT_HOST://:9092'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka:19092,PLAINTEXT_HOST://localhost:9092'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_LOG_DIRS: '/var/lib/kafka/data'
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qg'
    ports:
      - "9092:9092"
    volumes:
      - kafka-data:/var/lib/kafka/data

volumes:
  mysql-data:
  kafka-data:
```

- [ ] **Step 1.2: Add library aliases to libs.versions.toml**

Add to the `[libraries]` section:

```toml
spring-kafka      = { module = "org.springframework.kafka:spring-kafka" }
spring-kafka-test = { module = "org.springframework.kafka:spring-kafka-test" }
h2                = { module = "com.h2database:h2" }
```

- [ ] **Step 1.3: Update build.gradle.kts**

In the `dependencies` block, add after `implementation(libs.otel.logback.appender)`:

```kotlin
implementation(libs.spring.kafka)
testImplementation(libs.spring.kafka.test)
testRuntimeOnly(libs.h2)
```

In the `listOf(...)` loop, add a fourth entry so the full list is:

```kotlin
listOf(
    Triple("Greeting",      "greeting",       "observability-greeting"),
    Triple("Salutation",    "salutation",     "observability-salutation"),
    Triple("Visitor",       "visitor",        "observability-visitor"),
    Triple("TimeProvider",  "time-provider",  "observability-time-provider")
)
```

- [ ] **Step 1.4: Create application-time-provider.properties**

```properties
spring.application.name=observability-time-provider
server.port=8083
spring.kafka.bootstrap-servers=localhost:9092
spring.docker.compose.enabled=false
```

- [ ] **Step 1.5: Update application-salutation.properties**

```properties
spring.application.name=observability-salutation
server.port=8081
spring.kafka.bootstrap-servers=localhost:9092
spring.docker.compose.enabled=false
```

- [ ] **Step 1.6: Verify build compiles**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.7: Commit**

```bash
git add docker-compose.yaml gradle/libs.versions.toml build.gradle.kts \
    src/main/resources/application-time-provider.properties \
    src/main/resources/application-salutation.properties
git commit -m "feat: add Kafka infrastructure, build deps, and properties"
```

---

## Task 2: Implement TimeProviderService and KafkaConfig (TDD)

**Files:**
- Create: `src/test/java/org/example/observabilitytest/KafkaTimeRequestIntegrationTest.java`
- Create: `src/main/java/org/example/observabilitytest/TimeProviderService.java`
- Create: `src/main/java/org/example/observabilitytest/TimeProviderKafkaConfig.java`
- Create: `src/main/java/org/example/observabilitytest/KafkaConfig.java`

> The integration test covers the full flow — write it first, then implement each class until it passes.

- [ ] **Step 2.1: Write the failing integration test**

Create `src/test/java/org/example/observabilitytest/KafkaTimeRequestIntegrationTest.java`:

```java
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
```

- [ ] **Step 2.2: Run test — expect compile failure (classes missing)**

```bash
./gradlew test --tests "org.example.observabilitytest.KafkaTimeRequestIntegrationTest"
```

Expected: FAIL — `TimeProviderService`, `TimeProviderKafkaConfig`, `KafkaConfig` missing; `SalutationController` has wrong dependencies.

- [ ] **Step 2.3: Create TimeProviderService.java**

```java
package org.example.observabilitytest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
@Profile("time-provider")
@Slf4j
public class TimeProviderService {

    @KafkaListener(topics = "time-request", groupId = "time-provider-group")
    @SendTo
    public String provideTime(String request) {
        log.info("Received time request, returning current hour");
        return String.valueOf(LocalTime.now().getHour());
    }
}
```

- [ ] **Step 2.4: Create TimeProviderKafkaConfig.java**

`@SendTo` on a `@KafkaListener` method requires the listener container factory to have a `replyTemplate` set. This class explicitly wires it using the auto-configured `kafkaTemplate` bean. The `@Qualifier` is required to avoid ambiguity when both `salutation` and `time-provider` profiles are active (which adds a second `KafkaTemplate`-subtype bean from `KafkaConfig`).

```java
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
```

- [ ] **Step 2.5: Create KafkaConfig.java**

```java
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
```

> Note: `autoStartup` is left at its default (`true`) on the reply container. `ReplyingKafkaTemplate` starts it during its own `SmartLifecycle.start()` call; Spring's lifecycle machinery also calls `start()` directly since `autoStartup=true`. Starting an already-started container is idempotent.
> Note: `ConsumerFactory<String, String>` injection works via type erasure against Spring Boot's auto-configured `ConsumerFactory<Object, Object>` — this is safe and expected.

---

## Task 3: Update SalutationController

**Files:**
- Modify: `src/main/java/org/example/observabilitytest/SalutationController.java`

- [ ] **Step 3.1: Replace SalutationController.java**

> Note: The existing controller injects `ObservationRegistry` but never uses it — the field is removed here since the project philosophy is auto-instrumentation only (no manual observation calls).

```java
package org.example.observabilitytest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyMessageFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Slf4j
@Profile("salutation")
public class SalutationController {

    private final ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate;

    @GetMapping("/salutation")
    String salutation() throws Exception {
        log.info("Requesting time via Kafka");
        var record = new ProducerRecord<String, String>("time-request", "get-time");
        RequestReplyMessageFuture<String, String> future = replyingKafkaTemplate.sendAndReceive(record);
        int hourOfDay = Integer.parseInt((String) future.get(5, TimeUnit.SECONDS).getPayload());

        if (hourOfDay < 12) return "Good Morning";
        if (hourOfDay < 18) return "Good Afternoon";
        return "Good Evening";
    }
}
```

- [ ] **Step 3.2: Run the integration test — expect PASS**

```bash
./gradlew test --tests "org.example.observabilitytest.KafkaTimeRequestIntegrationTest"
```

Expected: `BUILD SUCCESSFUL` — 1 test passed.

- [ ] **Step 3.3: Full build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.4: Commit**

```bash
git add src/main/java/org/example/observabilitytest/TimeProviderService.java \
    src/main/java/org/example/observabilitytest/TimeProviderKafkaConfig.java \
    src/main/java/org/example/observabilitytest/KafkaConfig.java \
    src/main/java/org/example/observabilitytest/SalutationController.java \
    src/test/java/org/example/observabilitytest/KafkaTimeRequestIntegrationTest.java
git commit -m "feat: request time via Kafka in SalutationController (time-provider profile)"
```

---

## Task 4: Add Kafka broker metrics to OTel Collector

**Files:**
- Modify: `config/otel-collector-config.yaml`

- [ ] **Step 4.1: Replace otel-collector-config.yaml**

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
  mysql:
    endpoint: mysql:3306
    password: ${env:MYSQL_PASSWORD}
    collection_interval: 10s
    initial_delay: 1s
    statement_events:
      digest_text_limit: 120
      time_limit: 24h
      limit: 250
  kafkametrics:
    brokers: [kafka:19092]
    protocol_version: 3.0.0
    scrapers: [brokers, topics, consumers]
    collection_interval: 10s

processors:
  resourcedetection:
    detectors: [ env, system ]
  cumulativetodelta:
  batch:
  resource:
    attributes:
      - key: deployment.environment
        value: "local"
        action: upsert

exporters:
  otlp:
    endpoint: otel-lgtm:4317
    tls:
      insecure: true
  debug:
    verbosity: basic

extensions:
  health_check:
  pprof:
  zpages:
    endpoint: 0.0.0.0:55679

service:
  telemetry:
    logs:
      level: "info"
  extensions: [health_check, pprof, zpages]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [otlp, debug]
    metrics:
      receivers: [otlp, mysql, kafkametrics]
      processors: [cumulativetodelta, batch, resource]
      exporters: [otlp, debug]
    logs:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [otlp, debug]
```

> Note: `kafkametricsreceiver` officially supports Kafka 2.x/3.x. Kafka 4.0 is expected to work via `franz-go` ApiVersions negotiation (`receiver.kafkametricsreceiver.UseFranzGo` feature gate, enabled by default in collector 0.148.0). If `kafka_` metrics don't appear in Grafana, check the collector container logs for connection errors to `kafka:19092`.

- [ ] **Step 4.2: Commit**

```bash
git add config/otel-collector-config.yaml
git commit -m "feat: add kafkametricsreceiver to OTel Collector for Kafka broker metrics"
```

---

## Task 5: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 5.1: Update CLAUDE.md**

In the `## Commands` section, update the service run tasks to:

```bash
./gradlew runGreeting     # port 8080
./gradlew runSalutation   # port 8081
./gradlew runVisitor      # port 8082
./gradlew runTimeProvider # port 8083
```

Update the **Service interaction** diagram in the Architecture section to:

```
User → Greeting Service (8080)
           ├→ Salutation Service (8081)   [requests time via Kafka, returns greeting text]
           │       └→ Kafka time-request → Time-Provider Service (8083)
           └→ Visitor Service (8082)      [visitor count, persisted in MySQL]
```

Update the **Trigger a distributed trace** comment to:

```bash
# Trigger a distributed trace (requires all 4 services + docker-compose up -d)
curl "http://localhost:8080/greeting?name=World"
```

- [ ] **Step 5.2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with time-provider service and Kafka topology"
```

---

## Task 6: Update architecture documentation

**Files:**
- Modify: `docs/diagrams/observability-system-container.puml`
- Modify: `docs/open-telemetry.md`

- [ ] **Step 6.1: Replace observability-system-container.puml**

```plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml
LAYOUT_WITH_LEGEND()

title Container diagram for Observability Demo System

Person(user, User, "A user of the observability system")

System_Boundary(c1, "Observability Demo") {
    Container(observability_greeting, "Greeting Service", "Java, Spring Boot", "Orchestrates greeting: calls Salutation and Visitor over HTTP")
    Container(observability_salutation, "Salutation Service", "Java, Spring Boot", "Returns time-of-day greeting; requests current time from Time-Provider via Kafka")
    Container(observability_visitor, "Visitor Service", "Java, Spring Boot", "Tracks and persists visitor count in MySQL")
    Container(observability_time_provider, "Time-Provider Service", "Java, Spring Boot", "Listens on Kafka for time requests; replies with current hour")
    Container(kafka, "Apache Kafka", "Docker, KRaft mode", "Message broker for time request/reply; broker metrics scraped by OTel Collector")
    Container(otel_collection, "OpenTelemetry Collector", "Docker Container", "Receives OTLP telemetry; scrapes MySQL and Kafka broker metrics; forwards to LGTM")
    Container(mysql, "MySQL Database", "Docker Container", "Stores visitor counts")
    Container(observability_backend, "Grafana LGTM", "Docker Container", "Stores and visualises logs (Loki), traces (Tempo), and metrics (Prometheus/Mimir)")
}

System_Ext(datadog, "DataDog", "External observability backend")
System_Ext(honeycomb, "Honeycomb", "External observability backend")

Rel(user, observability_greeting, "Uses", "HTTP:8080")
Rel(observability_greeting, observability_salutation, "Uses", "HTTP:8081")
Rel(observability_greeting, observability_visitor, "Uses", "HTTP:8082")

Rel(observability_salutation, kafka, "Publishes/Subscribes", "time-request / time-reply")
Rel(observability_time_provider, kafka, "Publishes/Subscribes", "time-request / time-reply")

Rel(observability_greeting, otel_collection, "Sends telemetry", "OTLP")
Rel(observability_salutation, otel_collection, "Sends telemetry", "OTLP")
Rel(observability_visitor, otel_collection, "Sends telemetry", "OTLP")
Rel(observability_time_provider, otel_collection, "Sends telemetry", "OTLP")

Rel(otel_collection, mysql, "Scrapes metrics", "JDBC")
Rel(otel_collection, kafka, "Scrapes broker metrics", "Kafka API:19092")
Rel(observability_visitor, mysql, "Persists data", "JDBC")

Rel(otel_collection, observability_backend, "Exports telemetry", "OTLP")
Rel(otel_collection, datadog, "Exports telemetry", "OTLP")
Rel(otel_collection, honeycomb, "Exports telemetry", "OTLP")

@enduml
```

- [ ] **Step 6.2: Update open-telemetry.md demo section**

Replace everything from `#### Demo` to end of file with:

```markdown
#### Demo

The demo uses three services communicating over HTTP, plus a fourth communicating over Kafka — all auto-instrumented with the OpenTelemetry Java agent.

**Service topology:**

- **Greeting** (port 8080) — orchestrates a greeting by calling Salutation and Visitor over HTTP
- **Salutation** (port 8081) — determines time-of-day greeting by requesting the current time from Time-Provider **over Kafka** (request-reply)
- **Visitor** (port 8082) — counts and persists visitor numbers in MySQL
- **Time-Provider** (port 8083) — listens on Kafka `time-request` topic, replies with current hour on `time-reply`

**What gets observed:**

- **Distributed traces** — a single `curl` to Greeting produces a trace spanning HTTP → Kafka produce → Kafka consume (Time-Provider) → Kafka produce (reply) → Kafka consume (Salutation). Visible in Grafana Tempo.
- **Kafka broker metrics** — the OTel Collector scrapes the Kafka broker via `kafkametricsreceiver` (topics, consumer groups, broker stats). Visible in Grafana via Prometheus.
- **MySQL metrics** — scraped by the OTel Collector's `mysqlreceiver`.
- **Logs** — correlated with trace IDs via the OpenTelemetry Logback appender.

No manual `@Span` annotations or OTel SDK calls — everything is instrumented automatically by `opentelemetry-javaagent.jar`.

![demo_c4_container_diagram.png](images/demo_c4_container_diagram.png)
```

- [ ] **Step 6.3: Commit**

```bash
git add docs/diagrams/observability-system-container.puml docs/open-telemetry.md
git commit -m "docs: update architecture diagram and OTel doc to include Kafka / time-provider"
```

---

## Verification Checklist

After all tasks complete, verify end-to-end manually:

- [ ] `docker-compose up -d` — all 4 services start cleanly (otel-lgtm, otel-collector, mysql, kafka)
- [ ] `./gradlew runTimeProvider` — starts on port 8083 with no errors
- [ ] `./gradlew runSalutation` — starts on port 8081 with no errors
- [ ] `./gradlew runGreeting` and `./gradlew runVisitor` — start unchanged
- [ ] `curl "http://localhost:8080/greeting?name=World"` — returns `Good Morning/Afternoon/Evening, World. You are our N visitor`
- [ ] Grafana Tempo (http://localhost:3000) — trace shows Kafka producer/consumer spans in the waterfall
- [ ] Grafana Explore → Prometheus — `kafka_` metrics visible (allow up to 30 s for first scrape)
