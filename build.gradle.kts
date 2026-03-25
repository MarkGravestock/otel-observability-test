import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    id("org.springframework.boot") version "3.2.4"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val otelVersion = "2.3.0-alpha"

dependencyManagement {
    imports {
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:${otelVersion}")
    }
}

val mySqlConnectorVersion = "8.0.28"

dependencies {
    compileOnly("org.projectlombok:lombok")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.springframework.boot:spring-boot-docker-compose")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")
    runtimeOnly("mysql:mysql-connector-java:${mySqlConnectorVersion}")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
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