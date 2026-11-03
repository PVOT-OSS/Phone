/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.prauga.phone.common"
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

    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.preference)
    implementation(libs.dagger)
    implementation(libs.guava)
    implementation(libs.javax.inject)
    implementation(libs.jsr305)
    implementation(libs.protobuf.javalite)
    compileOnly(libs.auto.value.annotations)

    // Dagger generates through KSP, which reads the Kotlin and Java sources alike.
    ksp(libs.dagger.compiler)
}
