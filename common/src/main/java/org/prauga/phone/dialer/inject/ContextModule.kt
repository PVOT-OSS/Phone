/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.inject

import android.content.Context
import dagger.Module
import dagger.Provides

/** Provides the singleton application context object. */
@Module
class ContextModule(private val context: Context) {

    @Provides @ApplicationContext fun provideContext(): Context = context
}
