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

1. `SalutationController` calls `replyingKafkaTemplate.sendAndReceive(request)` — sends to `time-request` topic with a correlation ID and `REPLY_TOPIC` header set to `time-reply`
2. `TimeProviderService` listens on `time-request` via `@KafkaListener`, returns the current hour string via `@SendTo("time-reply")`
3. `SalutationController` blocks up to **5 seconds** for the reply; if timeout expires, throws an exception (HTTP 500 — no fallback by design, dependency must be visible)

### Distributed Tracing

The OTel Java agent auto-instruments Spring Kafka. No manual `@Span` annotations required. Trace context propagates via Kafka message headers (`traceparent`/`tracestate`). In Grafana Tempo, a single trace will show:

```
HTTP GET /salutation
  └─ kafka.produce → time-request
       └─ kafka.consume ← time-request (time-provider)
            └─ kafka.produce → time-reply
                 └─ kafka.consume ← time-reply (salutation)
```

### Broker Metrics

The OTel Collector's `kafkametricsreceiver` (already available in `otel/opentelemetry-collector-contrib`) scrapes the Kafka broker for topic-level and consumer group metrics. These flow into the existing metrics pipeline and are queryable in Grafana via Prometheus.

---

## Changes

### New Files

| File | Purpose |
|------|---------|
| `TimeProviderService.java` | `@Profile("time-provider")` Kafka listener; returns current hour as String |
| `KafkaConfig.java` | `@Profile("salutation")` — wires `ReplyingKafkaTemplate` and reply `ConcurrentMessageListenerContainer` |
| `application-time-provider.properties` | Service name `observability-time-provider`, port 8083, Kafka bootstrap |

### Modified Files

| File | Change |
|------|--------|
| `SalutationController.java` | Replace `LocalTime.now()` with `ReplyingKafkaTemplate` call; inject `KafkaConfig`-provided template |
| `application-salutation.properties` | Add `spring.kafka.bootstrap-servers` |
| `build.gradle.kts` | Add `spring-kafka` dependency; add `runTimeProvider` Gradle task |
| `libs.versions.toml` | Add `spring-kafka` library alias |
| `docker-compose.yaml` | Add `kafka` service (Apache Kafka 4.0.0, KRaft mode, port 9092) |
| `otel-collector-config.yaml` | Add `kafkametricsreceiver`; wire into metrics pipeline |

---

## Infrastructure

### Kafka (docker-compose)

- Image: `apache/kafka:4.0.0` (KRaft mode — no Zookeeper)
- Internal port: `9092` (app connections from containers)
- Host-mapped port: `9092` (app connections from host/Gradle run tasks)
- `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` — topics `time-request` and `time-reply` created on first use

### OTel Collector

`kafkametricsreceiver` added alongside the existing `mysqlreceiver`:

```yaml
kafkametrics:
  brokers: [kafka:9092]
  protocol_version: 3.0.0
  scrapers: [brokers, topics, consumers]
  collection_interval: 10s
```

Wired into the existing `metrics` pipeline alongside `mysql`.

---

## Gradle Tasks

New task `runTimeProvider` added following the same pattern as `runGreeting`, `runSalutation`, `runVisitor`:

```
./gradlew runTimeProvider   # port 8083, profile=time-provider
```

---

## Failure Modes

| Scenario | Behaviour |
|----------|-----------|
| `time-provider` down | Salutation waits 5 s, throws `KafkaException`, HTTP 500 returned |
| Kafka broker down | `ReplyingKafkaTemplate` fails immediately on send, HTTP 500 returned |
| `time-reply` topic backlog | Correlation ID ensures correct reply is matched; stale replies are discarded |

---

## Out of Scope

- Authentication/TLS on Kafka (not needed for local demo)
- Multiple partitions or consumer groups
- Schema registry
- Manual `@Span` annotations (auto-instrumentation only, per project philosophy)
