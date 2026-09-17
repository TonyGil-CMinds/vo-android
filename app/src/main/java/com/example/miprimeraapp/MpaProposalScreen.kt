package com.example.miprimeraapp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private val MpaBlue = Color(0xFF0085FF)
private val MpaIce = Color(0xFFE4FDFF)
private val MpaGrey = Color(0xFF9AA3A3)
private val MpaRule = Color(0xFFD3D9D9)
private val MpaHeading = FontFamily(Font(R.font.bowlby_one_regular))

/** Cuatro frases de 1.8 s; el porcentaje recorre el total. */
private const val GenerationMillis = 7200
private const val WaveMillis = 2700

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Sustituye este generador por un servicio que reciba area.toGeoJson(); la demo no analiza el polígono. */
interface MpaNameGenerator { suspend fun propose(area: AreaGeometry, avoid: String?): String }

class DemoMpaNameGenerator : MpaNameGenerator {
    private val prefixes = listOf("COSTA DE", "REFUGIO DE", "SANTUARIO DE", "CORREDOR DE", "RESERVA DE", "ÁREA MARINA DE")
    private val places = listOf(
        "BAHÍA MAGDALENA", "PUNTA COYOTE", "ISLA ESPÍRITU SANTO", "CABO PULMO", "LAGUNA SAN IGNACIO",
        "ISLA CERRALVO", "PUNTA ARENA", "ISLA SAN JOSÉ", "BAHÍA CONCEPCIÓN", "PUNTA LOBOS",
    )
    override suspend fun propose(area: AreaGeometry, avoid: String?): String {
        delay(650) // Tarda lo que tardaría una consulta, para que la espera se vea.
        var proposal: String
        do { proposal = "${prefixes.random()} ${places.random()}" } while (proposal == avoid)
        return proposal
    }
}

/**
 * Cubre el trazado con azul, analiza y descubre la portada recogiendo ese mismo azul
 * hasta el marco de la imagen, de modo que el bloque de color es el elemento compartido.
 */
@Composable
fun MpaProposalFlow(
    area: AreaGeometry,
    onBack: () -> Unit,
    generator: MpaNameGenerator = remember { DemoMpaNameGenerator() },
) {
    var named by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var regenerating by remember { mutableStateOf(false) }
    var showNext by rememberSaveable { mutableStateOf(false) }
    val enter = remember { Animatable(if (named) 1f else 0f) }
    val work = remember { Animatable(if (named) 1f else 0f) }
    val collapse = remember { Animatable(if (named) 1f else 0f) }
    val reveal = remember { Animatable(1f) } // 1 = título asentado, 0 = hueco mientras se propone otro
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (named) return@LaunchedEffect
        enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        // El nombre se pide mientras corre el análisis, no al final.
        launch { title = TextFieldValue(generator.propose(area, null)) }
        work.animateTo(1f, tween(GenerationMillis, easing = LinearEasing))
        delay(320)
        named = true
        collapse.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }

    // Durante el análisis el retroceso no debe devolver a medio trazado.
    BackHandler { if (showNext) showNext = false else if (named && !collapse.isRunning) onBack() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        val coverHeight = fullHeight * 0.38f
        val heightPx = with(LocalDensity.current) { fullHeight.toPx() }
        val fadePx = with(LocalDensity.current) { 200.dp.toPx() }

        if (named) NameProposalScreen(
            area = area,
            report = showNext,
            coverHeight = coverHeight,
            title = title,
            onTitle = { title = it },
            regenerating = regenerating,
            reveal = reveal.value,
            onRegenerate = {
                if (!regenerating) scope.launch {
                    regenerating = true
                    // El título se retira antes de pedir el nuevo; el hueco nunca queda vacío.
                    reveal.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                    title = TextFieldValue(generator.propose(area, title.text))
                    regenerating = false
                    reveal.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                }
            },
            onContinue = { showNext = true },
        )

        if (collapse.value < 1f) {
            val blockHeight = fullHeight + (coverHeight - fullHeight) * collapse.value
            val edge = (heightPx + fadePx) * (1f - enter.value)
            Box(
                Modifier.fillMaxWidth().height(blockHeight).clipToBounds()
                    .graphicsLayer { if (named) compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // El azul no se corta al recogerse: su borde inferior se apaga hacia transparente.
                        if (!named) return@drawWithContent
                        val feather = 96.dp.toPx()
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.White, Color.Transparent),
                                startY = size.height - feather, endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .background(
                        if (named) SolidColor(MpaBlue)
                        else Brush.verticalGradient(listOf(Color.Transparent, MpaBlue), edge - fadePx, edge),
                    ),
            ) {
                // Altura fija: el recorte recoge el bloque sin reacomodar lo que hay dentro.
                GeneratingView(
                    progress = work.value,
                    modifier = Modifier.fillMaxWidth().height(fullHeight)
                        .alpha(if (named) (1f - collapse.value * 2.2f).coerceIn(0f, 1f) else enter.value),
                )
                // Sin el velo del borde: el desvanecido inferior del bloque descubre el de la portada real.
                if (named) Image(
                    painterResource(R.drawable.cover_mpa),
                    stringResource(R.string.mpa_cover_description),
                    Modifier.fillMaxWidth().height(coverHeight).align(Alignment.TopCenter).alpha(collapse.value),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }

}

@Composable
private fun GeneratingView(progress: Float, modifier: Modifier) {
    val steps = listOf(R.string.mpa_step_signals, R.string.mpa_step_evidence, R.string.mpa_step_patterns, R.string.mpa_step_report)
    val step = (progress * steps.size).toInt().coerceIn(0, steps.lastIndex)
    BoxWithConstraints(modifier) {
        val disc = minOf(maxWidth * 0.56f, maxHeight * 0.30f, 220.dp)
        Column(
            Modifier.align(Alignment.Center).offset(y = -maxHeight * 0.05f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CrystallineRefraction(Modifier.size(disc))
            Spacer(Modifier.height(26.dp))
            Crossfade(step, animationSpec = tween(400), label = "Generation step") {
                Text(stringResource(steps[it]), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "${(progress * 100).toInt().coerceIn(0, 100)}%",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.08f)
                .semantics {
                    contentDescription = "Generando tu reporte"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
        )
    }
}

/**
 * Crystalline refraction sobre un disco: una onda sale del centro y empuja los puntos
 * de la retícula hacia fuera. Fuera del disco la retícula queda apagada, marcando el plano.
 */
@Composable
private fun CrystallineRefraction(modifier: Modifier) {
    val loop = rememberInfiniteTransition(label = "Crystalline refraction")
    val wave by loop.animateFloat(
        0f, 1f, infiniteRepeatable(tween(WaveMillis, easing = LinearEasing)), label = "Wave",
    )
    Canvas(modifier) {
        val grid = 15
        val span = size.minDimension
        val spacing = span / (grid - 1)
        val center = Offset(size.width / 2f, size.height / 2f)
        val disc = span * 0.47f
        val waveRadius = wave * span * 1.2f
        val waveWidth = span / 3f
        val maxShift = span * 0.055f
        val dim = 0.9.dp.toPx()
        val base = 1.2.dp.toPx()
        val grow = 2.dp.toPx()
        for (row in 0 until grid) for (column in 0 until grid) {
            val dot = Offset(column * spacing, row * spacing)
            val distance = (dot - center).getDistance()
            if (distance > disc) {
                drawCircle(Color.White.copy(alpha = 0.10f), dim, dot)
                continue
            }
            val toWave = abs(distance - waveRadius)
            val shift = if (toWave < waveWidth / 2f)
                easeInOutCubic(sin(toWave / (waveWidth / 2f) * Math.PI.toFloat())) * maxShift else 0f
            val energy = shift / maxShift
            val angle = atan2(dot.y - center.y, dot.x - center.x)
            drawCircle(
                Color.White.copy(alpha = 0.2f + energy * 0.8f),
                base + energy * grow,
                dot + Offset(cos(angle) * shift, sin(angle) * shift),
            )
        }
    }
}

@Composable
private fun NameProposalScreen(
    area: AreaGeometry,
    report: Boolean,
    coverHeight: Dp,
    title: TextFieldValue,
    onTitle: (TextFieldValue) -> Unit,
    regenerating: Boolean,
    reveal: Float,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var readingProgress by remember { mutableFloatStateOf(0f) }
    val compactCoverHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 52.dp
    val headerHeight by animateDpAsState(if (report) (coverHeight * 0.43f - 88.dp * readingProgress).coerceAtLeast(compactCoverHeight) else coverHeight, tween(if (report && readingProgress > 0f) 240 else 750, easing = FastOutSlowInEasing), label = "Report cover")
    val titleGap by animateDpAsState(if (report) 0.dp else coverHeight * 0.25f, tween(750), label = "Report title")
    val titleSize by animateFloatAsState(if (report) 24f - 4f * readingProgress else 30f, tween(280), label = "Report title size")
    val titleMinHeight by animateDpAsState(if (report) 84.dp - 28.dp * readingProgress else 84.dp, tween(240), label = "Reading header")
    LaunchedEffect(report) { if (report) focus.clearFocus() }
    // Alex vive fuera del lector: su llamada y su chat tapan la pantalla entera, sin cabecera ni navbar.
    val brain = remember { ScriptedAlexBrain() }
    var quality by remember { mutableStateOf<Int?>(null) }
    var calling by rememberSaveable { mutableStateOf(false) }
    var chatting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(area, report) {
        if (report && quality == null) {
            quality = runCatching { DemoAreaInformationRepository().evaluate(area).quality }.getOrNull()
        }
    }
    val alex = remember(area, title.text, quality) { alexContext(title.text, area, quality) }
    // Con el reporte abierto, acercarse el teléfono a la oreja llama a Alex.
    ProximityToEar(enabled = report && !calling) { calling = true }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, MpaIce)))) {
        Box(Modifier.fillMaxWidth().height(headerHeight)) {
            Image(
                painterResource(R.drawable.cover_mpa),
                stringResource(R.string.mpa_cover_description),
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // El canto de la imagen se disuelve en el fondo en vez de cortarse contra él.
            Box(
                Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White))),
            )
        }
        Column(
            Modifier.then(if (report) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()).fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(titleGap))
            AnimatedVisibility(!report) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.mpa_name_eyebrow),
                        color = MpaBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
            // Altura mínima fija: el título entra y sale sin que la regla ni el botón salten.
            Box(Modifier.fillMaxWidth().heightIn(min = titleMinHeight), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = title,
                    readOnly = report,
                    maxLines = if (report) 3 else Int.MAX_VALUE,
                    onValueChange = onTitle,
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer {
                            alpha = reveal
                            translationY = (1f - reveal) * 16.dp.toPx()
                        }
                        .semantics { contentDescription = "Nombre de la propuesta" },
                    textStyle = TextStyle(
                        fontFamily = MpaHeading, fontSize = titleSize.sp, lineHeight = (titleSize * 1.25f).sp,
                        letterSpacing = (-0.6).sp, color = Color.Black, textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(Color.Black),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                )
                if (reveal < 1f) TitleShimmer(Modifier.fillMaxWidth().alpha(1f - reveal))
            }
            Text(reportCoordinates(area.center), color = MpaBlue, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            AnimatedVisibility(!report) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(Modifier.fillMaxWidth(0.86f), color = MpaRule)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.mpa_name_hint),
                        color = MpaGrey, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                    val busy = rememberInfiniteTransition(label = "Regenerating")
                    val spin by busy.animateFloat(
                        0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "Spin",
                    )
                    val throb by busy.animateFloat(
                        0.85f, 1.25f,
                        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Throb",
                    )
                    Row(
                        Modifier.clickable(enabled = !regenerating, onClick = onRegenerate).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!regenerating) { Text(stringResource(R.string.mpa_name_or), color = MpaGrey, fontSize = 14.sp) }
                        Text(
                            stringResource(if (regenerating) R.string.mpa_name_generating else R.string.mpa_name_regenerate),
                            color = MpaBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painterResource(R.drawable.ic_ia),
                            null,
                            Modifier.size(14.dp).graphicsLayer {
                                if (!regenerating) { return@graphicsLayer }
                                rotationZ = spin; scaleX = throb; scaleY = throb
                            },
                            tint = Color.Unspecified,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        // Alex habla del reporte que el usuario tiene delante, empezando por su nombre.
        if (report) ReportReader(area, Modifier.weight(1f).fillMaxWidth(), title = title.text,
            onAgent = { chatting = true }, onReadingProgress = { readingProgress = it })
        AnimatedVisibility(!report) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onContinue,
                    enabled = title.text.isNotBlank() && !regenerating,
                    modifier = Modifier.fillMaxWidth(0.62f).widthIn(max = 300.dp).heightIn(min = 54.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MpaBlue, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(3.dp),
                ) {
                    Text(stringResource(R.string.mpa_continue), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    MpaArrow()
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) {
                        Box(Modifier.width(24.dp).height(6.dp).clip(CircleShape)
                            .background(if (it < 3) MpaBlue else Color(0xFFD4E3E3)))
                    }
                }
            }
        }
    }
    if (report) ReportActions(title.text, area, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
    // Encima de la cabecera y de la navbar: ocupan la pantalla completa.
    AnimatedVisibility(chatting && !calling, enter = fadeIn(tween(260)), exit = fadeOut(tween(200))) {
        AlexChat(alex, brain, onCall = { calling = true }, onClose = { chatting = false })
    }
    AnimatedVisibility(calling, enter = fadeIn(tween(320)), exit = fadeOut(tween(240))) {
        AlexCallScreen(alex, brain,
            onChat = { calling = false; chatting = true },
            onHangUp = { calling = false })
    }
    }
}


/** Ocupa el sitio del título mientras se propone otro: dos renglones con un brillo que los recorre. */
@Composable
private fun TitleShimmer(modifier: Modifier) {
    val loop = rememberInfiniteTransition(label = "Title shimmer")
    val sweep by loop.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1150, easing = LinearEasing)), label = "Sweep",
    )
    val base = MpaRule.copy(alpha = 0.7f)
    val glint = MpaBlue.copy(alpha = 0.28f)
    Column(
        modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(0.76f, 0.54f).forEach { width ->
            Box(
                Modifier.fillMaxWidth(width).height(24.dp).clip(RoundedCornerShape(12.dp)).background(
                    Brush.horizontalGradient(
                        0f to base,
                        (sweep - 0.25f).coerceIn(0f, 1f) to base,
                        sweep.coerceIn(0f, 1f) to glint,
                        (sweep + 0.25f).coerceIn(0f, 1f) to base,
                        1f to base,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun MpaArrow() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        fun line(x: Float, y: Float, a: Float, b: Float) =
            drawLine(Color.White, Offset(x * w, y * h), Offset(a * w, b * h), 2.dp.toPx(), StrokeCap.Round)
        line(.12f, .5f, .86f, .5f); line(.58f, .2f, .88f, .5f); line(.58f, .8f, .88f, .5f)
    }
}
