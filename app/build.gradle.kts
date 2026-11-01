/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("pvot.android.application")
    id("pvot.android.compose")
    alias(libs.plugins.protobuf)
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 0

android {
    namespace = "org.prauga.phone.dialer"

    defaultConfig {
        applicationId = "org.prauga.phone"
        minSdk = 34
        targetSdk = 37
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            // Libraries ship their .proto files for each other's imports; the app doesn't need them.
            excludes += "org/prauga/**/*.proto"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        warning += "MissingTranslation"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":telephony"))
    implementation(project(":annotations"))
    annotationProcessor(project(":processor"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.apache.mime4j.core)
    implementation(libs.apache.mime4j.dom)
    implementation(libs.commons.io)
    implementation(libs.dagger)
    implementation(libs.error.prone.annotations)
    implementation(libs.glide)
    implementation(libs.guava)
    implementation(libs.javax.inject)
    implementation(libs.jsr305)
    implementation(libs.libphonenumber)
    implementation(libs.libphonenumber.geocoder)
    implementation(libs.material)
    implementation(libs.androidx.car.app)
    implementation(libs.protobuf.javalite)
    implementation(libs.pvot.design.system)
    implementation(libs.volley)
    compileOnly(libs.auto.value.annotations)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)

    annotationProcessor(libs.auto.value)
    annotationProcessor(libs.dagger.compiler)
    annotationProcessor(libs.glide.compiler)
}
