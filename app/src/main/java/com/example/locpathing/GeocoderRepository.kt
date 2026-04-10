package com.example.locpathing

import android.content.Context
import android.location.*
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GeocoderRepository(private val context: Context) {

    suspend fun getAddressFromLocation(lat: Double, lng: Double): String {
        val geocoder = Geocoder(context)

        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cont.resume(formatAddress(addresses))
                    }
                    override fun onError(errorMessage: String?) {
                        cont.resume("Endereço não encontrado")
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                cont.resume(formatAddress(addresses ?: emptyList()))
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