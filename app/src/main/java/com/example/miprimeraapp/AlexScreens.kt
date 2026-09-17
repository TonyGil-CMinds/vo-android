package com.example.miprimeraapp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val AlexBlue = Color(0xFF0085FF)
private val AlexIce = Color(0xFFDDFBFF)
private val AlexInk = Color(0xFF14366F)
private val AlexGrey = Color(0xFF8F9BA0)
private val AlexRed = Color(0xFFC4453A)

/** La demo corta a los cinco minutos, como anuncia el contador de la pantalla. */
private const val CallLimitSeconds = 300

internal enum class AlexTurn { CONNECTING, SPEAKING, LISTENING }

/**
 * Llamada con Alex. No hay audio: el turno de habla se simula y el orbe se agita con esa
 * envolvente, de modo que conectar un motor real sólo cambia de dónde sale `amplitude`.
 */
@Composable
internal fun AlexCallScreen(
    context: AlexContext,
    brain: AlexBrain,
    onChat: () -> Unit,
    onHangUp: () -> Unit,
) {
    var turn by remember { mutableStateOf(AlexTurn.CONNECTING) }
    var caption by remember { mutableStateOf("") }
    var speaker by rememberSaveable { mutableStateOf(false) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var asked by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val clock = remember { mutableFloatStateOf(0f) }

    BackHandler { onHangUp() }

    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                if (start == 0L) start = frame
                clock.floatValue = (frame - start) / 1000f
            }
        }
    }
    LaunchedEffect(Unit) {
        while (seconds < CallLimitSeconds) { delay(1000); seconds++ }
        onHangUp()
    }
    // Alex saluda, escucha, y si nadie dice nada retoma con el siguiente tema.
    LaunchedEffect(asked) {
        if (asked == 0) {
            turn = AlexTurn.CONNECTING
            delay(1100)
            caption = brain.greeting(context)
        } else {
            turn = AlexTurn.SPEAKING
            caption = brain.reply(brain.topics()[(asked - 1) % brain.topics().size], context)
        }
        turn = AlexTurn.SPEAKING
        delay((caption.length * 42L).coerceIn(2200L, 9000L))
        turn = AlexTurn.LISTENING
        caption = ""
        delay(5200)
        asked++
    }

    val amplitude by remember {
        derivedStateOf {
            when (turn) {
                AlexTurn.SPEAKING -> 0.35f + 0.65f * speechEnvelope(clock.floatValue)
                AlexTurn.LISTENING -> 0.10f + 0.10f * speechEnvelope(clock.floatValue * 0.4f)
                AlexTurn.CONNECTING -> 0.05f
            }
        }
    }
    val orbLevel by animateFloatAsState(amplitude, spring(stiffness = Spring.StiffnessMedium), label = "Orb level")

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to AlexBlue, 0.55f to Color(0xFF7CC6FF), 1f to AlexIce),
        ).safeDrawingPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHangUp) { AlexIcon("back", Color.White) }
            Text(
                "%02d:%02d / 5:00".format(Locale.US, seconds / 60, seconds % 60),
                Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(48.dp))
        }
        val viewport = maxHeight
        val orbSide = minOf(maxWidth * 0.52f, 230.dp)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = viewport * 0.12f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Alex", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(viewport * 0.06f))
            FluidOrb(size = orbSide, amplitude = orbLevel)
            Spacer(Modifier.height(26.dp))
            Text(
                when (turn) {
                    AlexTurn.CONNECTING -> "Conectando…"
                    AlexTurn.LISTENING -> "Te escucho"
                    AlexTurn.SPEAKING -> ""
                },
                color = Color.White.copy(alpha = .85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            AnimatedVisibility(caption.isNotEmpty(), enter = fadeIn(tween(320)), exit = fadeOut(tween(200))) {
                Text(
                    caption, Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top,
        ) {
            CallAction("Altavoz", "speaker", if (speaker) AlexBlue else Color(0xFFE8FDFF),
                if (speaker) Color.White else AlexInk) { speaker = !speaker }
            CallAction("Colgar", "hangup", AlexRed, Color.White, big = true, onClick = onHangUp)
            CallAction("Chat", "chat", Color(0xFFE8FDFF), AlexInk, onClick = onChat)
        }
    }
}

@Composable
private fun CallAction(
    label: String, kind: String, container: Color, tint: Color,
    big: Boolean = false, onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val side = if (big) 86.dp else 64.dp
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "Call action")
        Box(
            Modifier.size(side).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape).background(container)
                .clickable(interaction, null, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { AlexIcon(kind, tint, if (big) 34.dp else 26.dp) }
        Spacer(Modifier.height(10.dp))
        Text(label, color = AlexGrey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Chat con Alex, con el mismo cerebro que la llamada. El botón azul abre la voz,
 * igual que acercarse el teléfono a la oreja.
 */
@Composable
internal fun AlexChat(
    context: AlexContext,
    brain: AlexBrain,
    onCall: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf<AlexMessage>() }
    var thinking by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) { if (messages.isEmpty()) messages += AlexMessage(brain.greeting(context), true) }
    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    fun send(text: String) {
        if (text.isBlank() || thinking) return
        messages += AlexMessage(text.trim(), false)
        draft = ""
        focus.clearFocus()
        scope.launch {
            thinking = true
            val answer = brain.reply(text, context)
            thinking = false
            messages += AlexMessage(answer, true)
        }
    }

    BackHandler { onClose() }
    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF7FFFF), AlexIce)))
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { AlexIcon("back", AlexInk) }
            Text("Alex", Modifier.padding(start = 4.dp), color = AlexInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromAlex) Arrangement.Start else Arrangement.End) {
                    Surface(
                        Modifier.widthIn(max = 300.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = if (message.fromAlex) Color.White else AlexBlue,
                    ) {
                        Text(
                            message.text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            color = if (message.fromAlex) Color(0xFF3F4A4D) else Color.White,
                            fontSize = 14.sp, lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(thinking) {
            Row(Modifier.padding(start = 26.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                FluidOrb(size = 18.dp, amplitude = 0.8f)
                Text("Alex está escribiendo…", Modifier.padding(start = 8.dp), color = AlexGrey, fontSize = 12.sp)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            brain.topics().forEach { topic ->
                SuggestionChip(onClick = { send(topic) }, label = { Text(topic, fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.White))
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(Modifier.weight(1f).heightIn(min = 52.dp), shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = AlexInk, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) Text("Pregunta a Alex", color = AlexGrey, fontSize = 15.sp)
                        BasicTextField(
                            value = draft, onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pregunta a Alex" },
                            textStyle = TextStyle(color = AlexInk, fontSize = 15.sp),
                            cursorBrush = SolidColor(AlexBlue),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send(draft) }),
                        )
                    }
                }
            }
            RoundButton(Color.White, AlexInk, "mic", "Dictar") { send(draft) }
            RoundButton(AlexBlue, Color.White, "voice", "Llamar a Alex", onClick = onCall)
        }
    }
}

@Composable
private fun RoundButton(container: Color, tint: Color, kind: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(container).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { AlexIcon(kind, tint, 22.dp) }
}

@Composable
private fun AlexIcon(kind: String, color: Color, side: androidx.compose.ui.unit.Dp = 24.dp) {
    Canvas(Modifier.size(side)) {
        val w = size.width; val h = size.height
        val thick = (w * 0.085f).coerceAtLeast(2f)
        fun line(x: Float, y: Float, a: Float, b: Float) =
            drawLine(color, Offset(x * w, y * h), Offset(a * w, b * h), thick, StrokeCap.Round)
        when (kind) {
            "back" -> { line(.14f, .5f, .86f, .5f); line(.42f, .22f, .14f, .5f); line(.42f, .78f, .14f, .5f) }
            "chat" -> {
                drawRoundRect(color, Offset(w * .12f, h * .18f),
                    androidx.compose.ui.geometry.Size(w * .76f, h * .56f),
                    androidx.compose.ui.geometry.CornerRadius(w * .14f), style = Stroke(thick))
                line(.30f, .74f, .30f, .92f); line(.30f, .92f, .52f, .74f)
            }
            "speaker" -> {
                val body = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * .14f, h * .38f); lineTo(w * .30f, h * .38f); lineTo(w * .50f, h * .18f)
                    lineTo(w * .50f, h * .82f); lineTo(w * .30f, h * .62f); lineTo(w * .14f, h * .62f); close()
                }
                drawPath(body, color, style = Stroke(thick, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                line(.64f, .36f, .64f, .64f); line(.80f, .26f, .80f, .74f)
            }
            "hangup" -> {
                val handset = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * .18f, h * .62f)
                    cubicTo(w * .30f, h * .34f, w * .70f, h * .34f, w * .82f, h * .62f)
                    lineTo(w * .70f, h * .70f)
                    cubicTo(w * .64f, h * .54f, w * .36f, h * .54f, w * .30f, h * .70f); close()
                }
                drawPath(handset, color)
                line(.16f, .18f, .84f, .84f)
            }
            "mic" -> {
                drawRoundRect(color, Offset(w * .36f, h * .12f),
                    androidx.compose.ui.geometry.Size(w * .28f, h * .46f),
                    androidx.compose.ui.geometry.CornerRadius(w * .14f))
                drawArc(color, 0f, 180f, false,
                    Offset(w * .22f, h * .34f), androidx.compose.ui.geometry.Size(w * .56f, h * .44f),
                    style = Stroke(thick, cap = StrokeCap.Round))
                line(.5f, .78f, .5f, .90f)
            }
            // Ecualizador: el mismo signo del botón azul de la referencia.
            else -> {
                line(.22f, .38f, .22f, .62f); line(.40f, .22f, .40f, .78f)
                line(.60f, .30f, .60f, .70f); line(.78f, .40f, .78f, .60f)
            }
        }
    }
}
