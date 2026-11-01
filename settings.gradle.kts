/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // PvotLib, published with `./gradlew publishToMavenLocal` from its own checkout.
        mavenLocal {
            content { includeGroup("com.prauga.pvot") }
        }
    }
}

// Build PvotLib from source instead of Maven local: pass -Ppvot.localPath=../PvotLib
// (or set it in ~/.gradle/gradle.properties) to work on both at once.
providers.gradleProperty("pvot.localPath").orNull?.let { includeBuild(it) }

rootProject.name = "Phone"
include(":app")
