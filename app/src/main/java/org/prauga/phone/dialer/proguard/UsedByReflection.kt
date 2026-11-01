/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.proguard

/**
 * Denotes that the class, constructor, method or field is used for reflection and therefore cannot
 * be removed by tools like ProGuard. Use the value parameter to mention a file that uses the
 * component marked as UsedByReflection.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
)
annotation class UsedByReflection(val value: String)
