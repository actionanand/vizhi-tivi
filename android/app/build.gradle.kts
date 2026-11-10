import groovy.json.JsonSlurper
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val repoRoot = rootProject.projectDir.parentFile
fun parseObject(fileName: String): Map<*, *> =
    JsonSlurper().parse(repoRoot.resolve(fileName)) as Map<*, *>
val config = parseObject("app-config.json")
val androidVersion = parseObject("android-version.json")
val tvVersion = parseObject("tv-version.json")
val sdk = Properties().apply {
    repoRoot.resolve("android-sdk.properties").inputStream().use { load(it) }
}
fun versionCodeFrom(data: Map<*, *>) = (data["versionCode"] as Number).toInt()
fun versionNameFrom(data: Map<*, *>) = data["versionName"] as String

val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val signingStorePassword = System.getenv("SIGNING_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val signingAlias = System.getenv("SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val signingEnabled = keystorePath != null && signingStorePassword != null && signingAlias != null

android {
    namespace = "com.actionanand.vizhitivi"
    compileSdk = sdk.getProperty("COMPILE_SDK_VERSION").toInt()
    buildToolsVersion = sdk.getProperty("BUILD_TOOLS_VERSION")

    defaultConfig {
        applicationId = config["mobileApplicationId"] as String
        minSdk = sdk.getProperty("MIN_SDK_VERSION").toInt()
        targetSdk = sdk.getProperty("TARGET_SDK_VERSION").toInt()
        versionCode = versionCodeFrom(androidVersion)
        versionName = versionNameFrom(androidVersion)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", config["appName"] as String)
    }

    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
            applicationId = config["mobileApplicationId"] as String
            versionCode = versionCodeFrom(androidVersion)
            versionName = versionNameFrom(androidVersion)
            manifestPlaceholders["launcherCategory"] = "android.intent.category.LAUNCHER"
        }
        create("tv") {
            dimension = "device"
            applicationId = config["tvApplicationId"] as String
            versionCode = versionCodeFrom(tvVersion)
            versionName = versionNameFrom(tvVersion)
            manifestPlaceholders["launcherCategory"] = "android.intent.category.LEANBACK_LAUNCHER"
        }
    }

    if (signingEnabled) {
        signingConfigs {
            create("ciRelease") {
                storeFile = file(keystorePath!!)
                storePassword = signingStorePassword!!
                keyAlias = signingAlias!!
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: signingStorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false // starter only; enable after rules and tests are in place
            if (signingEnabled) signingConfig = signingConfigs.getByName("ciRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
