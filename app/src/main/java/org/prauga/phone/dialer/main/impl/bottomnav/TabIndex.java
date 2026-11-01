/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Index for each tab in the main screen's nav bar. The values are stored as the last tab. */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
  TabIndex.NONE,
  TabIndex.DIALPAD,
  TabIndex.CALL_LOG,
  TabIndex.CONTACTS,
  TabIndex.VOICEMAIL,
})
public @interface TabIndex {
  int NONE = -1;
  int DIALPAD = 0;
  int CALL_LOG = 1;
  int CONTACTS = 2;
  int VOICEMAIL = 3;
}
