import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildTimestamp = SimpleDateFormat("yyMMddHHmm", Locale.getDefault()).format(Date())

android {
    namespace = "com.catchtouch.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.catchtouch.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4.$buildTimestamp"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "android123"
            keyAlias = "release"
            keyPassword = "android123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

tasks.register<Copy>("renameReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release.apk")
    }
    into(layout.buildDirectory.dir("outputs/apk/release"))
    rename("app-release.apk", "CatchTouch-v1.4.${buildTimestamp}.apk")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
