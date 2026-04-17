package com.example.locpathing

import android.annotation.SuppressLint
import android.app.Application
import android.location.*
import android.os.*
import androidx.lifecycle.*
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume


// Estado principal da UI
data class LocationUiState(
    val isLoading: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val errorMessage: String? = null,
    val timestamp: String? = null,
    val accuracy: Float? = null,
    val isMocked: Boolean = false,
    val locationUpdateId: Int = 0       // incrementa a cada fetch bem-sucedido
)

// Estado do receptor GNSS (atualizado de forma independente)
data class GnssInfo(
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val avgCno: Float = 0f
)

// Resultado tipado da obtenção de localização
sealed class LocationResult {
    data class Success(val location: Location) : LocationResult()
    data class Failure(val reason: String) : LocationResult()
}

class LocationViewModel(
    application: Application,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val geocoderRepository: GeocoderRepositoryContract
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LocationUiState())

    private val _gnssInfo = MutableStateFlow(GnssInfo())

    private val nmeaBuffer = ArrayDeque<String>(80)
    private val _nmeaLog   = MutableStateFlow<List<String>>(emptyList())
    val nmeaLog: StateFlow<List<String>> = _nmeaLog.asStateFlow()

    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()
    val gnssInfo: StateFlow<GnssInfo> = _gnssInfo.asStateFlow()

    private val locationManager =
        application.getSystemService(LocationManager::class.java)

    private val gnssThread = HandlerThread("GnssWorker").also { it.start() }
    private val gnssHandler = Handler(gnssThread.looper)
    private var gnssCallback: GnssStatus.Callback? = null
    private var nmeaListener: OnNmeaMessageListener? = null

    private val gnssStarted = AtomicBoolean(false)

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

// Fetch principal
@SuppressLint("MissingPermission")
fun fetchLocation() {
    viewModelScope.launch {

        // preserva dados anteriores, apenas sinaliza loading
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        if (gnssStarted.compareAndSet(false, true)) {
            startGnssMonitoring()
        }

        when (val result = getCurrentLocation()) {

            is LocationResult.Failure -> {
                // preserva dados e exibe o motivo real
                _uiState.update { it.copy(isLoading = false, errorMessage = result.reason) }
            }

            is LocationResult.Success -> {
                val location = result.location

                val address = runCatching {
                    geocoderRepository.getAddressFromLocation(
                        location.latitude,
                        location.longitude
                    )
                }.getOrElse { "Endereço não disponível" }

                val isMocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    location.isMock
                } else {
                    @Suppress("DEPRECATION")
                    location.isFromMockProvider
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address,
                        timestamp = timeFormatter.format(Date()),
                        accuracy = location.accuracy,
                        isMocked = isMocked,
                        errorMessage = null,
                        locationUpdateId = it.locationUpdateId + 1
                    )
                }
            }
        }
    }
}

// Registro assíncrono de GNSS e NMEA
@SuppressLint("MissingPermission")
private fun startGnssMonitoring() {
    gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            var totalCno = 0f
            var cnoCount = 0

            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) {
                    used++
                    totalCno += status.getCn0DbHz(i)
                    cnoCount++
                }
            }

            _gnssInfo.value = GnssInfo(
                satellitesUsed = used,
                satellitesVisible = status.satelliteCount,
                avgCno = if (cnoCount > 0) totalCno / cnoCount else 0f
            )
        }
    }
    locationManager?.registerGnssStatusCallback(gnssCallback!!, gnssHandler)

    nmeaListener = OnNmeaMessageListener { message, _ ->
        val sentence = message.trim()
        val relevant = sentence.startsWith("\$GPGGA") ||
                sentence.startsWith("\$GPRMC") ||
                sentence.startsWith("\$GNGGA") ||
                sentence.startsWith("\$GNRMC")

        if (relevant) {
            if (nmeaBuffer.size >= 80) nmeaBuffer.removeFirst()
            nmeaBuffer.addLast(sentence)
            _nmeaLog.value = nmeaBuffer.toList()
        }
    }
    locationManager?.addNmeaListener(nmeaListener!!, gnssHandler)
}

// Obtenção pontual com resultado tipado
@SuppressLint("MissingPermission")
private suspend fun getCurrentLocation(): LocationResult =
    suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    cont.resume(LocationResult.Success(location))
                } else {
                    cont.resume(
                        LocationResult.Failure(
                            "Não foi possível obter a localização. Verifique se o GPS está ativo."
                        )
                    )
                }
            }
            .addOnFailureListener { exception ->
                cont.resume(
                    LocationResult.Failure(
                        exception.localizedMessage
                            ?: "Erro desconhecido ao acessar o provedor de localização."
                    )
                )
            }

        cont.invokeOnCancellation { cts.cancel() }
    }

// Limpeza de recursos
override fun onCleared() {
    super.onCleared()
    gnssCallback?.let { locationManager?.unregisterGnssStatusCallback(it) }
    nmeaListener?.let { locationManager?.removeNmeaListener(it) }
    gnssThread.quitSafely()
}
}

// Factory - permite injetar dependências e mockar em testes
class LocationViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            "Factory só conhece LocationViewModel"
        }
        return LocationViewModel(
            application         = application,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(application),
            geocoderRepository  = GeocoderRepository(application)  // <- concreto só aqui
        ) as T
    }
}