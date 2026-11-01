/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.function

/** Functional interface for supplying generic values. */
fun interface Supplier<T> {

    /** Supplies a value. */
    fun get(): T
}
