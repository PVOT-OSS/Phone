/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

/** Compose test tags, exposed as resource ids so UiAutomator tests can find them with `By.res`. */
object TestTags {
    const val SETTINGS_LIST = "settings_list"
    const val APPEARANCE_GLASS_SWITCH = "appearance_glass_switch"
    const val APPEARANCE_GLASS_SLIDER = "appearance_glass_slider"
    const val APPEARANCE_GLASS_PREVIEW = "appearance_glass_preview"
    const val SETTINGS_TOP_BAR = "settings_top_bar"
    const val HELPLINE_LIST = "helpline_list"
    const val CALL_STATS_DETAIL = "call_stats_detail"
    const val CALL_STATS_CALL = "call_stats_call"
    const val CALL_DETAILS = "call_details"
    const val CALL_DETAILS_CALL_BACK = "call_details_call_back"
    const val CALL_DETAILS_DELETE = "call_details_delete"
    const val CALL_DETAILS_MESSAGE = "call_details_message"
    const val CALL_DETAILS_AVATAR = "call_details_avatar"
    const val CALL_SUBJECT_FIELD = "call_subject_field"
    const val CALL_SUBJECT_HISTORY = "call_subject_history"
    const val CALL_SUBJECT_SEND = "call_subject_send"
    const val MAIN_NAV_BAR = "main_nav_bar"
    const val MAIN_FAB = "main_fab"
    const val CONTACTS_LIST = "contacts_list"
    const val CONTACTS_ADD = "contacts_add"
    const val SPEED_DIAL_GRID = "speed_dial_grid"
    const val CALL_HISTORY_LIST = "call_history_list"
    const val CALL_HISTORY_TABS = "call_history_tabs"
    const val CALL_HISTORY_SELECTION = "call_history_selection"
    const val VOICEMAIL_PLAYBACK = "voicemail_playback"
    const val VOICEMAIL_PLAY = "voicemail_play"
    const val VOICEMAIL_STATUS = "voicemail_status"
    const val SEARCH_LIST = "search_list"
    const val CALL_STATS_LIST = "call_stats_list"
    const val CALL_STATS_SORT = "call_stats_sort"
    const val CALL_STATS_RANGE = "call_stats_range"
    const val SPEED_DIAL_ADD = "speed_dial_add"
    const val SPEED_DIAL_REMOVE_TARGET = "speed_dial_remove_target"
    const val SPEED_DIAL_MENU_REMOVE = "speed_dial_menu_remove"
    const val SPEED_DIAL_MENU_STAR = "speed_dial_menu_star"

    /** The calling account [id] in the account chooser. */
    fun callingAccount(id: String) = "calling_account_$id"

    /** The history row for the call log entry [callId]. */
    fun callDetailsEntry(callId: Long) = "call_details_entry_$callId"

    /** The play recordings button of the call log entry [callId]. */
    fun callDetailsRecordings(callId: Long) = "call_details_recordings_$callId"

    /** The call button of the helpline with [number]. */
    fun helplineCall(number: String) = "helpline_call_$number"

    /** The row for a setting, by its preference key or screen name. */
    fun settingsRow(key: String) = "settings_row_$key"

    /** An option in a setting's choice sheet, by its stored value. */
    fun settingsOption(value: String) = "settings_option_$value"

    /** The row of the contact with [id] in the contacts tab. */
    fun contactRow(id: Long) = "contact_$id"

    /** A starred contact in the favorites grid. */
    fun speedDialFavorite(contactId: Long) = "speed_dial_favorite_$contactId"

    /** A frequent contact under the favorites. */
    fun speedDialSuggestion(contactId: Long) = "speed_dial_suggestion_$contactId"

    /** The more options button of a frequent contact. */
    fun speedDialSuggestionMore(contactId: Long) = "speed_dial_suggestion_more_$contactId"

    /** A number in the speed dial number chooser. */
    fun speedDialChannel(number: String, video: Boolean) = "speed_dial_channel_${number}_$video"

    /** A row of the call history, by its newest call's id. */
    fun callHistoryRow(rowId: Long) = "call_history_row_$rowId"

    /** The call back or play button at the end of a call history row. */
    fun callHistoryPrimary(rowId: Long) = "call_history_primary_$rowId"

    /** The actions of an opened call history row. */
    fun callHistoryActions(rowId: Long) = "call_history_actions_$rowId"

    /** One action of an opened call history row, by its [RowAction] name. */
    fun callHistoryAction(rowId: Long, action: String) = "call_history_action_${rowId}_$action"

    /** A number in the call statistics. */
    fun callStatsRow(number: String) = "call_stats_row_$number"

    /** A number in the search results, by its row key. */
    fun searchRow(key: String) = "search_row_$key"

    /** A shortcut under the search results, by its SearchAction value. */
    fun searchAction(action: Int) = "search_action_$action"

    /** The dialpad, with its digits, keys, delete and call buttons. */
    const val DIALPAD = "dialpad"
    const val DIALPAD_DIGITS = "dialpad_digits"
    const val DIALPAD_MENU = "dialpad_menu"
    const val DIALPAD_DELETE = "dialpad_delete"
    const val DIALPAD_CALL = "dialpad_call"

    /** A dialpad key, by its digit or symbol. */
    fun dialpadKey(number: String) = "dialpad_key_$number"

    /** The main toolbar: the collapsed search bar, its buttons and the expanded field. */
    const val SEARCH_BAR = "search_bar"
    const val SEARCH_VOICE = "search_voice"
    const val SEARCH_MENU = "search_menu"
    const val SEARCH_BACK = "search_back"
    const val SEARCH_FIELD = "search_field"

    /** A route in the audio route picker, by its CallAudioState route and device name. */
    fun audioRoute(route: Int, label: String?) = "audio_route_${route}_${label.orEmpty()}"

    /** The list of people in a conference call. */
    const val CONFERENCE_LIST = "conference_list"

    /** A person in a conference call, by their call's id. */
    fun conferenceParticipant(callId: String) = "conference_participant_$callId"

    /** The messages of a saved RTT call. */
    const val RTT_TRANSCRIPT = "rtt_transcript"

    /** The banner for the call on hold, on the in-call screens. */
    const val ON_HOLD_BANNER = "on_hold_banner"

    /** The RTT call screen: the chat, its input, and the header's buttons. */
    const val RTT_CHAT = "rtt_chat"
    const val RTT_INPUT = "rtt_input"
    const val RTT_SUBMIT = "rtt_submit"
    const val RTT_MENU = "rtt_menu"
    const val RTT_END_CALL = "rtt_end_call"
    const val RTT_STATUS_BANNER = "rtt_status_banner"

    /** The dialpad shown during a call, and its close button. */
    const val INCALL_DIALPAD = "incall_dialpad"
    const val INCALL_DIALPAD_BACK = "incall_dialpad_back"

    /** The call screen: who the call is with, its buttons and end call. */
    const val CONTACT_GRID = "contact_grid"
    const val CONTACT_GRID_NAME = "contact_grid_name"
    const val CONTACT_GRID_BOTTOM = "contact_grid_bottom"
    const val INCALL_END_CALL = "incall_end_call"

    /** A slot of the in-call button grid, from 0. */
    fun incallButton(slot: Int) = "incall_button_$slot"

    /** The incoming call screen: the swipe button, the two plain buttons and the side buttons. */
    const val ANSWER_PUCK = "answer_puck"
    const val ANSWER_ACCEPT = "answer_accept"
    const val ANSWER_DECLINE = "answer_decline"
    const val ANSWER_SECONDARY = "answer_secondary"
    const val ANSWER_AND_RELEASE = "answer_and_release"

    /** The field for writing your own reply when declining a call. */
    const val CUSTOM_SMS_INPUT = "custom_sms_input"

    // First-run onboarding of the user build.
    const val ONBOARDING = "onboarding"
    const val ONBOARDING_PRIMARY = "onboarding_primary"
    const val ONBOARDING_SETTINGS = "onboarding_settings"
    const val ONBOARDING_NOT_NOW = "onboarding_not_now"

    /** The onboarding row explaining the permission group [name]. */
    fun onboardingPermission(name: String) = "onboarding_permission_$name"
}
