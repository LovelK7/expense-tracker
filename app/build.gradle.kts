import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Reads app/google-services.json (gitignored; CI copies google-services.json.example).
    alias(libs.plugins.google.services)
}

// Debug builds only: point Firebase at the local emulator suite and offer dev sign-in buttons
// (README "Local development with the Firebase emulators"). Set useFirebaseEmulators=true in
// local.properties or pass -PuseFirebaseEmulators=true.
val useFirebaseEmulators: Boolean = providers.gradleProperty("useFirebaseEmulators").orNull?.toBoolean()
    ?: providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { text -> Properties().apply { load(StringReader(text)) }.getProperty("useFirebaseEmulators") }
        ?.trim()?.toBoolean()
    ?: false

android {
    namespace = "hr.ml.expensetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "hr.ml.expensetracker"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useFirebaseEmulators.toString())
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // In-memory repositories shared by the JVM unit tests and the instrumented UI tests.
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
