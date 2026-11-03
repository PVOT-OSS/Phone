/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "org.prauga.phone.calls"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    // Resources keep the names the app always gave them.
    resourcePrefix = null

    defaultConfig {
        // Same as the app: all code may use API 34.
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            // Robolectric loads resources and the merged manifest.
            isIncludeAndroidResources = true
            all {
                // Robolectric's SDK 36 shared-memory shim reaches into this JDK package.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        warning += "MissingTranslation"
    }
}

dependencies {
    testImplementation(libs.junit)
    implementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.pvot.design.system)

    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(project(":common"))
    implementation(project(":telephony"))
    implementation(project(":ui"))
    implementation(project(":contacts"))
    implementation(project(":data"))
    implementation(project(":voicemail"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.preference)
    implementation(libs.dagger)
    implementation(libs.guava)
    implementation(libs.protobuf.javalite)
    implementation(libs.javax.inject)
    implementation(libs.jsr305)
    compileOnly(libs.auto.value.annotations)

    annotationProcessor(libs.auto.value)
    // Dagger generates through KSP, which reads the Kotlin and Java sources alike.
    ksp(libs.dagger.compiler)
}
