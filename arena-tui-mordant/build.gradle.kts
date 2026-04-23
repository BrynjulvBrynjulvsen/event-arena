plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))

    implementation(project(":arena-domain"))
    implementation(libs.kafka.clients)
    implementation(libs.confluent.json.schema.serializer) {
        exclude(group = "org.apache.kafka", module = "kafka-clients")
    }
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mordant.jvm)
    runtimeOnly("org.slf4j:slf4j-nop")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
}

application {
    mainClass = "io.practicegroup.arena.tui.mordant.ArenaTuiMordantKt"
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
