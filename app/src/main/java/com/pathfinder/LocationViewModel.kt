package com.example.pathfinder

import android.annotation.SuppressLint
import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Estado da UI
data class LocationUiState(
    val isLoading: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val errorMessage: String? = null
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        viewModelScope.launch {
            _uiState.value = LocationUiState(isLoading = true)

            try {
                // Obtém localização atual com alta precisão
                val location = suspendCancellableCoroutine { cont ->
                    val cts = CancellationTokenSource()
                    fusedLocationClient
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc -> cont.resume(loc) }
                        .addOnFailureListener { cont.resume(null) }

                    cont.invokeOnCancellation { cts.cancel() }
                }

                if (location == null) {
                    _uiState.value = LocationUiState(
                        errorMessage = "Não foi possível obter a localização. Verifique se o GPS está ativo."
                    )
                    return@launch
                }

                val lat = location.latitude
                val lng = location.longitude

                // Converte coordenadas em endereço via Geocoder
                val addressText = reverseGeocode(lat, lng)

                _uiState.value = LocationUiState(
                    latitude = lat,
                    longitude = lng,
                    address = addressText
                )

            } catch (e: Exception) {
                _uiState.value = LocationUiState(
                    errorMessage = "Erro: ${e.localizedMessage}"
                )
            }
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String {
        val geocoder = Geocoder(getApplication())

        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: usa callback assíncrono
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cont.resume(formatAddress(addresses))
                    }

                    override fun onError(errorMessage: String?) {
                        cont.resume("Endereço não encontrado")
                    }
                })
            } else {
                // API < 33: chamada síncrona (bloqueia a coroutine, ok pois já está em IO)
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

        addr.thoroughfare?.let { parts.add(it) }           // Rua
        addr.subThoroughfare?.let { parts.add("nº $it") }  // Número
        addr.subLocality?.let { parts.add(it) }            // Bairro
        addr.locality?.let { parts.add(it) }               // Cidade
        addr.adminArea?.let { parts.add(it) }              // Estado
        addr.countryName?.let { parts.add(it) }            // País
        addr.postalCode?.let { parts.add("CEP: $it") }     // CEP

        return parts.joinToString(", ")
    }
}