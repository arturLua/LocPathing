package com.example.locpathing

import android.annotation.SuppressLint
import android.app.Application
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

data class LocationUiState(
    val isLoading: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val errorMessage: String? = null,
    val timestamp: String? = null
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val geocoderRepository = GeocoderRepository(application)

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        viewModelScope.launch {
            _uiState.value = LocationUiState(isLoading = true)

            try {
                val location = getCurrentLocation()

                if (location == null) {
                    _uiState.value = LocationUiState(
                        errorMessage = "Não foi possível obter a localização. Verifique se o GPS está ativo."
                    )
                    return@launch
                }

                val address = geocoderRepository.getAddressFromLocation(
                    location.latitude,
                    location.longitude
                )

                _uiState.value = LocationUiState(
                    latitude  = location.latitude,
                    longitude = location.longitude,
                    address   = address,
                    timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                )

            } catch (e: Exception) {
                _uiState.value = LocationUiState(
                    errorMessage = "Erro: ${e.localizedMessage}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation() = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }

        cont.invokeOnCancellation { cts.cancel() }
    }
}