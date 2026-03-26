import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    alias(libs.plugins.spring.boot)
}

group = "org.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // Native Gradle BOM support. Non-inheriting configurations each declare
    // the platform explicitly so version constraints are applied uniformly.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.otel.bom))
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.spring.boot.docker.compose)
    implementation(libs.otel.logback.appender)
    runtimeOnly(libs.mysql.connector.j)
    developmentOnly(libs.spring.boot.devtools)
    testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── Service run tasks ────────────────────────────────────────────────────────
// Each task starts a service with the OpenTelemetry Java agent attached and
// telemetry pointed at the local collector (docker-compose must be running).
// Usage: ./gradlew runGreeting  (in a separate terminal for each service)

val otelJvmArgs = listOf(
    "-javaagent:${rootDir}/libs/opentelemetry-javaagent.jar",
    "-Dotel.exporter.otlp.endpoint=http://localhost:4328",
    "-Dotel.exporter.otlp.protocol=http/protobuf",
    "-Dotel.traces.exporter=otlp",
    "-Dotel.metrics.exporter=otlp",
    "-Dotel.logs.exporter=otlp"
)

listOf(
    Triple("Greeting",   "greeting",   "observability-greeting"),
    Triple("Salutation", "salutation", "observability-salutation"),
    Triple("Visitor",    "visitor",    "observability-visitor")
).forEach { (name, profile, serviceName) ->
    tasks.register<BootRun>("run$name") {
        group = "application"
        description = "Run the $name service with OpenTelemetry auto-instrumentation"
        dependsOn(tasks.classes)
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("org.example.observabilitytest.ObservabilityTestApplication")
        args("--spring.profiles.active=$profile")
        jvmArgs(otelJvmArgs + "-Dotel.service.name=$serviceName")
    }
}