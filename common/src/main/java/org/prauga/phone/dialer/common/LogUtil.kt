/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.common

import android.telephony.PhoneNumberUtils
import android.util.Log

/** Provides logging functions. */
object LogUtil {

    const val TAG = "Dialer"
    private const val SEPARATOR = " - "

    /**
     * Log at a verbose level. Verbose logs should generally be filtered out, but may be useful when
     * additional information is needed (e.g. to see how a particular flow evolved). These logs will
     * not generally be available on production builds.
     *
     * @param tag An identifier to allow searching for related logs. Generally of the form
     *   'Class.method'.
     * @param msg The message you would like logged, possibly with format arguments.
     * @param args Optional arguments to be used in the formatted string.
     * @see String.format
     * @see Log.v
     */
    @JvmStatic
    fun v(tag: String, msg: String?, vararg args: Any?) {
        println(Log.VERBOSE, TAG, tag, msg, *args)
    }

    /**
     * Log at a debug level. Debug logs should provide known-useful information to aid in
     * troubleshooting or evaluating flow. These logs will not generally be available on production
     * builds.
     *
     * @param tag An identifier to allow searching for related logs. Generally of the form
     *   'Class.method'
     * @param msg The message you would like logged, possibly with format arguments
     * @param args Optional arguments to be used in the formatted string
     * @see String.format
     * @see Log.d
     */
    @JvmStatic
    fun d(tag: String, msg: String?, vararg args: Any?) {
        println(Log.DEBUG, TAG, tag, msg, *args)
    }

    /**
     * Log at an info level. Info logs provide information that would be useful to have on
     * production builds for troubleshooting.
     *
     * @param tag An identifier to allow searching for related logs. Generally of the form
     *   'Class.method'.
     * @param msg The message you would like logged, possibly with format arguments.
     * @param args Optional arguments to be used in the formatted string.
     * @see String.format
     * @see Log.i
     */
    @JvmStatic
    fun i(tag: String, msg: String?, vararg args: Any?) {
        println(Log.INFO, TAG, tag, msg, *args)
    }

    /**
     * Log entry into a method at the info level.
     *
     * @param tag An identifier to allow searching for related logs. Generally of the form
     *   'Class.method'.
     */
    @JvmStatic
    fun enterBlock(tag: String) {
        println(Log.INFO, TAG, tag, "enter")
    }

    /**
     * Log at a warn level. Warn logs indicate a possible error (e.g. a default switch branch was
     * hit, or a null object was expected to be non-null), but recovery is possible. This may be
     * used when it is not guaranteed that an indeterminate or bad state was entered, just that
     * something may have gone wrong.
     *
     * @param tag An identifier to allow searching for related logs. Generally of the form
     *   'Class.method'.
     * @param msg The message you would like logged, possibly with format arguments.
     * @param args Optional arguments to be used in the formatted string.
     * @see String.format
     * @see Log.w
     */
    @JvmStatic
    fun w(tag: String, msg: String?, vararg args: Any?) {
        println(Log.WARN, TAG, tag, msg, *args)
    }

    /**
     * Log at an error level. Error logs are used when it is known that an error occurred and is
     * possibly fatal. This is used to log information that will be useful for troubleshooting a
     * crash or other severe condition (e.g. error codes, state values, etc.).
     *
     * @param tag An identifier to allow searching for related logs. Generally of the form
     *   'Class.method'.
     * @param msg The message you would like logged, possibly with format arguments.
     * @param args Optional arguments to be used in the formatted string.
     * @see String.format
     * @see Log.e
     */
    @JvmStatic
    fun e(tag: String, msg: String?, vararg args: Any?) {
        println(Log.ERROR, TAG, tag, msg, *args)
    }

    /**
     * Log an exception at an error level. Error logs are used when it is known that an error
     * occurred and is possibly fatal. This is used to log information that will be useful for
     * troubleshooting a crash or other severe condition (e.g. error codes, state values, etc.).
     *
     * @param tag An identifier to allow searching for related logs. Generally of the form
     *   'Class.method'.
     * @param msg The message you would like logged.
     * @param throwable The exception to log.
     * @see String.format
     * @see Log.e
     */
    @JvmStatic
    fun e(tag: String, msg: String?, throwable: Throwable) {
        if (!msg.isNullOrEmpty()) {
            println(Log.ERROR, TAG, tag, msg + "\n" + Log.getStackTraceString(throwable))
        }
    }

    /**
     * Used for log statements where we don't want to log various strings (e.g., usernames) with
     * default logging to avoid leaking PII in logcat.
     *
     * @return text as is if [TAG]'s log level is set to DEBUG or VERBOSE or on non-release builds;
     *   returns a redacted version otherwise.
     */
    @JvmStatic
    fun sanitizePii(`object`: Any?): String {
        if (`object` == null) {
            return "null"
        }
        if (isDebugEnabled()) {
            return `object`.toString()
        }
        return "Redacted-" + `object`.toString().length + "-chars"
    }

    /** Anonymizes char to prevent logging personally identifiable information. */
    @JvmStatic
    fun sanitizeDialPadChar(ch: Char): Char {
        if (isDebugEnabled()) {
            return ch
        }
        if (is12Key(ch)) {
            return '*'
        }
        return ch
    }

    /** Anonymizes the phone number to prevent logging personally identifiable information. */
    @JvmStatic
    fun sanitizePhoneNumber(phoneNumber: String?): String? {
        if (isDebugEnabled()) {
            return phoneNumber
        }
        if (phoneNumber == null) {
            return null
        }
        val stringBuilder = StringBuilder(phoneNumber.length)
        for (c in phoneNumber.toCharArray()) {
            stringBuilder.append(sanitizeDialPadChar(c))
        }
        return stringBuilder.toString()
    }

    @JvmStatic fun isVerboseEnabled(): Boolean = Log.isLoggable(TAG, Log.VERBOSE)

    @JvmStatic fun isDebugEnabled(): Boolean = Log.isLoggable(TAG, Log.DEBUG)

    private fun is12Key(ch: Char): Boolean = PhoneNumberUtils.is12Key(ch)

    private fun println(
        level: Int,
        tag: String,
        localTag: String,
        msg: String?,
        vararg args: Any?,
    ) {
        // Formatted message is computed lazily if required.
        // Either null is passed as a single argument or more than one argument is passed.
        val hasArgs = args.isNotEmpty()
        if (level >= Log.INFO || Log.isLoggable(tag, level)) {
            var formattedMsg = localTag
            if (!msg.isNullOrEmpty()) {
                formattedMsg += SEPARATOR + (if (hasArgs) String.format(msg, *args) else msg)
            }
            Log.println(level, tag, formattedMsg)
        }
    }
}
