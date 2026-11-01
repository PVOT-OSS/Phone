/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.function

/** Functional interface for consuming generic values. */
fun interface Consumer<T> {

    /** Consumes a value. */
    fun accept(value: T)
}
