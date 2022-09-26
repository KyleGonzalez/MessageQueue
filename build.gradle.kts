import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.3"
    id("io.spring.dependency-management") version "1.0.14.RELEASE"
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.spring") version "1.7.10"
}

group = "au.kilemon"
// Make sure version matches version defined in MessageQueueApplication
version = "0.1.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:2.7.3")
    implementation("org.springframework.boot:spring-boot-starter-validation:2.7.3")

    implementation("com.google.code.gson:gson:2.9.1")

    compileOnly("org.projectlombok:lombok:1.18.24")

    testImplementation("org.springframework.boot:spring-boot-starter-test:2.7.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
