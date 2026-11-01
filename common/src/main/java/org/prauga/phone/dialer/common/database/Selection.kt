/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.prauga.phone.dialer.common.database

import android.text.TextUtils
import java.util.Collections
import org.prauga.phone.dialer.common.Assert

/**
 * Utility to build SQL selections. Handles string concatenation, nested statements, empty
 * statements, and tracks the selection arguments.
 *
 * A selection can be build from a string, factory methods like [column], or use [Builder] to build
 * complex nested selection with multiple operators. The Selection manages the `selection` and
 * `selectionArgs` passed into [android.content.ContentResolver.query].
 *
 * Example:
 * ```
 *   fromString("foo = 1")
 * ```
 *
 * expands into "(foo = 1)", {}
 *
 * ```
 *   column("foo").is("LIKE", "bar")
 * ```
 *
 * expands into "(foo LIKE ?)", {"bar"}
 *
 * ```
 *   builder()
 *     .and(
 *       fromString("foo = ?", "1").buildUpon()
 *       .or(column("bar").is("<", 2))
 *       .build())
 *     .and(not(column("baz").is("!= 3")))
 *     .build();
 * ```
 *
 * expands into "(((foo = ?) OR (bar < ?)) AND (NOT (baz != 3)))", {"1", "2"}
 */
class Selection private constructor(val selection: String, val selectionArgs: Array<String>) {

    fun isEmpty(): Boolean = selection.isEmpty()

    /**
     * @return a mutable builder that appends to the selection. The selection will be parenthesized
     *   before anything is appended to it.
     */
    fun buildUpon(): Builder = Builder(this)

    /** Helper class to build a selection based on condition upon a column. */
    class Column internal constructor(column: String) {

        private val column: String = Assert.isNotNull(column)

        /** Expands to "<column> <operator> ?" and add `value` to the arguments. */
        fun `is`(operator: String, value: Any): Selection =
            fromString(column + " " + Assert.isNotNull(operator) + " ?", value.toString())

        /**
         * Expands to "<column> <operator>". [is] should be used if the condition is comparing to a
         * string or a user input value, which must be sanitized.
         */
        fun `is`(condition: String): Selection =
            fromString(column + " " + Assert.isNotNull(condition))

        fun `in`(vararg values: String): Selection = `in`(values.asList())

        fun `in`(values: Collection<String>): Selection =
            fromString(
                column + " IN (" + TextUtils.join(",", Collections.nCopies(values.size, "?")) + ")",
                values,
            )
    }

    /** Builder for [Selection] */
    class Builder {

        private val selection = StringBuilder()
        private val selectionArgs: MutableList<String> = ArrayList()

        internal constructor()

        internal constructor(selection: String?, args: Collection<String>?) {
            if (selection == null) {
                return
            }
            checkArgsCount(selection, args)
            this.selection.append(parenthesized(selection))
            if (args != null) {
                selectionArgs.addAll(args)
            }
        }

        internal constructor(selection: Selection) {
            this.selection.append(selection.selection)
            Collections.addAll(selectionArgs, *selection.selectionArgs)
        }

        fun build(): Selection {
            if (selection.isEmpty()) {
                return Selection("", arrayOf())
            }
            return Selection(parenthesized(selection.toString()), selectionArgs.toTypedArray())
        }

        fun and(selection: Selection): Builder {
            if (selection.isEmpty()) {
                return this
            }

            if (this.selection.isNotEmpty()) {
                this.selection.append(" AND ")
            }
            this.selection.append(selection.selection)
            Collections.addAll(selectionArgs, *selection.selectionArgs)
            return this
        }

        fun or(selection: Selection): Builder {
            if (selection.isEmpty()) {
                return this
            }

            if (this.selection.isNotEmpty()) {
                this.selection.append(" OR ")
            }
            this.selection.append(selection.selection)
            Collections.addAll(selectionArgs, *selection.selectionArgs)
            return this
        }

        companion object {
            private fun checkArgsCount(selection: String, args: Collection<String>?) {
                var argsInSelection = 0
                for (i in selection.indices) {
                    if (selection[i] == '?') {
                        argsInSelection++
                    }
                }
                Assert.checkArgument(argsInSelection == (args?.size ?: 0))
            }
        }
    }

    companion object {
        /** @return a builder that is empty. */
        @JvmStatic fun builder(): Builder = Builder()

        /**
         * @return a Selection built from regular selection string/args pair. The result selection
         *   will be enclosed in a parenthesis.
         */
        @JvmStatic
        fun fromString(selection: String?, vararg args: String): Selection =
            Builder(selection, args.asList()).build()

        @JvmStatic
        fun fromString(selection: String?, args: Collection<String>?): Selection =
            Builder(selection, args).build()

        /** @return a selection that is negated */
        @JvmStatic
        fun not(selection: Selection): Selection {
            Assert.checkArgument(!selection.isEmpty())
            return fromString("NOT " + selection.selection, *selection.selectionArgs)
        }

        /**
         * Build a selection based on condition upon a column. is() should be called to complete the
         * selection.
         */
        @JvmStatic fun column(column: String): Column = Column(column)

        /**
         * Parenthesized the `string`. Will not parenthesized if `string` is empty or is already
         * parenthesized (top level parenthesis encloses the whole string).
         */
        private fun parenthesized(string: String): String {
            if (string.isEmpty()) {
                return ""
            }
            if (!string.startsWith("(")) {
                return "($string)"
            }
            var depth = 1
            for (i in 1 until string.length - 1) {
                when (string[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            // First '(' closed before the string has ended,need an additional level
                            // of nesting.
                            // For example "(A) AND (B)" should become "((A) AND (B))"
                            return "($string)"
                        }
                    }
                    else -> continue
                }
            }
            Assert.checkArgument(depth == 1)
            return string
        }
    }
}
