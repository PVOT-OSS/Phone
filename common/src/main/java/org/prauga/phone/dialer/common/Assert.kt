/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.common

import android.os.Looper
import javax.annotation.CheckReturnValue

/** Assertions which will result in program termination unless disabled by flags. */
object Assert {

    /**
     * Called when a truly exceptional case occurs.
     *
     * @throws AssertionError
     */
    @JvmStatic
    @Deprecated("Use throw Assert.create*FailException() instead.")
    fun fail() {
        throw AssertionError("Fail")
    }

    /**
     * Called when a truly exceptional case occurs.
     *
     * @param reason the optional reason to supply as the exception message
     * @throws AssertionError
     */
    @JvmStatic
    @Deprecated("Use throw Assert.create*FailException() instead.")
    fun fail(reason: String?) {
        throw AssertionError(reason)
    }

    @JvmStatic
    @CheckReturnValue
    fun createAssertionFailException(msg: String?): AssertionError = AssertionError(msg)

    @JvmStatic
    @CheckReturnValue
    fun createAssertionFailException(msg: String?, reason: Throwable?): AssertionError =
        AssertionError(msg, reason)

    @JvmStatic
    @CheckReturnValue
    fun createUnsupportedOperationFailException(): UnsupportedOperationException =
        UnsupportedOperationException()

    @JvmStatic
    @CheckReturnValue
    fun createUnsupportedOperationFailException(msg: String?): UnsupportedOperationException =
        UnsupportedOperationException(msg)

    @JvmStatic
    @CheckReturnValue
    fun createIllegalStateFailException(): IllegalStateException = IllegalStateException()

    @JvmStatic
    @CheckReturnValue
    fun createIllegalStateFailException(msg: String?): IllegalStateException =
        IllegalStateException(msg)

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * @param expression a boolean expression
     * @throws IllegalArgumentException if `expression` is false
     */
    @JvmStatic
    fun checkArgument(expression: Boolean) {
        checkArgument(expression, null)
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param messageTemplate the message to log, possible with format arguments.
     * @param args optional arguments to be used in the formatted string.
     * @throws IllegalArgumentException if `expression` is false
     */
    @JvmStatic
    fun checkArgument(expression: Boolean, messageTemplate: String?, vararg args: Any?) {
        if (!expression) {
            throw IllegalArgumentException(format(messageTemplate, *args))
        }
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @throws IllegalStateException if `expression` is false
     */
    @JvmStatic
    fun checkState(expression: Boolean) {
        checkState(expression, null)
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param messageTemplate the message to log, possible with format arguments.
     * @param args optional arguments to be used in the formatted string.
     * @throws IllegalStateException if `expression` is false
     */
    @JvmStatic
    fun checkState(expression: Boolean, messageTemplate: String?, vararg args: Any?) {
        if (!expression) {
            throw IllegalStateException(format(messageTemplate, *args))
        }
    }

    /**
     * Ensures that an object reference passed as a parameter to the calling method is not null.
     *
     * @param reference an object reference
     * @return the non-null reference that was validated
     * @throws NullPointerException if `reference` is null
     */
    @JvmStatic fun <T : Any> isNotNull(reference: T?): T = isNotNull(reference, null)

    /**
     * Ensures that an object reference passed as a parameter to the calling method is not null.
     *
     * @param reference an object reference
     * @param messageTemplate the message to log, possible with format arguments.
     * @param args optional arguments to be used in the formatted string.
     * @return the non-null reference that was validated
     * @throws NullPointerException if `reference` is null
     */
    @JvmStatic
    fun <T : Any> isNotNull(reference: T?, messageTemplate: String?, vararg args: Any?): T {
        if (reference == null) {
            throw NullPointerException(format(messageTemplate, *args))
        }
        return reference
    }

    /**
     * Ensures that the current thread is the main thread.
     *
     * @throws IllegalStateException if called on a background thread
     */
    @JvmStatic
    fun isMainThread() {
        isMainThread(null)
    }

    /**
     * Ensures that the current thread is the main thread.
     *
     * @param messageTemplate the message to log, possible with format arguments.
     * @param args optional arguments to be used in the formatted string.
     * @throws IllegalStateException if called on a background thread
     */
    @JvmStatic
    fun isMainThread(messageTemplate: String?, vararg args: Any?) {
        checkState(Looper.getMainLooper() == Looper.myLooper(), messageTemplate, *args)
    }

    /**
     * Ensures that the current thread is a worker thread.
     *
     * @throws IllegalStateException if called on the main thread
     */
    @JvmStatic
    fun isWorkerThread() {
        isWorkerThread(null)
    }

    /**
     * Ensures that the current thread is a worker thread.
     *
     * @param messageTemplate the message to log, possible with format arguments.
     * @param args optional arguments to be used in the formatted string.
     * @throws IllegalStateException if called on the main thread
     */
    @JvmStatic
    fun isWorkerThread(messageTemplate: String?, vararg args: Any?) {
        checkState(Looper.getMainLooper() != Looper.myLooper(), messageTemplate, *args)
    }

    private fun format(messageTemplate: String?, vararg args: Any?): String? {
        if (messageTemplate == null) {
            return null
        }
        return String.format(messageTemplate, *args)
    }
}
