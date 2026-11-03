/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.car

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.CallLog.Calls
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.Executors
import org.prauga.phone.dialer.R

/** The app on a car's screen through Android Auto: favourites, recent calls and contacts to call. */
class PhoneCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = PhoneCarScreen(carContext)
    }
}

/** Someone to call from the car. */
internal data class CarEntry(val title: String, val detail: String?, val number: String)

/** Tabs of people to call; picking one places the call, which the car then shows. */
private class PhoneCarScreen(carContext: CarContext) : Screen(carContext) {

    private var activeTab = TAB_RECENTS
    private var entries: List<CarEntry>? = null
    private val loader = Executors.newSingleThreadExecutor()

    /** The tab a load is running for, so a redraw while it runs does not start another. */
    private var loadingTab: String? = null

    init {
        lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) loader.shutdownNow() },
        )
    }

    override fun onGetTemplate(): Template {
        val list = entries
        if (list == null && loadingTab != activeTab) load(activeTab)
        val content = ListTemplate.Builder().apply {
            if (list == null) {
                setLoading(true)
            } else {
                setSingleList(
                    ItemList.Builder().apply {
                        setNoItemsMessage(carContext.getString(R.string.car_no_entries))
                        list.take(MAX_ROWS).forEach { entry ->
                            addItem(
                                Row.Builder()
                                    .setTitle(entry.title)
                                    .apply { entry.detail?.let(::addText) }
                                    .setOnClickListener { call(entry.number) }
                                    .build(),
                            )
                        }
                    }.build(),
                )
            }
        }.build()

        // Tabs need a newer car host; older ones get the recent calls on their own.
        if (carContext.carAppApiLevel < TABS_API_LEVEL) {
            return ListTemplate.Builder().apply {
                if (list == null) setLoading(true) else setSingleList(content.singleList!!)
                setTitle(carContext.getString(R.string.tab_title_call_history))
                setHeaderAction(Action.APP_ICON)
            }.build()
        }
        return TabTemplate.Builder(
            object : TabTemplate.TabCallback {
                override fun onTabSelected(tabContentId: String) {
                    activeTab = tabContentId
                    entries = null
                    invalidate()
                }
            },
        ).apply {
            setHeaderAction(Action.APP_ICON)
            addTab(tab(TAB_FAVORITES, R.string.quick_dial_header, R.drawable.ic_star))
            addTab(tab(TAB_RECENTS, R.string.tab_title_call_history, R.drawable.ic_history))
            addTab(tab(TAB_CONTACTS, R.string.tab_all_contacts, R.drawable.ic_users))
            setActiveTabContentId(activeTab)
            setTabContents(TabContents.Builder(content).build())
        }.build()
    }

    private fun tab(id: String, title: Int, icon: Int): Tab = Tab.Builder()
        .setContentId(id)
        .setTitle(carContext.getString(title))
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
        .build()

    private fun load(tab: String) {
        loadingTab = tab
        loader.execute {
            val loaded = runCatching {
                when (tab) {
                    TAB_FAVORITES -> CarCallLists.contacts(carContext, starredOnly = true)
                    TAB_CONTACTS -> CarCallLists.contacts(carContext, starredOnly = false)
                    else -> CarCallLists.recents(carContext)
                }
            }.getOrDefault(emptyList())
            carContext.mainExecutor.execute {
                if (loadingTab == tab) loadingTab = null
                if (tab == activeTab) {
                    entries = loaded
                    invalidate()
                }
            }
        }
    }

    private fun call(number: String) {
        try {
            carContext.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), null)
        } catch (e: SecurityException) {
            CarToast.makeText(carContext, R.string.car_cannot_call, CarToast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val TAB_FAVORITES = "favorites"
        const val TAB_RECENTS = "recents"
        const val TAB_CONTACTS = "contacts"
        const val TABS_API_LEVEL = 6
        const val MAX_ROWS = CarCallLists.MAX_ROWS
    }
}

/** The people the car screen lists, newest call or alphabetical first. */
internal object CarCallLists {

    const val MAX_ROWS = 50

    fun recents(context: Context): List<CarEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<CarEntry>()
        context.contentResolver.query(
            Calls.CONTENT_URI,
            arrayOf(Calls.NUMBER, Calls.CACHED_NAME, Calls.CACHED_NORMALIZED_NUMBER),
            "${Calls.NUMBER} IS NOT NULL AND ${Calls.NUMBER} != '' AND ${Calls.TYPE} != ${Calls.VOICEMAIL_TYPE}",
            null,
            "${Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < MAX_ROWS) {
                val number = c.getString(0) ?: continue
                if (!seen.add(c.getString(2)?.takeIf { it.isNotEmpty() } ?: number)) continue
                val formatted = PhoneNumberUtils.formatNumber(number, null) ?: number
                val name = c.getString(1)?.takeIf { it.isNotBlank() }
                out += CarEntry(name ?: formatted, if (name != null) formatted else null, number)
            }
        }
        return out
    }

    fun contacts(context: Context, starredOnly: Boolean): List<CarEntry> {
        val out = ArrayList<CarEntry>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            if (starredOnly) "${Phone.STARRED} = 1" else null,
            null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < MAX_ROWS) {
                val number = c.getString(1) ?: continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString()
                out += CarEntry(c.getString(0) ?: number, "$label · ${PhoneNumberUtils.formatNumber(number, null) ?: number}", number)
            }
        }
        return out
    }
}
