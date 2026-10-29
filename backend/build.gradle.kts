// Versiones y dependencias: gradle/libs.versions.toml (catálogo único).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.nuvyra"
version = "0.1.0"

// Alinea stdlib, reflect y bibliotecas Kotlin con la versión de los plugins.
extra["kotlin.version"] = libs.versions.kotlin.get()

java {
    // Usa el JDK que ejecuta Gradle (JAVA_HOME), incluido Java 26, sin exigir instalar otro JDK.
    // El artefacto conserva el mínimo Java 21 LTS para el contenedor de producción.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // Aplicación web y seguridad
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.mail)

    // Base de datos
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Kotlin y JSON
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    // Documentación de la API e importación de Excel
    implementation(libs.springdoc.openapi)
    implementation(libs.poi.ooxml)

    // Código QR de la verificación en dos pasos
    implementation(libs.zxing.core)

    // Pruebas
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjdk-release=21")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("user.timezone", "UTC")
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

tasks.jar {
    enabled = false
}
