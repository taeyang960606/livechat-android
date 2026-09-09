plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wondergoland.livechat.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wondergoland.livechat.sample"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Bytecode target rather than a toolchain request: a toolchain pins the
    // build to one JDK and makes Gradle go download it when the machine has a
    // different one. This compiles on any JDK 17 or newer, which is what CI
    // images and developer machines actually have.
    kotlinOptions {
        jvmTarget = "17"
    }
}


dependencies {
    implementation(project(":livechat"))
    implementation("androidx.appcompat:appcompat:1.7.0")
}
