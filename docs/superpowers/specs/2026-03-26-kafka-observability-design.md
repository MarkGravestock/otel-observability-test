# Kafka Observability Design

**Date:** 2026-03-26
**Branch:** auto-instrumentation
**Status:** Approved

## Goal

Demonstrate Kafka observability in the existing Spring Boot OTel demo. The `SalutationController` will request the current time over Kafka from a new 4th service (`time-provider`) instead of reading `LocalTime.now()` locally. Distributed tracing must propagate across the Kafka boundary. Kafka broker metrics must appear in the Grafana LGTM stack.

---

## Service Topology

```
User → Greeting (8080)
           ├→ Salutation (8081)  [HTTP, unchanged]
           │       └→ Kafka: time-request  →  Time-Provider (8083)
           │                                        ↓
           │       ←── Kafka: time-reply ───────────┘
           └→ Visitor (8082)    [HTTP, unchanged]
```

Four services total. Greeting and Visitor are unchanged. Salutation gains a Kafka dependency. Time-Provider is new.

---

## Architecture

### Request-Reply Pattern

Spring Kafka's `ReplyingKafkaTemplate` handles the synchronous request-reply over Kafka:

1. `SalutationController` calls `replyingKafkaTemplate.sendAndReceive(request).get(5, TimeUnit.SECONDS)` — sends to `time-request` topic with a correlation ID and `REPLY_TOPIC` header set to `time-reply`, then blocks on the returned `RequestReplyMessageFuture`
2. `TimeProviderService` listens on `time-request` via `@KafkaListener`, returns the current hour string via `@SendTo("time-reply")`
3. If `.get()` times out or `defaultReplyTimeout` (also 5 s) elapses, an exception is thrown (HTTP 500 — no fallback by design, dependency must be visible)

**`KafkaConfig` wiring requirement:** `ReplyingKafkaTemplate` requires a reply `ConcurrentMessageListenerContainer` to be explicitly constructed and passed into its constructor — the container must be subscribed to the `time-reply` topic and configured with a matching `groupId`. Both beans must be declared in the same `@Configuration` class (annotated `@Profile("salutation")`). `ReplyingKafkaTemplate` implements `SmartLifecycle`, so Spring manages start/stop automatically once both beans are registered.

### Distributed Tracing

The OTel Java agent auto-instruments Spring Kafka (via `opentelemetry-spring-kafka-2.7` instrumentation, muzzle range `[2.7.0,)`, covering spring-kafka 4.0.4). No manual `@Span` annotations required. Trace context propagates via Kafka message headers (`traceparent`/`tracestate`). In Grafana Tempo, a single trace will show:

```
HTTP GET /salutation
  └─ kafka.produce → time-request
       └─ kafka.consume ← time-request (time-provider)
            └─ kafka.produce → time-reply
                 └─ kafka.consume ← time-reply (salutation)
```

### Broker Metrics

The OTel Collector's `kafkametricsreceiver` (config key: `kafkametrics`) scrapes the Kafka broker for topic-level and consumer group metrics. These flow into the existing metrics pipeline and are queryable in Grafana via Prometheus.

**Kafka 4.x compatibility note:** The receiver's documented support matrix lists Kafka 2.x and 3.x. Kafka 4.0 is not yet listed. The `franz-go` client path (enabled via `receiver.kafkametricsreceiver.UseFranzGo` feature gate, which is default in newer collector builds) negotiates protocol versions dynamically via ApiVersions and is expected to work with Kafka 4.0. `protocol_version: 3.0.0` is used as the wire protocol ceiling in the config. If the receiver fails to scrape, check feature gate status in `otel/opentelemetry-collector-contrib:0.148.0`.

---

## Changes

### New Files

| File | Purpose |
|------|---------|
| `TimeProviderService.java` | `@Profile("time-provider")` Kafka listener; returns current hour as String |
| `KafkaConfig.java` | `@Profile("salutation")` — wires `ReplyingKafkaTemplate` and reply `ConcurrentMessageListenerContainer` |
| `application-time-provider.properties` | Service name `observability-time-provider`, port 8083, `spring.kafka.bootstrap-servers=localhost:9092` |

### Modified Files

| File | Change |
|------|--------|
| `SalutationController.java` | Replace `LocalTime.now()` with `ReplyingKafkaTemplate` call |
| `application-salutation.properties` | Add `spring.kafka.bootstrap-servers=localhost:9092` |
| `build.gradle.kts` | Add `spring-kafka` dependency; add `runTimeProvider` Gradle task |
| `libs.versions.toml` | Add `spring-kafka` library alias (no `version.ref` — managed by Spring Boot BOM) |
| `docker-compose.yaml` | Add `kafka` service (Apache Kafka 4.0.0, KRaft mode, dual advertised listeners) |
| `otel-collector-config.yaml` | Add `kafkametricsreceiver`; wire into metrics pipeline |

---

## Infrastructure

### Kafka (docker-compose)

Image: `apache/kafka:4.0.0` (KRaft mode — no Zookeeper).

Kafka requires **two advertised listeners** because two different networks connect to the broker:
- **Host network** (Gradle run tasks for `salutation` and `time-provider`): connects via `localhost:9092`
- **Docker network** (OTel Collector scraping broker metrics): connects via `kafka:19092`

Required environment variables in the `kafka` service:

```yaml
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT_HOST://localhost:9092,PLAINTEXT://kafka:19092'
KAFKA_LISTENERS: 'CONTROLLER://:29093,PLAINTEXT_HOST://:9092,PLAINTEXT://:19092'
KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
```

Port mapping in `docker-compose.yaml`: `9092:9092` only (host-facing listener). Port 19092 is internal to the Docker network only.

**Bootstrap servers by consumer:**
- `application-salutation.properties` and `application-time-provider.properties`: `localhost:9092`
- `otel-collector-config.yaml` (`kafkametrics` receiver): `kafka:19092`

### OTel Collector

`kafkametricsreceiver` added alongside the existing `mysqlreceiver`:

```yaml
kafkametrics:
  brokers: [kafka:19092]
  protocol_version: 3.0.0
  scrapers: [brokers, topics, consumers]
  collection_interval: 10s
```

Wired into the existing `metrics` pipeline alongside `mysql`.

---

## Gradle Tasks

`runTimeProvider` added to the existing `listOf(Triple(...))` loop in `build.gradle.kts` alongside the three existing entries:

```kotlin
Triple("TimeProvider", "time-provider", "observability-time-provider")
```

This registers a `BootRun` task that starts the app with `--spring.profiles.active=time-provider`, attaches the OTel Java agent, and sets `OTEL_SERVICE_NAME=observability-time-provider`. Port 8083 is set via `application-time-provider.properties`.

```
./gradlew runTimeProvider   # port 8083
```

---

## Version Catalog

`spring-kafka` entry in `libs.versions.toml` — no `version.ref` needed, managed by the Spring Boot BOM (same convention as all other Spring Boot starters in the catalog):

```toml
spring-kafka = { module = "org.springframework.kafka:spring-kafka" }
```

---

## Failure Modes

| Scenario | Behaviour |
|----------|-----------|
| `time-provider` down | Salutation waits 5 s (`.get(5, TimeUnit.SECONDS)`), throws, HTTP 500 returned |
| Kafka broker down | `ReplyingKafkaTemplate` fails immediately on send, HTTP 500 returned |
| `time-reply` topic backlog | Correlation ID ensures correct reply is matched; stale replies are discarded |

---

## Out of Scope

- Authentication/TLS on Kafka (not needed for local demo)
- Multiple partitions or consumer groups (topics created with 1 partition via auto-creation; `time-reply` is consumed by a single template instance so partition fan-out is not a risk in this setup)
- Schema registry
- Manual `@Span` annotations (auto-instrumentation only, per project philosophy)
