import com.google.protobuf.gradle.id
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf") version "0.9.6"
}

val releaseStoreFile = providers.environmentVariable("RUSH_UPLOAD_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RUSH_UPLOAD_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RUSH_UPLOAD_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RUSH_UPLOAD_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

val robolectricSdkRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val prepareRobolectricRuntime by tasks.registering {
    val runtimeArtifacts = robolectricSdkRuntime
    val runtimeDirectory = layout.buildDirectory.dir("robolectric-runtime")
    doLast {
        val artifacts = runtimeArtifacts.files
        check(artifacts.size == 1) {
            "Expected one preinstrumented Robolectric SDK jar, found ${artifacts.size}"
        }
        val destinationDirectory = runtimeDirectory.get().asFile
        destinationDirectory.mkdirs()
        val destination = File(
            destinationDirectory,
            "android-all-instrumented-15-robolectric-12650502-i7.jar"
        )
        artifacts.single().copyTo(destination, overwrite = true)
    }
}

android {

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    namespace = "com.rush.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rush.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Fetch the large Android framework jar as a Gradle dependency, then let
                // Robolectric load that local artifact without making its own HTTP request.
                it.dependsOn(prepareRobolectricRuntime)
                it.systemProperty(
                    "robolectric.dependency.dir",
                    layout.buildDirectory.dir("robolectric-runtime").get().asFile.absolutePath
                )
                it.systemProperty("robolectric.offline", "true")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.media3:media3-exoplayer:1.9.2")
    implementation("androidx.media3:media3-datasource:1.9.2")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.2")
    implementation("androidx.media3:media3-database:1.9.2")
    implementation("androidx.media3:media3-exoplayer-dash:1.9.2")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.2")
    implementation("androidx.media3:media3-ui:1.9.2")
    implementation("androidx.media3:media3-session:1.9.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("com.github.libre-tube:NewPipeExtractor:894ae973d29b7c26502c72834bad636a72e345ca")
    implementation("com.google.protobuf:protobuf-javalite:4.33.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    robolectricSdkRuntime("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.33.5" }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins { id("java") { option("lite") } }
        }
    }
}
