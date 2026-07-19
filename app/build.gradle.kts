plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    // DI
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.flx_apps.digitaldetox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flx_apps.digitaldetox"
        minSdk = 26
        targetSdk = 33
        versionCode = 20501
        versionName = "2.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        compileOptions {
            languageVersion = "1.9"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
    }

    testOptions {
        unitTests {
            // Roborazzi/Robolectric render real resources, so they must be on the test classpath.
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("-Xmx3g")
                // Forward the roborazzi Gradle properties (set by the generateScreenshots task or
                // -Proborazzi.test.record=true on the command line) to JVM system properties.
                if (project.hasProperty("roborazzi.test.record")) {
                    it.systemProperty("roborazzi.test.record", "true")
                }
                if (project.hasProperty("roborazzi.test.verify")) {
                    it.systemProperty("roborazzi.test.verify", "true")
                }
            }
        }
    }
}

// ── Play Store / F-Droid marketing screenshots ───────────────────────────────
//
// generateScreenshots — renders the StoreScreenshotTest in record mode, writing PNGs to
// fastlane/metadata/android/<locale>/images/phoneScreenshots/.
//
// Usage:
//   ./gradlew :app:generateScreenshots
//
afterEvaluate {
    val baseTest = tasks.findByName("testDebugUnitTest") as? Test ?: return@afterEvaluate

    tasks.register<Test>("generateScreenshots") {
        description = "Generates the Play Store / F-Droid phone screenshots (record mode)."
        group = "marketing"
        testClassesDirs = baseTest.testClassesDirs
        classpath = baseTest.classpath
        filter {
            includeTestsMatching("*.StoreScreenshotTest")
        }
        jvmArgs("-Xmx3g")
        systemProperty("roborazzi.test.record", "true")
        outputs.upToDateWhen { false }
    }
}

dependencies {
    // Core dependencies
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata") // observeAsState() extension function
    implementation("androidx.datastore:datastore-core:1.1.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Preferences / DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.3")

    // Timber (Logging)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // DI
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Navigation Library
    implementation("dev.olshevski.navigation:reimagined:1.5.0")
    implementation("dev.olshevski.navigation:reimagined-hilt:1.5.0")
    implementation(kotlin("reflect"))

    // Number picker
    implementation("com.chargemap.compose:numberpicker:1.0.3")

    // ViewTreeLifecycleOwner
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Chart Engine (custom Canvas-based, no third-party library)

    // Material Icons
    implementation("androidx.compose.material:material-icons-extended")

    // libsu for running (adb) commands as root
    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    // Shizuku for elevated permissions without root
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Security: EncryptedSharedPreferences for commitment password storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // BCrypt for commitment password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // Room database for historical usage stats
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // ── Roborazzi screenshot testing (record-gated; see generateScreenshots task) ──
    val roborazziVersion = "1.68.0"
    testImplementation("io.github.takahirom.roborazzi:roborazzi:$roborazziVersion")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazziVersion")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:$roborazziVersion")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    kspTest("com.google.dagger:hilt-compiler:2.59.2")
}

kotlin.sourceSets.all {
    languageSettings.enableLanguageFeature("DataObjects")
}
