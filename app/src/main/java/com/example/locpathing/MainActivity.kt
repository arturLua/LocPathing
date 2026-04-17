package com.example.locpathing

import android.Manifest
import android.content.*
import android.os.Bundle
import androidx.activity.*
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locpathing.ui.theme.*
import kotlinx.coroutines.launch
import android.app.Application

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocPathingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// DEPOIS
@Composable
fun LocationScreen(
    viewModel: LocationViewModel = viewModel(
        factory = LocationViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val gnssInfo by viewModel.gnssInfo.collectAsState()
    val nmeaLog by viewModel.nmeaLog.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showLocationFound by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }
    var showNmeaSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.fetchLocation()
        else showPermissionDialog = true
    }

    // overlay fade in dos dados
    LaunchedEffect(uiState.locationUpdateId) {
        if (uiState.locationUpdateId == 0) return@LaunchedEffect

        // cancela qualquer sequência anterior automaticamente
        // (LaunchedEffect recompõe e reinicia o bloco a cada novo ID)
        showData = false
        showLocationFound = true
        kotlinx.coroutines.delay(2800)
        showLocationFound = false
        kotlinx.coroutines.delay(400)
        showData = true
    }

// Estado de reset separado: se erro ou loading, esconde dados
    LaunchedEffect(uiState.isLoading, uiState.errorMessage) {
        if (uiState.isLoading || uiState.errorMessage != null) {
            showData = false
            showLocationFound = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Ícone de localização",
                        tint = AccentRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text         = "LocPathing",
                        fontSize     = 22.sp,
                        fontWeight   = FontWeight.ExtraBold,
                        color        = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 1.sp
                    )
                }

                // Botão debug - aparece discretamente no canto
                TextButton(
                    onClick            = { showNmeaSheet = true },
                    contentPadding     = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text          = ">_",
                        fontSize      = 15.sp,
                        fontFamily    = FontFamily.Monospace,
                        color         = TextSecondary.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = SurfaceLine)
            Spacer(Modifier.height(28.dp))

            // Conteúdo principal
            when {
                uiState.isLoading -> {
                    Spacer(Modifier.weight(1f))
                    RadarCanvas(isScanning = true)
                    Spacer(Modifier.weight(1f))
                }

                uiState.errorMessage != null -> {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = uiState.errorMessage ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.weight(1f))
                }

                showData && uiState.latitude != null -> {
                    AnimatedVisibility(
                        visible = showData,
                        enter = fadeIn(tween(600)) + slideInVertically(
                            tween(600, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        LocationDataDisplay(
                            uiState = uiState,
                            gnssInfo = gnssInfo,
                            onCopy = {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Endereço", uiState.address ?: "")
                                )
                                scope.launch {
                                    snackbarHostState.showSnackbar("Endereço copiado!")
                                }
                            }
                        )
                    }
                }

                else -> {
                    Spacer(Modifier.weight(1f))
                    RadarCanvas(isScanning = false)
                    Spacer(Modifier.weight(1f))
                }
            }

            // Botão
            HorizontalDivider(color = SurfaceLine)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = Background
                ),
                enabled = !uiState.isLoading
            ) {
                Text(
                    text = when {
                        uiState.isLoading -> "localizando..."
                        uiState.latitude != null -> "atualizar localização"
                        else -> "obter localização"
                    },
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Overlays e dialogs
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permissão necessária") },
                text = {
                    Text("O acesso à localização foi negado. Ative nas configurações em Aplicativos > LocPathing > Permissões.")
                },
                confirmButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("OK", color = AccentTeal)
                    }
                },
                containerColor = SurfaceLine
            )
        }

        AnimatedVisibility(
            visible = showLocationFound,
            enter = fadeIn(tween(350)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 0.75f),
            exit = fadeOut(tween(500)) + scaleOut(tween(500), targetScale = 1.05f)
        ) {
            LocationFoundOverlay()
        }

        // NMEA Bottom Sheet
        if (showNmeaSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNmeaSheet = false },
                sheetState = sheetState,
                containerColor = Background,
                dragHandle = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 3.dp)
                                .background(SurfaceLine, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NMEA DEBUG LOG",
                                fontSize = 10.sp,
                                letterSpacing = 3.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentTeal.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${nmeaLog.size} linhas",
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = SurfaceLine
                        )
                    }
                }
            ) {
                NmeaLogContent(nmeaLog = nmeaLog)
            }
        }
    }
}

// Log NMEA - terminal performático
@Composable
fun NmeaLogContent(nmeaLog: List<String>) {
    val listState = rememberLazyListState()

    // Auto-scroll para a última linha a cada nova mensagem
    LaunchedEffect(nmeaLog.size) {
        if (nmeaLog.isNotEmpty()) {
            listState.scrollToItem(nmeaLog.size - 1)
        }
    }

    if (nmeaLog.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "aguardando sentenças NMEA...",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = TextSecondary.copy(alpha = 0.4f)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(nmeaLog) { sentence ->
                // Colore tipo de sentença diferente do conteúdo
                val typeEnd = sentence.indexOf(',').takeIf { it > 0 } ?: sentence.length
                val typeLabel = sentence.substring(0, typeEnd)
                val content = sentence.substring(typeEnd)
                val labelColor = if (sentence.contains("GGA")) AccentTeal.copy(alpha = 0.9f)
                else AccentTeal.copy(alpha = 0.55f)

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = typeLabel,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = labelColor
                    )
                    Text(
                        text = content,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

// Dados de localização
@Composable
fun LocationDataDisplay(
    uiState: LocationUiState,
    gnssInfo: GnssInfo,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Timestamp
        uiState.timestamp?.let {
            Text(
                text = "SINAL OBTIDO ÀS $it",
                fontSize = 9.sp,
                letterSpacing = 3.sp,
                color = AccentTeal.copy(alpha = 0.7f)
            )
        }

        // Coordenadas
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CoordBlock("LATITUDE", "%.6f°".format(uiState.latitude))
            HorizontalDivider(color = SurfaceLine)
            CoordBlock("LONGITUDE", "%.6f°".format(uiState.longitude))
            HorizontalDivider(color = SurfaceLine)

            // Linha GNSS compacta - uma única linha, sem ícones
            GnssStatusRow(uiState = uiState, gnssInfo = gnssInfo)
        }

        // Endereço
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "ENDEREÇO",
                fontSize = 9.sp,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = uiState.address ?: "Não disponível",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 24.sp
            )
            TextButton(
                onClick = onCopy,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "[ copiar endereço ]",
                    fontSize = 11.sp,
                    color = AccentTeal,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// Linha GNSS compacta com spoofing integrado
@Composable
fun GnssStatusRow(uiState: LocationUiState, gnssInfo: GnssInfo) {
    // Cor da precisão muda para vermelho se mock detectado
    val accuracyColor = if (uiState.isMocked) AccentRed else AccentTeal

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Satélites fixados / visíveis
        Text(
            text = "SAT  ${gnssInfo.satellitesUsed} / ${gnssInfo.satellitesVisible}",
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Força do sinal
            if (gnssInfo.avgCno > 0f) {
                Text(
                    text = "C/N0  ${"%.1f".format(gnssInfo.avgCno)} dB",
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            // Precisão: vermelho se mocked, teal se real
            uiState.accuracy?.let {
                Text(
                    text = if (uiState.isMocked) "⚠ MOCK" else "ACC  ${"%.1f".format(it)}m",
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    color = accuracyColor
                )
            }
        }
    }
}

// Bloco de coordenada individual
@Composable
fun CoordBlock(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            color = AccentTeal
        )
    }
}

// Tela idle
@Composable
fun RadarCanvas(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    // Animações condicionais por modo
    val rotationDuration = if (isScanning) 10000 else 12000
    val rotRevDuration = if (isScanning) 6000  else 8000
    val pulseDuration = if (isScanning) 500   else 1200
    val pulseTarget = if (isScanning) 8f    else 7f
    val textDuration = if (isScanning) 900   else 1500
    val majorTickAlpha = if (isScanning) 0.9f  else 0.8f
    val arcAlpha = if (isScanning) 0.55f else 0.5f
    val cornerAlpha = if (isScanning) 1.0f  else 0.9f
    val label = if (isScanning) "ESCANEANDO..." else "AGUARDANDO SINAL"

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(rotationDuration, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    val rotationReverse by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(rotRevDuration, easing = LinearEasing), RepeatMode.Restart),
        label = "rotationReverse"
    )
    val centerPulse by infiniteTransition.animateFloat(
        initialValue = 3f, targetValue = pulseTarget,
        animationSpec = infiniteRepeatable(tween(pulseDuration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "centerPulse"
    )
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(textDuration, easing = LinearEasing), RepeatMode.Reverse),
        label = "textAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {

            // Camada 1: anel externo com ticks
            Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
                val cx = size.width / 2; val cy = size.height / 2
                val rOuter = size.minDimension / 2f
                drawCircle(color = AccentTeal.copy(alpha = 0.2f), radius = rOuter, style = Stroke(width = 1f))
                for (i in 0 until 36) {
                    val angle   = Math.toRadians(i * 10.0)
                    val isMajor = i % 9 == 0
                    val tickLen = if (isMajor) 14f else 6f
                    drawLine(
                        color = AccentTeal.copy(alpha = if (isMajor) majorTickAlpha else 0.3f),
                        start = Offset(
                            cx + (rOuter - tickLen) * kotlin.math.cos(angle).toFloat(),
                            cy + (rOuter - tickLen) * kotlin.math.sin(angle).toFloat()
                        ),
                        end = Offset(
                            cx + rOuter * kotlin.math.cos(angle).toFloat(),
                            cy + rOuter * kotlin.math.sin(angle).toFloat()
                        ),
                        strokeWidth = if (isMajor) 2f else 1f
                    )
                }
            }

            // Camada 2: anel médio com arcos e cantos
            Canvas(modifier = Modifier.size(148.dp).rotate(rotationReverse)) {
                val cx = size.width / 2; val cy = size.height / 2
                val rMid = size.minDimension / 2f
                for (i in 0 until 4) {
                    drawArc(
                        color = AccentTeal.copy(alpha = arcAlpha),
                        startAngle = i * 90f - 35f, sweepAngle = 70f,
                        useCenter = false, style = Stroke(width = 1.5f),
                        topLeft = Offset(cx - rMid, cy - rMid),
                        size = androidx.compose.ui.geometry.Size(rMid * 2, rMid * 2)
                    )
                }
                val cornerR = rMid * 0.72f; val cornerLen = 18f
                listOf(-1f, 1f).forEach { sx ->
                    listOf(-1f, 1f).forEach { sy ->
                        drawLine(AccentTeal.copy(alpha = cornerAlpha),
                            Offset(cx + sx * cornerR, cy + sy * cornerR - sy * cornerLen),
                            Offset(cx + sx * cornerR, cy + sy * cornerR), strokeWidth = 2f)
                        drawLine(AccentTeal.copy(alpha = cornerAlpha),
                            Offset(cx + sx * cornerR - sx * cornerLen, cy + sy * cornerR),
                            Offset(cx + sx * cornerR, cy + sy * cornerR), strokeWidth = 2f)
                    }
                }
            }

            // Camada 3: conteúdo interno que varia por modo
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2; val cy = size.height / 2
                val rSweep = size.minDimension / 2f - 4f
                val gap = 22f; val lineLen = 20f
                val mira = AccentTeal.copy(alpha = 0.5f)

                if (isScanning) {
                    // Círculos internos + crosshair lines (scanning)
                    listOf(0.35f, 0.65f).forEach { f ->
                        drawCircle(color = AccentTeal.copy(alpha = 0.08f), radius = rSweep * f, style = Stroke(width = 1f))
                    }
                    drawLine(AccentTeal.copy(alpha = 0.06f), Offset(cx - rSweep, cy), Offset(cx + rSweep, cy), strokeWidth = 1f)
                    drawLine(AccentTeal.copy(alpha = 0.06f), Offset(cx, cy - rSweep), Offset(cx, cy + rSweep), strokeWidth = 1f)

                    // Trail do sweep
                    val trailLength = 120f; val steps = 30
                    for (i in 0..steps) {
                        val fraction = i / steps.toFloat()
                        drawArc(
                            color = AccentTeal.copy(alpha = fraction * 0.22f),
                            startAngle = sweepAngle - trailLength * (1f - fraction),
                            sweepAngle = trailLength / steps, useCenter = true,
                            topLeft = Offset(cx - rSweep, cy - rSweep),
                            size = androidx.compose.ui.geometry.Size(rSweep * 2, rSweep * 2)
                        )
                    }

                    // Linha do sweep
                    val sweepRad  = Math.toRadians(sweepAngle.toDouble())
                    val sweepEndX = cx + rSweep * kotlin.math.cos(sweepRad).toFloat()
                    val sweepEndY = cy + rSweep * kotlin.math.sin(sweepRad).toFloat()
                    drawLine(AccentTeal.copy(alpha = 0.3f),  Offset(cx, cy), Offset(sweepEndX, sweepEndY), strokeWidth = 4f)
                    drawLine(AccentTeal.copy(alpha = 0.95f), Offset(cx, cy), Offset(sweepEndX, sweepEndY), strokeWidth = 1.2f)

                    // Centro: pulse preenchido + halo
                    drawCircle(color = AccentRed.copy(alpha = 0.25f), radius = centerPulse * 3f)
                    drawCircle(color = AccentRed, radius = centerPulse)
                } else {
                    // Círculo estático idle
                    drawCircle(color = AccentTeal.copy(alpha = 0.15f), radius = 30f, style = Stroke(width = 1f))

                    // Centro: pulse + halo stroke
                    drawCircle(color = AccentRed, radius = centerPulse)
                    drawCircle(color = AccentRed.copy(alpha = 0.3f), radius = centerPulse * 2.5f, style = Stroke(width = 1f))
                }

                // Mira comum aos dois modos
                drawLine(mira, Offset(cx, cy - gap),      Offset(cx, cy - gap - lineLen),      strokeWidth = 1.5f)
                drawLine(mira, Offset(cx, cy + gap),      Offset(cx, cy + gap + lineLen),      strokeWidth = 1.5f)
                drawLine(mira, Offset(cx - gap, cy),      Offset(cx - gap - lineLen, cy),      strokeWidth = 1.5f)
                drawLine(mira, Offset(cx + gap, cy),      Offset(cx + gap + lineLen, cy),      strokeWidth = 1.5f)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(text = label, fontSize = 10.sp, letterSpacing = 4.sp, color = AccentTeal.copy(alpha = textAlpha))
        Spacer(Modifier.height(6.dp))
    }
}

// Overlay localização encontrada
@Composable
fun LocationFoundOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "found")

    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringAlpha"
    )
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "textAlpha"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(Background.copy(alpha = 0.75f)))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2; val cy = size.height / 2; val baseR = size.minDimension / 3.5f
                    drawCircle(color = AccentTeal.copy(alpha = ringAlpha * 0.5f), radius = baseR * ringScale * 1.4f, style = Stroke(width = 1f))
                    drawCircle(color = AccentTeal.copy(alpha = ringAlpha * 0.8f), radius = baseR * ringScale, style = Stroke(width = 1.5f))
                    drawCircle(color = AccentTeal.copy(alpha = 0.15f), radius = baseR * 0.85f)
                    drawCircle(color = AccentTeal.copy(alpha = 0.6f), radius = baseR * 0.85f, style = Stroke(width = 1.5f))
                    val cornerR = baseR * 1.25f; val cornerLen = 14f
                    listOf(-1f, 1f).forEach { sx -> listOf(-1f, 1f).forEach { sy ->
                        drawLine(AccentTeal.copy(alpha = 0.9f), Offset(cx + sx * cornerR, cy + sy * cornerR - sy * cornerLen), Offset(cx + sx * cornerR, cy + sy * cornerR), strokeWidth = 2f)
                        drawLine(AccentTeal.copy(alpha = 0.9f), Offset(cx + sx * cornerR - sx * cornerLen, cy + sy * cornerR), Offset(cx + sx * cornerR, cy + sy * cornerR), strokeWidth = 2f)
                    }}
                }
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = AccentRed, modifier = Modifier.size(32.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(text = "SINAL OBTIDO", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, color = AccentTeal.copy(alpha = textAlpha))
            Spacer(Modifier.height(8.dp))
            Text(text = "SUA LOCALIZAÇÃO FOI ENCONTRADA!", fontSize = 9.sp, letterSpacing = 3.sp, color = TextPrimary.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
        }
    }
}