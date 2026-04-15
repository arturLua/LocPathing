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

@Composable
fun LocationScreen(viewModel: LocationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.fetchLocation()
    }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showLocationFound by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }

// Controla a sequência da interface
    LaunchedEffect(uiState.latitude) {
        if (uiState.latitude != null) {
            showData = false
            showLocationFound = true
            kotlinx.coroutines.delay(2800)
            showLocationFound = false
            kotlinx.coroutines.delay(400) // espera o fade out terminar
            showData = true
        } else {
            showData = false
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Ícone de localização",
                    tint = AccentRed,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "LocPathing",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = SurfaceLine)
            Spacer(Modifier.height(28.dp))

            // Conteúdo
            when {
                uiState.isLoading -> {
                    Spacer(Modifier.weight(1f))
                    ScanningAnimation()
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
                    CrosshairIdle()
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

        // Overlay de feedback - localização encontrada
        AnimatedVisibility(
            visible = showLocationFound,
            enter = fadeIn(tween(350)) + scaleIn(
                tween(350, easing = FastOutSlowInEasing),
                initialScale = 0.75f
            ),
            exit = fadeOut(tween(500)) + scaleOut(
                tween(500),
                targetScale = 1.05f
            )
        ) {
            LocationFoundOverlay()
        }

    }
}

// Tela vazia: mira estática
@Composable
fun CrosshairIdle() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    // Rotação lenta do anel externo
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    // Rotação inversa do anel intermediário
    val rotationReverse by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotationReverse"
    )

    // Pulso suave do ponto central
    val centerPulse by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue  = 7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "centerPulse"
    )

    // Opacidade piscando do texto
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "textAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Anel externo girando com marcações
            Canvas(modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
            ) {
                val cx = size.width / 2
                val cy = size.height / 2
                val rOuter = size.minDimension / 2f

                // Anel externo
                drawCircle(
                    color = AccentTeal.copy(alpha = 0.2f),
                    radius = rOuter,
                    style = Stroke(width = 1f)
                )

                // Marcações ao redor do anel externo (tipo bússola)
                val totalTicks = 36
                for (i in 0 until totalTicks) {
                    val angle = Math.toRadians((i * (360.0 / totalTicks)))
                    val isMajor = i % 9 == 0
                    val tickLen = if (isMajor) 14f else 6f
                    val alpha = if (isMajor) 0.8f else 0.3f

                    val startX = cx + (rOuter - tickLen) * kotlin.math.cos(angle).toFloat()
                    val startY = cy + (rOuter - tickLen) * kotlin.math.sin(angle).toFloat()
                    val endX   = cx + rOuter * kotlin.math.cos(angle).toFloat()
                    val endY   = cy + rOuter * kotlin.math.sin(angle).toFloat()

                    drawLine(
                        color = AccentTeal.copy(alpha = alpha),
                        start = Offset(startX, startY),
                        end   = Offset(endX, endY),
                        strokeWidth = if (isMajor) 2f else 1f
                    )
                }
            }

            // Anel intermediário girando ao contrário com arcos tracejados
            Canvas(modifier = Modifier
                .size(148.dp)
                .rotate(rotationReverse)
            ) {
                val cx = size.width / 2
                val cy = size.height / 2
                val rMid = size.minDimension / 2f

                // Arcos tracejados (4 arcos de 70° com gap de 20°)
                for (i in 0 until 4) {
                    val startAngle = i * 90f - 35f
                    drawArc(
                        color = AccentTeal.copy(alpha = 0.5f),
                        startAngle = startAngle,
                        sweepAngle = 70f,
                        useCenter = false,
                        style = Stroke(width = 1.5f),
                        topLeft = Offset(cx - rMid, cy - rMid),
                        size = androidx.compose.ui.geometry.Size(rMid * 2, rMid * 2)
                    )
                }

                // Cantos decorativos nos 4 quadrantes
                val cornerR = rMid * 0.72f
                val cornerLen = 18f
                listOf(-1f, 1f).forEach { sx ->
                    listOf(-1f, 1f).forEach { sy ->
                        drawLine(
                            color = AccentTeal.copy(alpha = 0.9f),
                            start = Offset(cx + sx * cornerR, cy + sy * cornerR - sy * cornerLen),
                            end   = Offset(cx + sx * cornerR, cy + sy * cornerR),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = AccentTeal.copy(alpha = 0.9f),
                            start = Offset(cx + sx * cornerR - sx * cornerLen, cy + sy * cornerR),
                            end   = Offset(cx + sx * cornerR, cy + sy * cornerR),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // Elementos fixos centrais
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val gap = 22f
                val lineLen = 20f

                // Círculo interno fixo
                drawCircle(
                    color = AccentTeal.copy(alpha = 0.15f),
                    radius = 30f,
                    style = Stroke(width = 1f)
                )

                // Linhas da mira com gap central
                val miraColor = AccentTeal.copy(alpha = 0.5f)
                drawLine(miraColor, Offset(cx, cy - gap), Offset(cx, cy - gap - lineLen), strokeWidth = 1.5f)
                drawLine(miraColor, Offset(cx, cy + gap), Offset(cx, cy + gap + lineLen), strokeWidth = 1.5f)
                drawLine(miraColor, Offset(cx - gap, cy), Offset(cx - gap - lineLen, cy), strokeWidth = 1.5f)
                drawLine(miraColor, Offset(cx + gap, cy), Offset(cx + gap + lineLen, cy), strokeWidth = 1.5f)

                // Ponto central pulsando
                drawCircle(color = AccentRed, radius = centerPulse)
                drawCircle(
                    color = AccentRed.copy(alpha = 0.3f),
                    radius = centerPulse * 2.5f,
                    style = Stroke(width = 1f)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Texto piscando
        Text(
            text = "AGUARDANDO SINAL",
            fontSize = 10.sp,
            letterSpacing = 4.sp,
            color = AccentTeal.copy(alpha = textAlpha)
        )

        Spacer(Modifier.height(6.dp))
    }
}

// Loading: linha de scan animada
@Composable
fun ScanningAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "sweep"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            tween(10000, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "rotation"
    )

    val rotationReverse by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            tween(6000, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "rotationReverse"
    )

    val centerPulse by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue  = 8f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "centerPulse"
    )

    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing),
            RepeatMode.Reverse
        ), label = "textAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {

            // Anel externo com marcações girando
            Canvas(modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
            ) {
                val cx = size.width / 2
                val cy = size.height / 2
                val rOuter = size.minDimension / 2f

                drawCircle(
                    color = AccentTeal.copy(alpha = 0.2f),
                    radius = rOuter,
                    style = Stroke(width = 1f)
                )

                for (i in 0 until 36) {
                    val angle = Math.toRadians(i * 10.0)
                    val isMajor = i % 9 == 0
                    val tickLen = if (isMajor) 14f else 6f
                    val alpha   = if (isMajor) 0.9f else 0.3f
                    val startX  = cx + (rOuter - tickLen) * kotlin.math.cos(angle).toFloat()
                    val startY  = cy + (rOuter - tickLen) * kotlin.math.sin(angle).toFloat()
                    val endX    = cx + rOuter * kotlin.math.cos(angle).toFloat()
                    val endY    = cy + rOuter * kotlin.math.sin(angle).toFloat()
                    drawLine(
                        color = AccentTeal.copy(alpha = alpha),
                        start = Offset(startX, startY),
                        end   = Offset(endX, endY),
                        strokeWidth = if (isMajor) 2f else 1f
                    )
                }
            }

            // Anel intermediário ao contrário com arcos e cantos
            Canvas(modifier = Modifier
                .size(148.dp)
                .rotate(rotationReverse)
            ) {
                val cx   = size.width / 2
                val cy   = size.height / 2
                val rMid = size.minDimension / 2f

                for (i in 0 until 4) {
                    drawArc(
                        color = AccentTeal.copy(alpha = 0.55f),
                        startAngle = i * 90f - 35f,
                        sweepAngle = 70f,
                        useCenter  = false,
                        style      = Stroke(width = 1.5f),
                        topLeft    = Offset(cx - rMid, cy - rMid),
                        size       = androidx.compose.ui.geometry.Size(rMid * 2, rMid * 2)
                    )
                }

                val cornerR   = rMid * 0.72f
                val cornerLen = 18f
                listOf(-1f, 1f).forEach { sx ->
                    listOf(-1f, 1f).forEach { sy ->
                        drawLine(
                            color = AccentTeal.copy(alpha = 1f),
                            start = Offset(cx + sx * cornerR, cy + sy * cornerR - sy * cornerLen),
                            end   = Offset(cx + sx * cornerR, cy + sy * cornerR),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = AccentTeal.copy(alpha = 1f),
                            start = Offset(cx + sx * cornerR - sx * cornerLen, cy + sy * cornerR),
                            end   = Offset(cx + sx * cornerR, cy + sy * cornerR),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // Sweep + blips + mira + centro
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx     = size.width / 2
                val cy     = size.height / 2
                val rSweep = size.minDimension / 2f - 4f

                // Anéis de grade internos
                listOf(0.35f, 0.65f).forEach { fraction ->
                    drawCircle(
                        color  = AccentTeal.copy(alpha = 0.08f),
                        radius = rSweep * fraction,
                        style  = Stroke(width = 1f)
                    )
                }

                // Linhas de grade cruzadas (fixas)
                drawLine(
                    color = AccentTeal.copy(alpha = 0.06f),
                    start = Offset(cx - rSweep, cy),
                    end   = Offset(cx + rSweep, cy),
                    strokeWidth = 1f
                )
                drawLine(
                    color = AccentTeal.copy(alpha = 0.06f),
                    start = Offset(cx, cy - rSweep),
                    end   = Offset(cx, cy + rSweep),
                    strokeWidth = 1f
                )

                // Rastro do sweep em camadas (mais suave)
                val trailLength = 120f
                val steps = 30
                for (i in 0..steps) {
                    val fraction   = i / steps.toFloat()
                    val alpha      = fraction * 0.22f
                    val arcStart   = sweepAngle - trailLength * (1f - fraction)
                    val sweepChunk = trailLength / steps
                    drawArc(
                        color      = AccentTeal.copy(alpha = alpha),
                        startAngle = arcStart,
                        sweepAngle = sweepChunk,
                        useCenter  = true,
                        topLeft    = Offset(cx - rSweep, cy - rSweep),
                        size       = androidx.compose.ui.geometry.Size(rSweep * 2, rSweep * 2)
                    )
                }

                // Linha de frente do sweep com brilho duplo
                val sweepRad = Math.toRadians(sweepAngle.toDouble())
                val sweepEndX = cx + rSweep * kotlin.math.cos(sweepRad).toFloat()
                val sweepEndY = cy + rSweep * kotlin.math.sin(sweepRad).toFloat()
                drawLine(
                    color       = AccentTeal.copy(alpha = 0.3f),
                    start       = Offset(cx, cy),
                    end         = Offset(sweepEndX, sweepEndY),
                    strokeWidth = 4f
                )
                drawLine(
                    color       = AccentTeal.copy(alpha = 0.95f),
                    start       = Offset(cx, cy),
                    end         = Offset(sweepEndX, sweepEndY),
                    strokeWidth = 1.2f
                )

                // Mira central fixa
                val gap     = 22f
                val lineLen = 20f
                val mira    = AccentTeal.copy(alpha = 0.5f)
                drawLine(mira, Offset(cx, cy - gap), Offset(cx, cy - gap - lineLen), strokeWidth = 1.5f)
                drawLine(mira, Offset(cx, cy + gap), Offset(cx, cy + gap + lineLen), strokeWidth = 1.5f)
                drawLine(mira, Offset(cx - gap, cy), Offset(cx - gap - lineLen, cy), strokeWidth = 1.5f)
                drawLine(mira, Offset(cx + gap, cy), Offset(cx + gap + lineLen, cy), strokeWidth = 1.5f)

                // Ponto central com halo pulsando
                drawCircle(
                    color  = AccentRed.copy(alpha = 0.25f),
                    radius = centerPulse * 3f
                )
                drawCircle(color = AccentRed, radius = centerPulse)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "ESCANEANDO...",
            fontSize = 10.sp,
            letterSpacing = 4.sp,
            color = AccentTeal.copy(alpha = textAlpha)
        )

        Spacer(Modifier.height(6.dp))
    }
}

// Dados de localização
@Composable
fun LocationDataDisplay(
    uiState: LocationUiState,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(28.dp)
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

@Composable
fun LocationFoundOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "found")

    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 1.4f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "ring"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "ringAlpha"
    )

    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = LinearEasing),
            RepeatMode.Reverse
        ), label = "textAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Fundo escurecido
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background.copy(alpha = 0.75f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ícone com anel pulsando
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val baseR = size.minDimension / 3.5f

                    // Anel externo pulsando
                    drawCircle(
                        color  = AccentTeal.copy(alpha = ringAlpha * 0.5f),
                        radius = baseR * ringScale * 1.4f,
                        style  = Stroke(width = 1f)
                    )
                    // Anel médio pulsando
                    drawCircle(
                        color  = AccentTeal.copy(alpha = ringAlpha * 0.8f),
                        radius = baseR * ringScale,
                        style  = Stroke(width = 1.5f)
                    )
                    // Círculo de fundo do ícone
                    drawCircle(
                        color  = AccentTeal.copy(alpha = 0.15f),
                        radius = baseR * 0.85f
                    )
                    drawCircle(
                        color  = AccentTeal.copy(alpha = 0.6f),
                        radius = baseR * 0.85f,
                        style  = Stroke(width = 1.5f)
                    )

                    // Cantos decorativos em L
                    val cornerR   = baseR * 1.25f
                    val cornerLen = 14f
                    listOf(-1f, 1f).forEach { sx ->
                        listOf(-1f, 1f).forEach { sy ->
                            drawLine(
                                color = AccentTeal.copy(alpha = 0.9f),
                                start = Offset(cx + sx * cornerR, cy + sy * cornerR - sy * cornerLen),
                                end   = Offset(cx + sx * cornerR, cy + sy * cornerR),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = AccentTeal.copy(alpha = 0.9f),
                                start = Offset(cx + sx * cornerR - sx * cornerLen, cy + sy * cornerR),
                                end   = Offset(cx + sx * cornerR, cy + sy * cornerR),
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                Icon(
                    imageVector        = Icons.Default.LocationOn,
                    contentDescription = "Localização encontrada",
                    tint               = AccentRed,
                    modifier           = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text          = "SINAL OBTIDO",
                fontSize      = 13.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 5.sp,
                color         = AccentTeal.copy(alpha = textAlpha)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text          = "SUA LOCALIZAÇÃO FOI ENCONTRADA!",
                fontSize      = 9.sp,
                letterSpacing = 3.sp,
                color         = TextPrimary.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}