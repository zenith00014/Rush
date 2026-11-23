import com.google.protobuf.gradle.id

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.protobuf") version "0.9.6"
}

android {
    namespace = "com.rush.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rush.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric fetches its Android runtime separately from Gradle dependencies.
                // Use the same mirror configured in settings.gradle.kts, not repo1.maven.org.
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven-central.storage-download.googleapis.com/maven2/"
                )
                it.systemProperty("robolectric.dependency.repo.id", "google-maven-central-mirror")
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
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.7.0")
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
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7")
    implementation("com.github.libre-tube:NewPipeExtractor:894ae973d29b7c26502c72834bad636a72e345ca")
    implementation("com.google.protobuf:protobuf-javalite:4.33.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.33.5" }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins { id("java") { option("lite") } }
        }
    }
}
