/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "org.prauga.phone.dialer"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "org.prauga.phone"
        minSdk = 34
        targetSdk = 37
        versionCode = 2900000
        versionName = "23.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
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
    implementation(libs.protobuf.javalite)
    implementation(libs.volley)
    compileOnly(libs.auto.value.annotations)

    annotationProcessor(libs.auto.value)
    annotationProcessor(libs.dagger.compiler)
    annotationProcessor(libs.glide.compiler)
}
