plugins {
    java
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.samharrison"
version = "0.0.1-SNAPSHOT"
description = "Educational event-driven payments and reconciliation platform"

val springModulithVersion = "2.0.7"
val springdocVersion = "3.0.3"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(
            "org.springframework.modulith:" +
                "spring-modulith-bom:$springModulithVersion"
        )
    }
}

dependencies {
    implementation(
        "org.springframework.boot:spring-boot-starter-actuator"
    )
    implementation(
        "org.springframework.boot:spring-boot-starter-data-jpa"
    )
    implementation(
        "org.springframework.boot:spring-boot-starter-validation"
    )
    implementation(
        "org.springframework.security:spring-security-crypto"
    )
    implementation(
        "org.springframework.boot:spring-boot-starter-webmvc"
    )

    implementation(
        "org.springframework.boot:spring-boot-starter-flyway"
    )
    runtimeOnly(
        "org.flywaydb:flyway-database-postgresql"
    )

    implementation(
        "org.springframework.modulith:spring-modulith-starter-core"
    )

    implementation(
        "org.springdoc:" +
            "springdoc-openapi-starter-webmvc-ui:$springdocVersion"
    )

    runtimeOnly("org.postgresql:postgresql")

    testImplementation(
        "org.springframework.boot:spring-boot-starter-actuator-test"
    )
    testImplementation(
        "org.springframework.boot:spring-boot-starter-data-jpa-test"
    )
    testImplementation(
        "org.springframework.boot:spring-boot-starter-validation-test"
    )
    testImplementation(
        "org.springframework.boot:spring-boot-starter-webmvc-test"
    )
    testImplementation(
        "org.springframework.boot:spring-boot-testcontainers"
    )

    testImplementation(
        "org.springframework.modulith:spring-modulith-starter-test"
    )

    testImplementation(
        "org.testcontainers:testcontainers-junit-jupiter"
    )
    testImplementation(
        "org.testcontainers:testcontainers-postgresql"
    )

    testRuntimeOnly(
        "org.junit.platform:junit-platform-launcher"
    )
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Xlint:all"
        )
    )
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        events("failed", "skipped")
    }
}
