import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Firebase: reads app/google-services.json (project "adwizzzz").
    id("com.google.gms.google-services")
    // Crash reports with readable stack traces (uploads R8 mapping files for release builds).
    id("com.google.firebase.crashlytics")
}

// Release signing: reads keystore.properties (kept out of git). Without it, release builds fall
// back to the debug key so `assembleRelease` still works on any machine.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "rocks.chirag.adwizz"
    compileSdk = 37

    defaultConfig {
        applicationId = "rocks.chirag.adwizz"
        minSdk = 24  // Android 7.0: Firebase Analytics 24+ requires it
        targetSdk = 37
        versionCode = 7
        versionName = "1.3.2"
        // Phones (and Apple-silicon emulators) are ARM; x86 builds would only add size.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        // The optional "AdWizz Reels Blocker" add-on (the :focus module).
        buildConfigField("String", "FOCUS_PACKAGE", "\"rocks.chirag.adwizz.focus\"")
        manifestPlaceholders["focusPackage"] = "rocks.chirag.adwizz.focus"
    }

    // ShortVideoRules is shared with the add-on: AdWizz uses its DNS domains, the add-on the rest.
    sourceSets["main"].java.srcDir("../shared/focus-rules")

    // The UI is English-only: don't ship every library's ~80 translations.
    androidResources { localeFilters += listOf("en") }

    packaging {
        resources.excludes += listOf(
            "kotlin/**", "META-INF/*.version", "META-INF/**/*.kotlin_module", "META-INF/androidx/**",
            "DebugProbesKt.bin", "META-INF/com/android/build/gradle/**",
            // Build-time leftovers nothing reads at runtime.
            "kotlin-tooling-metadata.json", "client_analytics.proto", "META-INF/version-control-info.textproto",
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Own package name, so a test build sits next to the real app instead of blocking its
            // install ("App not installed": the debug and release keys differ). Registered in Firebase.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "FOCUS_PACKAGE", "\"rocks.chirag.adwizz.focus.debug\"")
            manifestPlaceholders["focusPackage"] = "rocks.chirag.adwizz.focus.debug"
        }
        release {
            vcsInfo.include = false   // no git commit info file in the APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    lint {
        // x86 Chromebooks would need x86 native libs; ARM Chromebooks and all phones work. Leaving
        // x86 out keeps the APK smaller (see ndk.abiFilters).
        disable += "ChromeOsAbiSupport"
        // activity-compose / lifecycle are pinned to older (secure, supported) versions on purpose:
        // the newer ones pull in ~35 KB more code with nothing this app uses.
        disable += setOf("GradleDependency", "NewerVersionAvailable")
        // Suggests fragment-ktx; nothing here uses its Kotlin extensions, so the plain artifact is enough.
        disable += "KtxExtensionAvailable"
    }

    testOptions {
        // Robolectric needs merged resources/assets (starter lists, themes) on the JVM.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            // `./gradlew testDebugUnitTest -Plive=true` also runs tests that hit the real internet.
            it.systemProperty("adwizz.live", project.findProperty("live") ?: "false")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.zxing:core:3.5.4")

    // Firebase (versions come from the BoM).
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    // Firebase pulls in an old Fragment; registerForActivityResult (VPN consent) needs >= 1.3.
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Debug builds only: watches for leaked Activities/ViewModels/Services and reports them.
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
