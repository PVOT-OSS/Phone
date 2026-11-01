/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.inject

/**
 * Used by packages to access the root component from the Application without creating a dependency
 * cycle.
 */
interface HasRootComponent {
    fun component(): Any
}
