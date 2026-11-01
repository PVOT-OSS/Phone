/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.roborazzi)
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 0

android {
    namespace = "org.prauga.phone.dialer"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "org.prauga.phone"
        minSdk = 34
        targetSdk = 37
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Installable from Play and F-Droid: no privileged permissions.
        create("user") {
            dimension = "distribution"
        }
        // Built into the PVOT ROM as a privileged app: call recording and system settings.
        create("system") {
            dimension = "distribution"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
        compose = true
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        warning += "MissingTranslation"
    }

    testOptions {
        unitTests {
            // Robolectric and Roborazzi load resources and the merged manifest.
            isIncludeAndroidResources = true
            all {
                // Robolectric's SDK 36 shared-memory shim reaches into this JDK package.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// The user build is installed like any other app, so it must not ask for permissions only a
// privileged system app can hold. Their source of truth is the system flavour's manifest.
abstract class CheckUserPermissions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val systemManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val permission = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
        val systemOnly = permission.findAll(systemManifest.get().asFile.readText()).map { it.groupValues[1] }.toSet()
        val requested = permission.findAll(mergedManifest.get().asFile.readText()).map { it.groupValues[1] }.toSet()
        val leaked = requested intersect systemOnly
        if (leaked.isNotEmpty()) {
            throw GradleException("The user build requests system-only permissions: ${leaked.sorted().joinToString()}")
        }
        report.get().asFile.writeText(requested.sorted().joinToString("\n"))
    }
}

androidComponents {
    onVariants(selector().withFlavor("distribution" to "user")) { variant ->
        val check = tasks.register<CheckUserPermissions>("check${variant.name.replaceFirstChar { it.uppercase() }}Permissions") {
            mergedManifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            systemManifest.set(layout.projectDirectory.file("src/system/AndroidManifest.xml"))
            report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(check) }
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

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
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.javax.inject)
    implementation(libs.jsr305)
    implementation(libs.libphonenumber)
    implementation(libs.libphonenumber.geocoder)
    implementation(libs.material)
    implementation(libs.protobuf.javalite)
    implementation(libs.pvot.core.ui)
    implementation(libs.pvot.design.system)
    implementation(libs.volley)
    compileOnly(libs.auto.value.annotations)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    annotationProcessor(libs.auto.value)
    annotationProcessor(libs.dagger.compiler)
    annotationProcessor(libs.glide.compiler)
}
