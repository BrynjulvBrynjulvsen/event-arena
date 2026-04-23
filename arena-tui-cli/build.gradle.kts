plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))

    implementation(project(":arena-domain"))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.json)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.confluent.json.schema.serializer) {
        exclude(group = "org.apache.kafka", module = "kafka-clients")
    }
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
