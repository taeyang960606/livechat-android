plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.wondergoland.livechat"
    compileSdk = 34

    defaultConfig {
        // 23 rather than 21 because the WebView error callback this SDK reports
        // through ErrorListener only exists from 23: on 21 and 22 a failed page
        // load would be silent, which is worse than not supporting them. Those
        // two versions are also long past the share any of this is built for.
        minSdk = 23
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}


dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.taeyang960606"
            artifactId = "livechat-android"
            version = "0.2.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
