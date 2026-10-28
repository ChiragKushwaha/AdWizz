import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// "AdWizz Reels Blocker": the optional add-on that holds the Accessibility service. It lives in
// its own APK because Play Protect blocks any downloaded app that can use Accessibility, and the
// ad blocker itself shouldn't be blocked for that. AdWizz downloads and installs it on demand.

// Signed with the same key as AdWizz: AdWizz checks this before installing it, and the add-on
// reads the switch from AdWizz through a signature-only permission.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val mainAppId = "rocks.chirag.adwizz"

android {
    namespace = "rocks.chirag.adwizz.focus"
    compileSdk = 37

    defaultConfig {
        applicationId = "rocks.chirag.adwizz.focus"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["mainAppId"] = mainAppId
        buildConfigField("String", "MAIN_APP_ID", "\"$mainAppId\"")
    }
    buildFeatures { buildConfig = true }

    androidResources { localeFilters += listOf("en") }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets["main"].java.srcDir("../shared/focus-rules")

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
            // Pairs with AdWizz's debug build (rocks.chirag.adwizz.debug).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["mainAppId"] = "$mainAppId.debug"
            buildConfigField("String", "MAIN_APP_ID", "\"$mainAppId.debug\"")
        }
        release {
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
    lint {
        disable += setOf("ChromeOsAbiSupport", "GradleDependency", "NewerVersionAvailable")
        // allowBackup is off: the add-on keeps no data of its own.
        disable += "DataExtractionRules"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
