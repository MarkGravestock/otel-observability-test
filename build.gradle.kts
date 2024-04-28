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

/* dependencyManagement {
    imports {
        mavenBom("io.opentelemetry:opentelemetry-bom:1.37.0")
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:2.3.0-alpha")
    }
} */

val otelVersion = "2.3.0-alpha"
val mySqlConnectorVersion = "8.0.28"

dependencies {
    compileOnly("org.projectlombok:lombok")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Required for @Timed aspect
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:${otelVersion}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:${otelVersion}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-web-3.1:${otelVersion}")
    runtimeOnly("mysql:mysql-connector-java:${mySqlConnectorVersion}")
    runtimeOnly("io.micrometer:micrometer-registry-otlp")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
