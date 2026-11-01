/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.searchfragment.list;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Shortcuts offered under search results for the typed number, such as "Send SMS". */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
  SearchAction.INVALID,
  SearchAction.CREATE_NEW_CONTACT,
  SearchAction.ADD_TO_CONTACT,
  SearchAction.SEND_SMS,
  SearchAction.MAKE_VILTE_CALL,
  SearchAction.MAKE_VOICE_CALL
})
public @interface SearchAction {
  int INVALID = 0;
  int CREATE_NEW_CONTACT = 1;
  int ADD_TO_CONTACT = 2;
  int SEND_SMS = 3;
  int MAKE_VILTE_CALL = 4;
  int MAKE_VOICE_CALL = 5;
}
