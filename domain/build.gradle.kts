plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Repository interfaces expose kotlinx.coroutines Flow.
    api(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The golden test reads the (gitignored) legacy export; rerun it when that file changes.
    inputs.files(layout.projectDirectory.file("../private/legacy_popis.csv"))
        .withPropertyName("legacyCsv")
        .optional()
    testLogging {
        events("skipped", "failed")
        showStandardStreams = false
    }
}
