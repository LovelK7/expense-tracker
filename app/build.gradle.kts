import java.io.StringReader
import java.security.MessageDigest
import java.util.Base64
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

// Version: the release workflow passes these from the tag vX.Y.Z
// (versionCode = X*10000 + Y*100 + Z). Local builds default to 0.1.0-dev / 1.
val appVersionName: String = providers.gradleProperty("appVersionName").orNull?.trim()
    ?.takeIf { it.isNotEmpty() } ?: "0.1.0-dev"
val appVersionCode: Int = providers.gradleProperty("appVersionCode").orNull?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { it.toIntOrNull()?.takeIf { code -> code > 0 } ?: throw GradleException("appVersionCode must be a positive integer, got '$it'") }
    ?: 1

// Release signing (README "Releases"). Sources, first match wins:
// 1. private/keystore.properties (see keystore.properties.example); storeFile is relative to
//    this module.
// 2. Environment variables TROSKOVI_KEYSTORE_BASE64 (decoded to a file in the temp directory),
//    TROSKOVI_KEYSTORE_PASSWORD, TROSKOVI_KEY_PASSWORD, TROSKOVI_KEY_ALIAS (release workflow).
// 3. Neither: release builds are unsigned (app-release-unsigned.apk), so CI without secrets works.
class ReleaseSigning(val storeFile: File, val storePassword: String, val keyPassword: String, val keyAlias: String)

val releaseSigning: ReleaseSigning? = run {
    val propertiesText = providers.fileContents(rootProject.layout.projectDirectory.file("private/keystore.properties"))
        .asText.orNull
    if (propertiesText != null) {
        val props = Properties().apply { load(StringReader(propertiesText)) }
        fun required(key: String): String = props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw GradleException("private/keystore.properties: '$key' is missing")
        val storeFile = file(required("storeFile"))
        if (!storeFile.isFile) throw GradleException("private/keystore.properties: storeFile $storeFile does not exist")
        return@run ReleaseSigning(storeFile, required("storePassword"), required("keyPassword"), required("keyAlias"))
    }
    val names = listOf("TROSKOVI_KEYSTORE_BASE64", "TROSKOVI_KEYSTORE_PASSWORD", "TROSKOVI_KEY_PASSWORD", "TROSKOVI_KEY_ALIAS")
    val env = names.associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
    if (env.values.all { it == null }) return@run null
    val missing = env.filterValues { it == null }.keys
    if (missing.isNotEmpty()) throw GradleException("Release signing: set ${missing.joinToString()} too (or none of them)")
    val keystore = Base64.getMimeDecoder().decode(env.getValue("TROSKOVI_KEYSTORE_BASE64"))
    // Named by content, so a configuration-cache hit (which skips this block) finds the same file.
    val hash = MessageDigest.getInstance("SHA-256").digest(keystore).joinToString("") { "%02x".format(it) }.take(16)
    val storeFile = File(System.getProperty("java.io.tmpdir"), "troskovi-signing/release-$hash.jks")
    if (!storeFile.isFile || !storeFile.readBytes().contentEquals(keystore)) {
        storeFile.parentFile.mkdirs()
        storeFile.writeBytes(keystore)
    }
    ReleaseSigning(
        storeFile,
        env.getValue("TROSKOVI_KEYSTORE_PASSWORD")!!.trim(),
        env.getValue("TROSKOVI_KEY_PASSWORD")!!.trim(),
        env.getValue("TROSKOVI_KEY_ALIAS")!!.trim(),
    )
}

if (releaseSigning == null && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    logger.warn(
        "WARNING: no release signing config (private/keystore.properties or TROSKOVI_* environment " +
            "variables); the release APK will be UNSIGNED (app-release-unsigned.apk).",
    )
}

android {
    namespace = "hr.ml.expensetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "hr.ml.expensetracker"
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyPassword = releaseSigning.keyPassword
                keyAlias = releaseSigning.keyAlias
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useFirebaseEmulators.toString())
        }
        release {
            // R8 (code + resource shrinking) is a later step: Firebase and Koin need keep rules first.
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = if (releaseSigning != null) signingConfigs.getByName("release") else null
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
