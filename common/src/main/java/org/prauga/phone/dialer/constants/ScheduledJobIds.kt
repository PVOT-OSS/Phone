/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.constants

/**
 * Registry of scheduled job ids used by the dialer UID.
 *
 * Any dialer jobs which use the android JobScheduler should register their IDs here, to avoid the
 * same ID accidentally being reused.
 *
 * Do not change any existing IDs.
 */
object ScheduledJobIds {
    // This job refreshes dynamic launcher shortcuts.
    const val SHORTCUT_PERIODIC_JOB = 100

    const val VVM_TASK_SCHEDULER_JOB = 200
    const val VVM_STATUS_CHECK_JOB = 201
    const val VVM_DEVICE_PROVISIONED_JOB = 202
    const val VVM_NOTIFICATION_JOB = 205
}
