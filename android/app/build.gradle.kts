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
    compileSdk {
        version = release(sdk.getProperty("COMPILE_SDK_VERSION").toInt()) {
            minorApiLevel = sdk.getProperty("COMPILE_SDK_MINOR").toInt()
        }
    }
    buildToolsVersion = sdk.getProperty("BUILD_TOOLS_VERSION")

    defaultConfig {
        applicationId = config["mobileApplicationId"] as String
        minSdk = sdk.getProperty("MIN_SDK_VERSION").toInt()
        targetSdk = sdk.getProperty("TARGET_SDK_VERSION").toInt()
        versionCode = versionCodeFrom(androidVersion)
        versionName = versionNameFrom(androidVersion)
        testInstrumentationRunner = "com.actionanand.vizhitivi.PlatformInstrumentation"
        resValue("string", "app_name", config["appName"] as String)
        buildConfigField("String", "CATALOGUE_MANIFEST_URL", groovy.json.JsonOutput.toJson(config["catalogueManifestUrl"]))
    }

    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
            applicationId = config["mobileApplicationId"] as String
            versionCode = versionCodeFrom(androidVersion)
            versionName = versionNameFrom(androidVersion)
        }
        create("tv") {
            dimension = "device"
            applicationId = config["tvApplicationId"] as String
            versionCode = versionCodeFrom(tvVersion)
            versionName = versionNameFrom(tvVersion)
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

    buildFeatures { compose = true; buildConfig = true; resValues = true }
    bundle { language { enableSplit = false } }
    sourceSets.getByName("test").resources.directories.add("../../contracts/fixtures")
    sourceSets.getByName("androidTest").assets.directories.add("../../contracts/fixtures/official-v1/synthetic")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.tv:tv-material:1.0.1")
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.3")
    implementation("androidx.media3:media3-exoplayer-dash:1.9.3")
    implementation("androidx.media3:media3-ui:1.9.3")
    testImplementation("org.json:json:20240303")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
