/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.inject

import javax.inject.Qualifier

/** Annotation for retrieving the application context. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationContext
