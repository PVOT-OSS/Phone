/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Shows where the phone is during an emergency call, so the user can read it out: the address when
 * the phone can look it up, its Plus Code and its coordinates. Only displayed, never sent anywhere.
 */
class EmergencyLocationFragment : Fragment(), LocationListener {

    private var title by mutableIntStateOf(R.string.emergency_location_loading)
    private var address by mutableStateOf<String?>(null)
    private var plusCode by mutableStateOf<String?>(null)
    private var coordinates by mutableStateOf<String?>(null)
    private var listening = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PhoneScreenTheme(fillBackground = false) {
                    EmergencyLocationCard(stringResource(title), address, plusCode, coordinates)
                }
            }
        }

    override fun onStart() {
        super.onStart()
        val context = requireContext()
        when {
            batteryPercent(context) < BATTERY_THRESHOLD_PERCENT -> showError(R.string.emergency_location_battery_too_low)
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED -> showError(R.string.emergency_location_error)
            else -> startUpdates(context)
        }
    }

    override fun onStop() {
        super.onStop()
        if (listening) {
            requireContext().getSystemService(LocationManager::class.java)?.removeUpdates(this)
            listening = false
        }
    }

    @Suppress("MissingPermission") // Checked in onStart.
    private fun startUpdates(context: Context) {
        val locations = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { locations?.isProviderEnabled(it) == true }
        if (locations == null || provider == null) {
            showError(R.string.emergency_location_error)
            return
        }
        locations.getLastKnownLocation(provider)?.let(::onLocationChanged)
        val request = LocationRequest.Builder(UPDATE_INTERVAL_MILLIS).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
        locations.requestLocationUpdates(provider, request, ContextCompat.getMainExecutor(context), this)
        listening = true
    }

    override fun onLocationChanged(location: Location) {
        if (!isUsable(location) || !isAdded) return
        title = R.string.emergency_location_title
        plusCode = getString(R.string.emergency_location_plus_code, OpenLocationCode.encode(location.latitude, location.longitude))
        coordinates = getString(R.string.emergency_location_coordinates, location.latitude, location.longitude)
        lookUpAddress(location)
    }

    private fun lookUpAddress(location: Location) {
        if (!Geocoder.isPresent()) return
        val main: Executor = ContextCompat.getMainExecutor(requireContext())
        Geocoder(requireContext(), Locale.getDefault()).getFromLocation(
            location.latitude,
            location.longitude,
            1,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    val line = addresses.firstOrNull()?.getAddressLine(0) ?: return
                    main.execute { showAddress(line) }
                }

                override fun onError(errorMessage: String?) {
                    LogUtil.i("EmergencyLocationFragment.lookUpAddress", "no address: $errorMessage")
                }
            },
        )
    }

    /** The address on two lines, split at its first comma. */
    private fun showAddress(line: String) {
        if (!isAdded) return
        val comma = line.indexOf(',')
        address = if (comma < 0) line else line.substring(0, comma).trim() + "\n" + line.substring(comma + 1).trim()
    }

    private fun showError(message: Int) {
        title = message
        address = null
        plusCode = null
        coordinates = null
    }

    private companion object {
        const val BATTERY_THRESHOLD_PERCENT = 10
        const val UPDATE_INTERVAL_MILLIS = 30_000L
        const val MAX_AGE_MILLIS = 60_000L
        const val MAX_ACCURACY_METERS = 250f

        /** A fix is shown only when it is recent, close enough and real. */
        fun isUsable(location: Location): Boolean {
            val ageMillis = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
            return ageMillis <= MAX_AGE_MILLIS &&
                location.hasAccuracy() && location.accuracy <= MAX_ACCURACY_METERS &&
                !location.isMock
        }

        fun batteryPercent(context: Context): Int =
            context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
    }
}

/** Where the phone is, for the user to read out to the operator; the codes can be selected and copied. */
@Composable
private fun EmergencyLocationCard(title: String, address: String?, plusCode: String?, coordinates: String?) {
    PvotCard(Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L).testTag(TestTags.EMERGENCY_LOCATION)) {
        SelectionContainer {
            Column(Modifier.padding(PvotSpacing.L), verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
                for (line in listOfNotNull(plusCode, coordinates)) {
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
