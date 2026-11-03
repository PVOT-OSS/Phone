/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins { `java-library` }

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }

dependencies {
    implementation(project(":annotations"))
    implementation(libs.auto.common)
    implementation(libs.dagger)
    implementation(libs.guava.jre)
    implementation(libs.javapoet)
    implementation(libs.javax.inject)
    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)
}
