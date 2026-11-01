/*
 * Copyright (C) 2016 The Android Open Source Project
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

package org.prauga.phone.dialer.common

import androidx.annotation.CheckResult
import androidx.fragment.app.Fragment
import org.prauga.phone.dialer.main.MainActivityPeer

/** Utility methods for working with Fragments */
object FragmentUtils {

    /**
     * Returns an instance of the `callbackInterface` that is defined in the parent of the
     * `fragment`, or null if no such call back can be found.
     */
    @JvmStatic
    @CheckResult(suggest = "#checkParent(Fragment, Class)}")
    fun <T : Any> getParent(fragment: Fragment, callbackInterface: Class<T>): T? {
        val parentFragment = fragment.parentFragment
        val activity = fragment.activity
        if (callbackInterface.isInstance(parentFragment)) {
            // Casts are checked using runtime methods
            @Suppress("UNCHECKED_CAST")
            return parentFragment as T
        } else if (callbackInterface.isInstance(activity)) {
            // Casts are checked using runtime methods
            @Suppress("UNCHECKED_CAST")
            return activity as T
        } else if (activity is FragmentUtilListener) {
            return activity.getImpl(callbackInterface)
        } else if (activity is MainActivityPeer.PeerSupplier) {
            val peer = activity.peer
            if (peer is FragmentUtilListener) {
                return peer.getImpl(callbackInterface)
            }
        }
        return null
    }

    /** Returns the parent or throws. Should perform check elsewhere(e.g. onAttach, newInstance). */
    @JvmStatic
    fun <T : Any> getParentUnsafe(fragment: Fragment, callbackInterface: Class<T>): T =
        Assert.isNotNull(getParent(fragment, callbackInterface))

    /**
     * Ensures fragment has a parent that implements the corresponding interface
     *
     * @param frag The Fragment whose parents are to be checked
     * @param callbackInterface The interface class that a parent should implement
     * @throws IllegalStateException if no parents are found that implement callbackInterface
     */
    @JvmStatic
    @Throws(IllegalStateException::class)
    fun checkParent(frag: Fragment, callbackInterface: Class<*>) {
        if (getParent(frag, callbackInterface) == null) {
            val parentFragment = frag.parentFragment
            val parent =
                if (parentFragment == null) {
                    frag.requireActivity().javaClass.name
                } else {
                    parentFragment.javaClass.name
                }
            throw IllegalStateException(
                frag.javaClass.name +
                    " must be added to a parent" +
                    " that implements " +
                    callbackInterface.name +
                    ". Instead found " +
                    parent
            )
        }
    }

    /** Useful interface for activities that don't want to implement arbitrary listeners. */
    interface FragmentUtilListener {

        /** Returns an implementation of T if parent has one, otherwise null. */
        fun <T : Any> getImpl(callbackInterface: Class<T>): T?
    }
}
