package com.example.miprimeraapp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val ReportBlue = Color(0xFF0085FF)
private val ReportIce = Color(0xFFE0FFFF)
private val ReportGrey = Color(0xFF828B8B)

internal enum class ReportTab(val label: Int, val icon: Int) {
    SUMMARY(R.string.report_summary, R.drawable.ic_navbar_resumen),
    BIODIVERSITY(R.string.report_biodiversity, R.drawable.ic_navbar_biodiversidad),
    ACTIVITIES(R.string.report_activities, R.drawable.ic_navbar_actividades),
    PROPOSAL(R.string.report_proposal, R.drawable.ic_navbar_propuesta),
    AGENT(R.string.report_agent, R.drawable.ic_navbar_agente),
}

internal data class ReportBlock(val title: Int, val body: Int, val map: Boolean = false, val statuses: Boolean = false)
internal fun reportBlocks(tab: ReportTab) = when (tab) {
    ReportTab.SUMMARY -> listOf(
        ReportBlock(R.string.report_executive, R.string.report_executive_body, map = true),
        ReportBlock(R.string.report_context, R.string.report_context_body),
        ReportBlock(R.string.report_findings, R.string.report_findings_body),
        ReportBlock(R.string.report_source, R.string.report_source_body))
    ReportTab.BIODIVERSITY -> listOf(
        ReportBlock(R.string.report_richness, R.string.report_richness_body, map = true),
        ReportBlock(R.string.report_status, R.string.report_status_body, statuses = true),
        ReportBlock(R.string.report_habitats, R.string.report_habitats_body))
    ReportTab.ACTIVITIES -> listOf(
        ReportBlock(R.string.report_community, R.string.report_community_body, map = true),
        ReportBlock(R.string.report_fishing, R.string.report_fishing_body),
        ReportBlock(R.string.report_tourism, R.string.report_tourism_body))
    ReportTab.PROPOSAL -> listOf(
        ReportBlock(R.string.report_polygon, R.string.report_polygon_body, map = true),
        ReportBlock(R.string.report_objectives, R.string.report_objectives_body),
        ReportBlock(R.string.report_management, R.string.report_management_body))
    ReportTab.AGENT -> listOf(
        ReportBlock(R.string.report_assistant, R.string.report_assistant_body),
        ReportBlock(R.string.report_next_steps, R.string.report_next_steps_body),
        ReportBlock(R.string.report_source, R.string.report_source_body))
}

@Composable
internal fun ReportReader(
    area: AreaGeometry,
    modifier: Modifier = Modifier,
    title: String = "",
    onAgent: () -> Unit = {},
    onReadingProgress: (Float) -> Unit = {},
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val states = ReportTab.entries.map { key(it) { rememberLazyListState() } }
    val tab = ReportTab.entries[selected]
    val state = states[selected]
    val blocks = remember(tab) { reportBlocks(tab) }
    var iconsVisible by remember { mutableStateOf(true) }
    var entered by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 12.dp.toPx() }
    val collapseDistance = with(LocalDensity.current) { 120.dp.toPx() }
    val readingProgress by remember(state, collapseDistance) { derivedStateOf {
        if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
    } }
    SideEffect { onReadingProgress(readingProgress) }
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            var travel = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (travel * available.y < 0) travel = 0f
                    travel += available.y
                    if (abs(travel) > threshold) { iconsVisible = travel > 0; travel = 0f }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(selected) { iconsVisible = true }
    // La cabeza de lectura: el trazo azul llega hasta aquí, y de ahí sale el relleno de cada bloque.
    val anchor = with(LocalDensity.current) { 200.dp.toPx() }
    var reader by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mapSource by remember { mutableStateOf<Rect?>(null) }
    val grow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFFF4FFFF), ReportIce)))
        .onGloballyPositioned { reader = it }) {
        Column(Modifier.fillMaxSize()) {
        // El origen queda fuera del viewport de lectura: nunca se recorta al deslizar.
        ReportRoute(tab.icon)
        LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(connection),
            contentPadding = PaddingValues(bottom = 140.dp)) {
            itemsIndexed(blocks, key = { _, block -> block.title }) { index, block ->
                val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                // Sin escalones: el relleno es la parte del bloque que ya pasó la cabeza de lectura.
                val fill = if (item == null) (if (index < state.firstVisibleItemIndex) 1f else 0f)
                    else ((anchor - item.offset) / item.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                ReportTimelineBlock(block, fill) {
                    if (block.map) {
                        ReportMapCard(area, tab, reader) { rect ->
                            mapSource = rect
                            scope.launch { grow.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(stringResource(block.body), color = Color(0xFF626D70), fontSize = 15.sp, lineHeight = 23.sp)
                    if (tab == ReportTab.PROPOSAL && index == 0) {
                        Spacer(Modifier.height(16.dp))
                        Text(String.format(Locale.US, "%.2f km²  ·  %.2f km", area.squareMeters / 1_000_000, area.perimeterMeters / 1000),
                            fontWeight = FontWeight.Bold, color = ReportBlue, fontSize = 18.sp)
                        Text(stringResource(R.string.report_measurements), fontSize = 12.sp, color = ReportGrey)
                    }
                    if (block.statuses) ConservationStatuses()
                    if (tab == ReportTab.AGENT && index == 0) AgentQuestions()
                }
            }
        }
        }
        AnimatedVisibility(entered, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(700, delayMillis = 100)) { it } + fadeIn(tween(500)),
            exit = fadeOut()) {
            Column(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, .28f to ReportIce, 1f to ReportIce))
                .navigationBarsPadding().padding(top = 40.dp, bottom = 10.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                val weights = listOf(1f, 1.3f, 1f, 1f, 1f)
                val target = (weights.take(selected).sum() + weights[selected] / 2f) / weights.sum()
                val indicator by animateFloatAsState(target, tween(480, easing = FastOutSlowInEasing), label = "Shared active indicator")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    ReportTab.entries.forEachIndexed { index, entry ->
                        val active = selected == index
                        val color by animateColorAsState(if (active) ReportBlue else ReportGrey, tween(220), label = "Tab color")
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        // El icono se hunde bajo el dedo y rebota al quedar elegido.
                        val press by animateFloatAsState(if (pressed) 0.88f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "Tab press")
                        val pop = remember { Animatable(1f) }
                        val lift by animateFloatAsState(if (active) -3f else 0f, spring(stiffness = Spring.StiffnessLow), label = "Tab lift")
                        LaunchedEffect(active) {
                            if (active) { pop.snapTo(0.78f); pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                        }
                        Column(Modifier.weight(if (entry == ReportTab.BIODIVERSITY) 1.3f else 1f)
                            // Agente no es una pestaña más: abre el chat a pantalla completa.
                            .selectable(active, interaction, null, role = Role.Tab, onClick = {
                                if (entry == ReportTab.AGENT) onAgent() else selected = index
                            })
                            .heightIn(min = 48.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedVisibility(iconsVisible,
                                enter = expandVertically(tween(280)) + fadeIn(tween(220)),
                                exit = shrinkVertically(tween(280)) + fadeOut(tween(180))) {
                                Icon(painterResource(entry.icon), null,
                                    Modifier.padding(bottom = 12.dp).size(25.dp).graphicsLayer {
                                        scaleX = press * pop.value; scaleY = press * pop.value
                                        translationY = lift.dp.toPx()
                                    }, tint = color)
                            }
                            Text(stringResource(entry.label), color = color, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Spacer(Modifier.size(6.dp))
                        }
                    }
                }
                // Un único punto viaja entre los centros, incluso con los iconos recogidos.
                Box(Modifier.align(Alignment.BottomStart).offset(x = maxWidth * indicator - 3.dp, y = (-8).dp)
                    .size(6.dp).background(ReportBlue, CircleShape))
                }
            }
        }
        // Encima de la navbar: al crecer, el mapa cubre toda la lectura.
        mapSource?.let { source ->
            ExpandedMap(area, tab, source, grow.value, onClose = {
                scope.launch { grow.animateTo(0f, tween(460, easing = FastOutSlowInEasing)); mapSource = null }
            })
        }
    }
}

@Composable
private fun ReportRoute(icon: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), null, Modifier.size(21.dp), tint = ReportBlue)
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val x = 32.dp.toPx(); val y = size.height - 17.dp.toPx()
            val path = Path().apply {
                moveTo(size.width / 2, 5.dp.toPx()); lineTo(size.width / 2, y - 20.dp.toPx())
                quadraticTo(size.width / 2, y, size.width / 2 - 22.dp.toPx(), y)
                lineTo(x + 18.dp.toPx(), y); quadraticTo(x, y, x, size.height)
            }
            drawPath(path, ReportBlue, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun ReportTimelineBlock(block: ReportBlock, fill: Float, content: @Composable ColumnScope.() -> Unit) {
    // El color sigue al deslizamiento, no a un salto de estado: al subir vuelve por donde vino.
    val color = lerp(Color.Black, ReportBlue, (fill * 8f).coerceIn(0f, 1f))
    Box(Modifier.fillMaxWidth().drawBehindTimeline(fill)) {
        Column(Modifier.padding(start = 58.dp, end = 24.dp, bottom = 28.dp)) {
            Text(stringResource(block.title), color = color, fontWeight = FontWeight.Bold,
                fontSize = 19.sp, lineHeight = 25.sp, modifier = Modifier.padding(bottom = 14.dp).semantics { heading() })
            content()
        }
    }
}

private fun Modifier.drawBehindTimeline(fill: Float): Modifier = this.then(
    Modifier.drawBehind {
        val x = 32.dp.toPx()
        val dotY = 13.dp.toPx()
        val dot = Offset(x, dotY)
        val drawn = size.height * fill
        drawLine(Color(0xFFA5B5B5), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
        // Arranca en 0 y termina en size.height: el trazo de un bloque continúa el del anterior.
        if (drawn > 0f) drawLine(ReportBlue, Offset(x, 0f), Offset(x, drawn), 2.dp.toPx(), cap = StrokeCap.Round)
        // El punto se enciende cuando el trazo lo alcanza, y la onda acompaña esa llegada.
        val arrival = ((drawn - dotY) / 30.dp.toPx()).coerceIn(0f, 1f)
        if (arrival > 0f && arrival < 1f) {
            drawCircle(ReportBlue.copy(alpha = (1f - arrival) * 0.30f), 6.dp.toPx() + arrival * 11.dp.toPx(), dot)
        }
        drawCircle(lerp(Color(0xFF9CA5A5), ReportBlue, arrival), 5.dp.toPx() + arrival * 1.4.dp.toPx(), dot)
    })

@Composable
private fun ConservationStatuses() {
    val entries = listOf(
        R.drawable.icon_peligrocritico to R.string.report_cr,
        R.drawable.icon_peligrocritico_enpeligro to R.string.report_en,
        R.drawable.icon_peligrocritico_vulnerable to R.string.report_vu,
        R.drawable.icon_casiamenazado to R.string.report_nt,
        R.drawable.icon_navbar_preocupacionmenor to R.string.report_lc,
        R.drawable.icon_datosinsuficientes to R.string.report_dd,
        R.drawable.icon_noevaluado to R.string.report_ne)
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        entries.forEach { (icon, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), null, Modifier.size(32.dp))
                Text(stringResource(label), Modifier.padding(start = 10.dp), fontSize = 13.sp, color = Color(0xFF536064))
            }
        }
    }
}

@Composable
private fun AgentQuestions() {
    var answer by rememberSaveable { mutableIntStateOf(-1) }
    val questions = listOf(R.string.report_question_species, R.string.report_question_activities, R.string.report_question_data)
    val answers = listOf(R.string.report_answer_species, R.string.report_answer_activities, R.string.report_answer_data)
    Column(Modifier.padding(top = 16.dp)) {
        questions.forEachIndexed { index, question ->
            OutlinedButton(onClick = { answer = if (answer == index) -1 else index }, Modifier.fillMaxWidth()) {
                Text(stringResource(question), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            AnimatedVisibility(answer == index) {
                Text(stringResource(answers[index]), Modifier.padding(vertical = 12.dp), fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFF536064))
            }
        }
    }
}

@Composable
private fun ReportMapCard(area: AreaGeometry, tab: ReportTab, reader: LayoutCoordinates?, onExpand: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxWidth().height(125.dp).clip(RoundedCornerShape(10.dp))
        // El rect vivo de la tarjeta es el origen del crecimiento; no se supone, se mide.
        .onGloballyPositioned { if (reader != null) bounds = reader.localBoundingBoxOf(it) }) {
        ReportMap(area, tab, false, Modifier.fillMaxSize())
        TextButton(onClick = { onExpand(bounds) }, Modifier.align(Alignment.TopEnd).padding(5.dp)
            .background(Color.White.copy(alpha = .95f), CircleShape)) {
            Text(stringResource(R.string.report_explore), fontSize = 11.sp)
        }
    }
    Text(stringResource(R.string.report_map_caption), fontSize = 10.sp, lineHeight = 14.sp, color = ReportGrey,
        modifier = Modifier.padding(top = 5.dp))
}

/** El mapa crece desde el rect de su tarjeta hasta llenar la lectura: el mismo mapa, no otro que aparece. */
@Composable
private fun ExpandedMap(area: AreaGeometry, tab: ReportTab, source: Rect, grow: Float, onClose: () -> Unit) {
    val density = LocalDensity.current
    BackHandler(enabled = true, onBack = onClose)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullWidth = with(density) { maxWidth.toPx() }
        val fullHeight = with(density) { maxHeight.toPx() }
        val width = source.width + (fullWidth - source.width) * grow
        val height = source.height + (fullHeight - source.height) * grow
        Box(Modifier.fillMaxSize().background(Color(0xFF06283D).copy(alpha = 0.32f * grow))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
        Box(
            Modifier.offset { IntOffset((source.left * (1f - grow)).roundToInt(), (source.top * (1f - grow)).roundToInt()) }
                .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .clip(RoundedCornerShape(10.dp * (1f - grow))),
        ) {
            ReportMap(area, tab, true, Modifier.fillMaxSize(), chrome = ((grow - 0.55f) / 0.45f).coerceIn(0f, 1f))
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp)
                    .alpha(grow).background(Color.White, CircleShape),
            ) { Text("✕", fontSize = 15.sp, color = Color(0xFF14366F)) }
        }
    }
}

@Composable
private fun ReportMap(area: AreaGeometry, tab: ReportTab, interactive: Boolean, modifier: Modifier = Modifier, chrome: Float = 1f) {
    val labelIds = when (tab) {
        ReportTab.BIODIVERSITY -> listOf(R.string.report_layer_corals, R.string.report_layer_mangroves, R.string.report_layer_species)
        ReportTab.ACTIVITIES -> listOf(R.string.report_layer_fishing, R.string.report_layer_diving, R.string.report_layer_tourism)
        else -> listOf(R.string.report_layer_habitats, R.string.report_layer_community, R.string.report_layer_monitoring)
    }
    val labels = labelIds.map { stringResource(it) }
    val colors = listOf("#0085FF", "#18AB89", "#F2A044")
    val allPoints = remember(area, labels) { reportDemoPoints(area, labels, colors) }
    var layer by rememberSaveable(tab) { mutableIntStateOf(-1) }
    var selected by remember { mutableStateOf<ReportMapPoint?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val controller = remember(retry) { AreaMapController(miniature = !interactive) }
    val token = stringResource(R.string.mapbox_access_token).startsWith("pk.")
    val points = if (layer < 0) allPoints else allPoints.filter { it.label == labels[layer] }
    SideEffect { controller.onReportPoint = { selected = it }; controller.setReportPoints(points) }
    Box(modifier.background(Color(0xFFB9DBE9))) {
        if (token) key(retry) {
            NativeAreaMap(controller, area.vertices, true, interactive, Modifier.fillMaxSize(), miniature = true,
                onReady = { ready = true; failed = false; controller.fit() }, onError = { if (!ready) failed = true })
        }
        if (!token || failed) Column(Modifier.align(Alignment.Center).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.report_map_unavailable), fontSize = 12.sp)
            if (token) TextButton(onClick = { ready = false; failed = false; retry++ }) { Text(stringResource(R.string.report_retry)) }
        } else if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center).size(22.dp), strokeWidth = 2.dp)
        // Los controles esperan a que el mapa termine de crecer; no viajan encogidos con él.
        if (interactive && ready && chrome > 0.01f) Box(Modifier.fillMaxSize().alpha(chrome)) {
            Row(Modifier.align(Alignment.TopCenter).padding(top = 42.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { i, label -> FilterChip(selected = layer == i, onClick = { layer = if (layer == i) -1 else i; selected = null },
                    label = { Text(label) }, leadingIcon = { Box(Modifier.size(8.dp).background(Color(android.graphics.Color.parseColor(colors[i])), CircleShape)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.White, selectedContainerColor = ReportIce)) }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(12.dp).background(Color.White, RoundedCornerShape(20.dp))) {
                IconButton(onClick = { controller.zoom(1.0) }) { Text("+", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "Acercar mapa" }) }
                IconButton(onClick = { controller.zoom(-1.0) }) { Text("−", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "Alejar mapa" }) }
                IconButton(onClick = { controller.fit() }) { Icon(painterResource(R.drawable.ic_mylocation), stringResource(R.string.report_recenter), Modifier.size(22.dp), tint = ReportBlue) }
            }
            Surface(Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 42.dp), shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(Modifier.padding(16.dp)) {
                    Text(selected?.label ?: stringResource(R.string.report_touch_point), fontWeight = FontWeight.Bold, color = ReportBlue)
                    Text(selected?.let { reportCoordinates(it.position) } ?: stringResource(R.string.report_points_demo), fontSize = 12.sp, color = ReportGrey)
                }
            }
        }
    }
}
