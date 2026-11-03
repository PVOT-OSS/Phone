/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("pvot.android.library")
    alias(libs.plugins.protobuf)
}

android {
    namespace = "org.prauga.phone.ui"
    // Resources keep the names the app always gave them.
    resourcePrefix = null

    defaultConfig {
        // Same as the app: all code may use API 34.
        minSdk = 34
    }

    lint {
        baseline = file("lint-baseline.xml")
        warning += "MissingTranslation"
    }

    // Ship the .proto files so modules above can import them.
    sourceSets { getByName("main") { resources.directories.add("src/main/proto") } }
}

protobuf {
    protoc { artifact = libs.protoc.get().toString() }
    generateProtoTasks { all().configureEach { builtins { create("java") { option("lite") } } } }
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
    compileOnly(libs.auto.value.annotations)

    annotationProcessor(libs.auto.value)
    annotationProcessor(libs.dagger.compiler)
}
