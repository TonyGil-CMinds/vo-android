package com.example.miprimeraapp

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ZoneBlue = Color(0xFF0085FF)
private val ZoneIce = Color(0xFFDBFFFF)
private val ZoneBlack = Color(0xFF282727)
private val ZoneHeading = FontFamily(Font(R.font.bowlby_one_regular))
private fun mix(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

/** The cover expands in-place; the selector is mounted once and uncovered from below. */
@Composable
fun ExperienceFlow() {
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var tracing by rememberSaveable { mutableStateOf(false) }
    if (tracing) {
        TraceAreaScreen(onBack = { tracing = false })
        return
    }
    var startGeneration by remember { mutableIntStateOf(0) }
    var cover by remember { mutableStateOf(Rect.Zero) }
    val expansion = remember { Animatable(0f) }
    val reveal = remember { Animatable(if (stage == 2) 1f else 0f) }
    var frameReady by remember { mutableStateOf(false) }
    var videoEnded by remember { mutableStateOf(false) }
    val frameAlpha by animateFloatAsState(if (frameReady) 1f else 0f, tween(220), label = "Poster to video")
    BackHandler(stage > 0) { startGeneration++; stage = 0 }

    LaunchedEffect(stage) {
        if (stage == 1) {
            expansion.snapTo(0f)
            reveal.snapTo(0f)
            expansion.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(stage, frameReady, videoEnded) {
        if (stage == 1 && (frameReady || videoEnded)) {
            if (!videoEnded) delay(2900)
            reveal.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
            stage = 2
        }
    }
    // An unavailable decoder must not leave the experience blocked.
    LaunchedEffect(stage) {
        if (stage == 1) {
            delay(8000)
            if (!frameReady) videoEnded = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        if (stage < 2) {
          key(startGeneration) {
            StartScreen { bounds ->
                if (stage == 0) {
                    cover = bounds
                    frameReady = false
                    videoEnded = false
                    stage = 1
                }
            }
          }
        }
        if (stage == 1) {
            val initialHeight = if (cover.height > 0) with(density) { cover.height.toDp() } else maxHeight * 0.52f
            val initialTop = with(density) { cover.top.toDp() }
            Box(Modifier.offset(y = initialTop * (1f - expansion.value))
                .fillMaxWidth().height(initialHeight + (maxHeight - initialHeight) * expansion.value)
                .clipToBounds()) {
                Image(painterResource(R.drawable.onboardingscreen_cover), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                CoverVideo(
                    modifier = Modifier.fillMaxSize().alpha(frameAlpha),
                    onFrame = { frameReady = true }, onEnd = { videoEnded = true },
                )
            }
        }
        if (stage > 0) {
            Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                drawContent()
                val feather = 100.dp.toPx()
                val edge = (size.height + feather) * (1f - reveal.value) - feather
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White),
                    startY = edge, endY = edge + feather), blendMode = BlendMode.DstIn)
            }) {
                ZoneSelectorScreen(interactive = stage == 2, onSelect = { tracing = true })
            }
        }
    }
}

@Composable
private fun CoverVideo(modifier: Modifier, onFrame: () -> Unit, onEnd: () -> Unit) {
    val context = LocalContext.current
    val frameCallback by rememberUpdatedState(onFrame)
    val endCallback by rememberUpdatedState(onEnd)
    val player = remember { MediaPlayer.create(context, R.raw.onboarding_screen_afterclick) }
    DisposableEffect(player) {
        player?.setVolume(0f, 0f)
        player?.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) frameCallback()
            false
        }
        player?.setOnCompletionListener { endCallback() }
        player?.setOnErrorListener { _, _, _ -> endCallback(); true }
        if (player == null) endCallback()
        onDispose {
            player?.setOnInfoListener(null)
            player?.setOnCompletionListener(null)
            player?.setOnErrorListener(null)
            player?.release()
        }
    }
    AndroidView(modifier = modifier, factory = { ctx ->
        TextureView(ctx).apply {
            isOpaque = false
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    val surface = Surface(texture)
                    player?.setSurface(surface)
                    surface.release()
                    cropVideo(this@apply, player)
                    player?.start()
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    cropVideo(this@apply, player)
                }
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    return true
                }
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        }
    })
}

private fun cropVideo(view: TextureView, player: MediaPlayer?) {
    if (player == null || view.width == 0 || view.height == 0 || player.videoHeight == 0) return
    val aspect = player.videoWidth.toFloat() / player.videoHeight
    val viewAspect = view.width.toFloat() / view.height
    view.setTransform(Matrix().apply {
        setScale(maxOf(1f, aspect / viewAspect), maxOf(1f, viewAspect / aspect), view.width / 2f, view.height / 2f)
    })
}

@Composable
fun ZoneSelectorScreen(interactive: Boolean = true, onSelect: () -> Unit = {}) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var firstHeight by remember { mutableStateOf(205.dp) }
    var secondHeight by remember { mutableStateOf(205.dp) }
    BoxWithConstraints(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color.White, ZoneIce)),
    ).safeDrawingPadding()) {
        val viewport = maxHeight
        val collapseDistance = 300.dp
        val scrollDp = with(density) { scroll.value.toDp() }
        val collapse = (scrollDp / collapseDistance).coerceIn(0f, 1f)
        val eased = FastOutSlowInEasing.transform(collapse)
        val extra = maxOf(0.dp, scrollDp - collapseDistance)
        val titleTop = viewport * mix(0.565f, 0.075f, eased)
        val boatTop = titleTop + mix(120f, 110f, eased).dp
        val pathTop = boatTop + 39.dp
        val footerHeight = 135.dp
        val timelineHeight = 88.dp + firstHeight + 35.dp + secondHeight + 45.dp
        val availableTimeline = maxOf(100.dp, viewport - (viewport * 0.075f + 149.dp) - footerHeight)
        val extraRange = maxOf(180.dp, timelineHeight - availableTimeline + 80.dp)
        val timelineAlpha = ((collapse - 0.58f) / 0.42f).coerceIn(0f, 1f)

        // Real ScrollState provides fling, accessibility scrolling, and restoration.
        // Counter-offset keeps the composition pinned while its geometry follows the scroll.
        Box(Modifier.fillMaxSize().verticalScroll(scroll, enabled = interactive)) {
            Box(Modifier.fillMaxWidth().height(viewport + collapseDistance + extraRange)) {
                Box(Modifier.fillMaxWidth().height(viewport).offset(y = scrollDp).clipToBounds()) {
                    val windowHeight = minOf(viewport * 0.33f, 295.dp)
                    Image(painterResource(R.drawable.ventana_oceano),
                        stringResource(R.string.zone_window_description),
                        modifier = Modifier.align(Alignment.TopCenter)
                            .offset(y = viewport * mix(0.17f, 0.09f, eased))
                            .height(windowHeight).width(windowHeight * 0.74f)
                            .graphicsLayer { scaleX = 1f - eased; scaleY = 1f - eased; alpha = 1f - eased },
                        contentScale = ContentScale.Fit)
                    Column(Modifier.fillMaxWidth().offset(y = titleTop), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.zone_title), fontFamily = ZoneHeading,
                            fontSize = mix(27f, 24f, eased).sp, lineHeight = mix(27f, 24f, eased).sp,
                            color = ZoneBlack, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(9.dp))
                        Text(stringResource(R.string.zone_coordinates), color = ZoneBlue,
                            fontFamily = ZoneHeading, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    Column(Modifier.fillMaxWidth().offset(y = boatTop), horizontalAlignment = Alignment.CenterHorizontally) {
                        RockingBoat(Modifier.size(30.dp).clickable(enabled = interactive) {
                            scope.launch { scroll.animateScrollTo(with(density) { collapseDistance.roundToPx() }, tween(900)) }
                        })
                        Text(stringResource(R.string.zone_swipe), color = ZoneBlue, fontSize = 8.sp,
                            letterSpacing = 2.sp, modifier = Modifier.alpha(1f - collapse))
                    }
                    if (timelineAlpha > 0f) {
                        Box(Modifier.fillMaxWidth().offset(y = pathTop)
                            .height(maxOf(1.dp, viewport - pathTop)).clipToBounds().alpha(timelineAlpha)) {
                            Roadmap(
                                modifier = Modifier.fillMaxWidth().height(timelineHeight),
                                advance = collapse, extraPx = with(density) { extra.toPx() },
                                firstHeight = firstHeight,
                                onFirstHeight = { firstHeight = with(density) { it.toDp() } },
                                onSecondHeight = { secondHeight = with(density) { it.toDp() } },
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, ZoneIce, ZoneIce)))
            .padding(top = 25.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onSelect, enabled = interactive,
                modifier = Modifier.fillMaxWidth(0.62f).widthIn(max = 300.dp).heightIn(min = 52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ZoneBlue, contentColor = Color.White,
                    disabledContainerColor = ZoneBlue, disabledContentColor = Color.White)) {
                Text(stringResource(R.string.zone_select), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { index -> Box(Modifier.width(24.dp).height(6.dp).clip(CircleShape)
                    .background(if (index == 0) ZoneBlue else Color(0xFFCEE3E5))) }
            }
        }
    }
}

@Composable
private fun RockingBoat(modifier: Modifier) {
    val loop = rememberInfiniteTransition(label = "Boat on waves")
    val motion by loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Bob")
    Image(painterResource(R.drawable.ic_boat), stringResource(R.string.zone_swipe),
        modifier.graphicsLayer { rotationZ = motion * 7f; translationY = motion * 2.dp.toPx() })
}

@Composable
private fun Roadmap(modifier: Modifier, advance: Float, extraPx: Float, firstHeight: androidx.compose.ui.unit.Dp,
    onFirstHeight: (Int) -> Unit, onSecondHeight: (Int) -> Unit) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val firstTop = 70.dp
        val secondTop = firstTop + firstHeight + 35.dp
        val firstY = with(density) { (firstTop + 20.dp).toPx() }
        val secondY = with(density) { (secondTop + 20.dp).toPx() }
        val lane = 38.dp
        val lanePx = with(density) { lane.toPx() }
        val bendY = with(density) { 52.dp.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val prefix = remember(widthPx, density) {
            Path().apply {
                moveTo(widthPx / 2f, 0f)
                lineTo(widthPx / 2f, bendY - 24f * density.density)
                quadraticTo(widthPx / 2f, bendY - 12f * density.density, widthPx / 2f - 24f * density.density, bendY - 12f * density.density)
                lineTo(lanePx + 16f * density.density, bendY - 12f * density.density)
                quadraticTo(lanePx, bendY - 12f * density.density, lanePx, bendY + 4f * density.density)
            }
        }
        val prefixMeasure = remember(prefix) { PathMeasure().apply { setPath(prefix, false) } }
        val curveEnd = bendY + with(density) { 4.dp.toPx() }
        val firstDistance = prefixMeasure.length + firstY - curveEnd
        val secondDistance = prefixMeasure.length + secondY - curveEnd
        val travel = (firstDistance + with(density) { 90.dp.toPx() }) * ((advance - 0.58f) / 0.42f).coerceIn(0f, 1f) + extraPx
        val firstActive = travel >= firstDistance
        val secondActive = travel >= secondDistance
        Canvas(Modifier.matchParentSize()) {
            val path = Path().apply { addPath(prefix); lineTo(lanePx, size.height) }
            drawPath(path, Color(0xFFABBABA), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            val measure = PathMeasure().apply { setPath(path, false) }
            val drawn = Path()
            measure.getSegment(0f, (travel - extraPx).coerceIn(0f, measure.length), drawn, true)
            drawPath(drawn, ZoneBlue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        // Keep the curved approach attached to the boat as the milestones pass beneath it.
        val extra = with(density) { extraPx.toDp() }
        Box(Modifier.fillMaxSize().drawWithContent {
            clipRect(top = curveEnd) { this@drawWithContent.drawContent() }
        }) {
        RoadNode(firstActive, Modifier.offset(x = lane - 11.dp, y = firstTop + 9.dp - extra))
        RoadNode(secondActive, Modifier.offset(x = lane - 11.dp, y = secondTop + 9.dp - extra))
        RoadEntry(firstActive, true, Modifier.fillMaxWidth().offset(y = firstTop - extra)
            .padding(start = 70.dp, end = 28.dp).onSizeChanged { onFirstHeight(it.height) })
        RoadEntry(secondActive, false, Modifier.fillMaxWidth().offset(y = secondTop - extra)
            .padding(start = 70.dp, end = 28.dp).onSizeChanged { onSecondHeight(it.height) })
        }
    }
}

@Composable
private fun RoadNode(active: Boolean, modifier: Modifier) {
    val fill by animateFloatAsState(if (active) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "Node activation")
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) { pulse.snapTo(1f); pulse.animateTo(0f, tween(650)) }
        else pulse.snapTo(0f)
    }
    Canvas(modifier.size(22.dp)) {
        val color = lerp(Color(0xFFAFBBBB), ZoneBlue, fill.coerceIn(0f, 1f))
        if (pulse.value > 0f) drawCircle(ZoneBlue.copy(alpha = pulse.value * 0.22f), radius = size.width * (1f - pulse.value * 0.4f))
        drawCircle(Color(0xFFEFFFFF), radius = 9.dp.toPx())
        drawCircle(color, radius = 9.dp.toPx(), style = Stroke(1.dp.toPx()))
        drawCircle(color, radius = (5f + fill * 0.6f).dp.toPx())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoadEntry(active: Boolean, pilot: Boolean, modifier: Modifier) {
    val emphasis by animateFloatAsState(if (active) 1f else 0.3f, tween(450), label = "Milestone reveal")
    Column(modifier.graphicsLayer { alpha = mix(0.55f, 1f, emphasis); translationY = (1f - emphasis) * 6.dp.toPx() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            if (pilot) {
                Image(painterResource(R.drawable.logo_fundes), "Fundes", Modifier.size(38.dp))
                Image(painterResource(R.drawable.logo_coppel), "Fundación Coppel", Modifier.size(38.dp))
            } else Image(painterResource(R.drawable.imagen_mesasdialogo), null, Modifier.size(38.dp).clip(CircleShape))
        }
        Spacer(Modifier.height(15.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (pilot) R.string.zone_pilot_title else R.string.zone_dialogue_title),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ZoneBlack)
            Text(stringResource(if (pilot) R.string.zone_now else R.string.zone_date),
                fontFamily = ZoneHeading, fontSize = 12.sp, color = if (active) ZoneBlue else Color(0xFF9DA5A5))
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(if (pilot) R.string.zone_pilot_body else R.string.zone_dialogue_body),
            fontSize = 16.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium,
            color = Color(0xFF828B8B).copy(alpha = mix(0.4f, 1f, emphasis)))
    }
}
