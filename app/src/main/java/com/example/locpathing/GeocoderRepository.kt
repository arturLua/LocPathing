package com.example.locpathing

import android.content.Context
import android.location.*
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class GeocoderRepository(private val context: Context) : GeocoderRepositoryContract {
    override suspend fun getAddressFromLocation(lat: Double, lng: Double): String =
        withTimeout(5_000L) {
            val geocoder = Geocoder(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(formatAddress(addresses))
                        }
                        override fun onError(errorMessage: String?) {
                            cont.resume("Endereço não encontrado")
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = runCatching {
                        geocoder.getFromLocation(lat, lng, 1)
                    }.getOrNull()
                    formatAddress(addresses ?: emptyList())
                }
            }
        }

    private fun formatAddress(addresses: List<Address>): String {
        if (addresses.isEmpty()) return "Endereço não disponível"

        val addr = addresses[0]
        val parts = mutableListOf<String>()

        addr.thoroughfare?.let    { parts.add(it) }
        addr.subThoroughfare?.let { parts.add("nº $it") }
        addr.subLocality?.let     { parts.add(it) }
        addr.locality?.let        { parts.add(it) }
        addr.adminArea?.let       { parts.add(it) }
        addr.countryName?.let     { parts.add(it) }
        addr.postalCode?.let      { parts.add("CEP: $it") }

        return parts.joinToString(", ")
    }
}