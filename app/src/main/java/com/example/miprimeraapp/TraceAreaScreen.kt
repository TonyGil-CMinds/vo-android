package com.example.miprimeraapp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

private val TraceBlue = Color(0xFF0085FF)
private val TraceInk = Color(0xFF14366F)
private val TraceIce = Color(0xFFDBFFFF)
private val TraceHeading = FontFamily(Font(R.font.bowlby_one_regular))

@Composable
fun TraceAreaScreen(onBack: () -> Unit, repository: AreaInformationRepository = remember { DemoAreaInformationRepository() }) {
    var serialized by rememberSaveable { mutableStateOf("") }
    val points = remember(serialized) { serialized.split(';').filter { it.isNotBlank() }.map {
        val p = it.split(','); GeoVertex(p[0].toDouble(),p[1].toDouble())
    } }
    fun store(points: List<GeoVertex>) { serialized = points.joinToString(";") { "${it.longitude},${it.latitude}" } }
    var tutorial by rememberSaveable { mutableStateOf(true) }
    var complete by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showNext by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var information by remember { mutableStateOf<AreaInformation?>(null) }
    val controller = remember(retry) { AreaMapController() }
    val tokenPresent = stringResource(R.string.mapbox_access_token).startsWith("pk.")
    val area = remember(points, complete) { if (complete) AreaMath.measure(points) else null }
    fun finish() {
        val problem = AreaMath.validationError(points)
        if (problem != null) notice = problem else { complete = true; expanded = false; notice = null }
    }
    fun back() {
        when { complete -> { complete = false; expanded = false }
            points.isNotEmpty() -> confirmLeave = true
            else -> onBack() }
    }
    BackHandler { back() }
    LaunchedEffect(area) {
        information = null
        if (area != null) {
            try { information = repository.evaluate(area) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { notice = "No pudimos consultar la información. Puedes volver a intentarlo." }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(TraceIce)) {
        val viewport = maxHeight
        val mapHeight by animateDpAsState(if (complete) viewport * 0.55f else viewport, tween(650), label = "Map to summary")
        if (tokenPresent) key(retry) {
            NativeAreaMap(controller, points, complete, !tutorial && !complete && ready,
                Modifier.fillMaxWidth().height(mapHeight).then(if (tutorial) Modifier.blur(4.dp) else Modifier),
                onTap = { point ->
                    when {
                        controller.isFirst(point) -> finish()
                        points.size >= 100 -> notice = "Puedes usar hasta 100 puntos por área."
                        points.any { AreaMath.distance(it,point) < 1 } -> notice = "Ese punto está demasiado cerca de otro."
                        else -> { store(points + point); notice = null }
                    }
                }, onReady = { ready = true; failed = false }, onError = { if (!ready) failed = true })
        }
        Box(Modifier.fillMaxWidth().height(if (complete) 100.dp else 230.dp)
            .align(if (complete) Alignment.TopCenter else Alignment.BottomCenter)
            .then(if (complete) Modifier.offset(y = mapHeight - 100.dp) else Modifier)
            .background(Brush.verticalGradient(listOf(Color.Transparent, TraceIce))))

        if (!complete && !tutorial) {
            IconButton(onClick = { back() }, Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart)
                .background(Color.White, CircleShape)) { TraceIcon("back", TraceInk) }
        }
        if (!complete) Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 50.dp, end = 20.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .96f))) {
            IconButton(onClick = { controller.zoom(1.0) }, enabled = !tutorial && ready) { Text("+", fontSize = 24.sp, color = TraceInk) }
            IconButton(onClick = { controller.zoom(-1.0) }, enabled = !tutorial && ready) { Text("−", fontSize = 24.sp, color = TraceInk) }
            HorizontalDivider(Modifier.width(28.dp).align(Alignment.CenterHorizontally))
            IconButton(onClick = { controller.reset() }, enabled = !tutorial && ready) { TraceIcon("center", TraceInk) }
        }
        if ((!ready || failed || !tokenPresent) && !tutorial) {
            Surface(Modifier.align(Alignment.Center).padding(32.dp), shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (!tokenPresent) "Falta configurar Mapbox." else if (failed) "No se pudo cargar el mapa. Revisa tu conexión." else "Cargando mapa…", textAlign = TextAlign.Center)
                    if (failed) TextButton(onClick = { ready = false; failed = false; retry++ }) { Text("Reintentar") }
                    else if (tokenPresent) CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                }
            }
        }

        if (complete && area != null) {
            Column(Modifier.fillMaxWidth().padding(top = viewport * .46f, bottom = 140.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { complete = false; expanded = false }) { TraceIcon("back", Color(0xFF282727)) }
                    Text("ÁREA\nTRAZADA", fontFamily = TraceHeading, fontSize = 26.sp, lineHeight = 26.sp, color = Color.Black)
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Calidad de Datos", fontWeight = FontWeight.Bold, color = TraceInk, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(information?.let { "${it.quality}%" } ?: "…", fontWeight = FontWeight.Bold, color = TraceInk, fontSize = 17.sp)
                    TraceIcon(if (expanded) "up" else "down", TraceInk, Modifier.padding(start = 8.dp))
                }
                LinearProgressIndicator(progress = { (information?.quality ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = TraceBlue, trackColor = Color(0xFFD8DEDE), drawStopIndicator = {})
                Text(if (information?.simulated == true) "Datos de demostración" else "", fontSize = 10.sp, color = TraceInk.copy(alpha = .6f), modifier = Modifier.padding(top = 6.dp))
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(top = 10.dp, bottom = 18.dp)) {
                        information?.sources?.forEach { source ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(source.logo), source.name, Modifier.size(23.dp).clip(CircleShape))
                                Text(source.name, Modifier.weight(1f).padding(start = 9.dp), color = Color(0xFF939C9C), fontSize = 14.sp)
                                val color = when (source.level) { InformationLevel.GOOD -> TraceBlue; InformationLevel.LIMITED -> Color(0xFFFE9201); InformationLevel.UNAVAILABLE -> Color(0xFFD94B3A) }
                                Text("${source.percent}%", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Image(painterResource(when (source.level) {
                                    InformationLevel.GOOD -> R.drawable.nivelinformacion_buenresultado
                                    InformationLevel.LIMITED -> R.drawable.nivelinformacion_hayinfonotanbuena
                                    InformationLevel.UNAVAILABLE -> R.drawable.nivelinformacion_noexiste
                                }), when (source.level) { InformationLevel.GOOD -> "Buena información"; InformationLevel.LIMITED -> "Información limitada"; InformationLevel.UNAVAILABLE -> "Información insuficiente" }, Modifier.padding(start = 10.dp).size(15.dp))
                            }
                        }
                    }
                }
                AnimatedVisibility(!expanded) {
                    Column(Modifier.padding(top = 12.dp, bottom = 18.dp)) {
                        Metric("Superficie", String.format(Locale.US,"%.2f km²",area.squareMeters/1_000_000))
                        Metric("Perímetro", String.format(Locale.US,"%.2f km",area.perimeterMeters/1000))
                        Metric("Ubicación", String.format(Locale.US,"%.5f° N\n%.5f° W",area.center.latitude, -area.center.longitude))
                    }
                }
            }
        }
        if (!tutorial) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent,TraceIce,TraceIce)))
            .navigationBarsPadding().padding(top = 20.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            notice?.let { Text(it, Modifier.padding(horizontal = 28.dp, vertical = 8.dp), color = TraceInk, textAlign = TextAlign.Center, fontSize = 12.sp) }
            if (complete) TraceButton(if (expanded) "Continuar" else "Crear MPA", { showNext = true })
            else {
                Text(if (points.size < 3) "Toca el mapa para añadir puntos (${points.size}/3)" else "Toca el primer punto para cerrar el área", color = TraceInk, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { store(points.dropLast(1)); notice = null }, enabled = points.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = TraceInk, contentColor = Color.White), modifier = Modifier.height(52.dp), shape = CircleShape) {
                        TraceIcon("undo", Color.White); Spacer(Modifier.width(8.dp)); Text("Deshacer")
                    }
                    if (points.size >= 3) Button(onClick = { finish() }, colors = ButtonDefaults.buttonColors(containerColor = TraceBlue, contentColor = Color.White), modifier = Modifier.height(52.dp), shape = CircleShape) { Text("Ver área"); Spacer(Modifier.width(6.dp)); TraceIcon("arrow", Color.White) }
                    else Surface(shape = CircleShape, color = Color.White) {
                        Row { IconButton(onClick = { controller.reset() }) { TraceIcon("center", Color.Gray) }
                            IconButton(onClick = { tutorial = true }) { TraceIcon("help", Color.Gray) } }
                    }
                }
            }
            Spacer(Modifier.height(20.dp)); TraceSteps()
        }
        if (tutorial) {
            // Blocks touches on the blurred map, while retaining the map's attribution.
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)).clickable(enabled = true, onClick = {}))
            TutorialCard(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
                onStart = { tutorial = false })
        }
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("¿Salir del trazado?") },
        text = { Text("Se perderán los puntos de esta área.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Seguir trazando") } })
    if (showNext) AlertDialog(onDismissRequest = { showNext = false }, title = { Text("Área preparada") },
        text = { Text("El polígono está listo para el siguiente paso de la demo. Todavía no se ha enviado ni creado una MPA.") },
        confirmButton = { TextButton(onClick = { showNext = false }) { Text("Entendido") } })
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TraceInk)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TraceInk, textAlign = TextAlign.End)
    }
}

@Composable
private fun TraceButton(text: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth(.76f).heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = TraceBlue, contentColor = Color.White),
        shape = CircleShape, elevation = ButtonDefaults.buttonElevation(3.dp)) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp)); TraceIcon("arrow", Color.White)
    }
}

@Composable
private fun TraceSteps() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { Box(Modifier.width(24.dp).height(6.dp).clip(CircleShape).background(if (it < 2) TraceBlue else Color(0xFFD4E3E3))) }
    }
}

@Composable
private fun TutorialCard(modifier: Modifier, onStart: () -> Unit) {
    val controller = remember { AreaMapController() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (isActive) { progress.snapTo(0f); progress.animateTo(4f,tween(3400,easing = LinearEasing)); delay(1300) } }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = Color.White, shadowElevation = 7.dp) {
        Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(166.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF80BEDB))) {
                if (stringResource(R.string.mapbox_access_token).startsWith("pk.")) {
                    NativeAreaMap(controller, emptyList(), false, false, Modifier.fillMaxSize(), miniature = true)
                }
                Canvas(Modifier.fillMaxSize()) {
                    val pts = listOf(Offset(size.width*.47f,size.height*.22f),Offset(size.width*.76f,size.height*.29f),Offset(size.width*.73f,size.height*.82f),Offset(size.width*.43f,size.height*.66f))
                    val path = Path().apply {
                        moveTo(pts[0].x,pts[0].y)
                        for (segment in 0..3) {
                            val t = (progress.value-segment).coerceIn(0f,1f)
                            if (t > 0) { val a=pts[segment]; val b=pts[(segment+1)%4]; lineTo(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t) }
                        }
                    }
                    if (progress.value >= 4) drawPath(path,TraceBlue.copy(alpha=.16f))
                    drawPath(path,TraceBlue,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                    pts.forEachIndexed { index,p -> if (progress.value >= index) {
                        drawCircle(Color.White,5.dp.toPx(),p); drawCircle(TraceBlue,3.dp.toPx(),p)
                    } }
                }
                IconButton(onClick = onStart, Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).background(TraceInk,CircleShape)) { TraceIcon("close",Color.White) }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { TraceIcon("polygon",Color.Black); Spacer(Modifier.width(6.dp)); Text("Trazar Área",fontSize=16.sp,fontWeight=FontWeight.Bold) }
            Text("Toca la pantalla para crear puntos y delimitar tu área. Con 3 o más puntos, toca el primero o pulsa «Ver área». Puedes deshacer el último punto.",
                Modifier.padding(horizontal=24.dp,vertical=14.dp),fontSize=14.sp,lineHeight=20.sp,color=Color.Gray,textAlign=TextAlign.Center)
            TraceButton("Empezar a trazar",onStart)
            Spacer(Modifier.height(20.dp)); TraceSteps(); Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TraceIcon(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp).semantics { contentDescription = when(kind) { "back" -> "Volver"; "center" -> "Centrar en La Paz"; "help" -> "Cómo trazar"; "close" -> "Cerrar tutorial"; "undo" -> "Deshacer"; "up", "down" -> "Detalle de calidad"; else -> "" } }) {
        val w=size.width; val h=size.height
        fun line(x:Float,y:Float,a:Float,b:Float) = drawLine(color,Offset(x*w,y*h),Offset(a*w,b*h),2.dp.toPx(),StrokeCap.Round)
        when(kind) {
            "arrow" -> { line(.12f,.5f,.86f,.5f); line(.58f,.2f,.88f,.5f); line(.58f,.8f,.88f,.5f) }
            "back" -> { line(.12f,.5f,.86f,.5f); line(.42f,.2f,.12f,.5f); line(.42f,.8f,.12f,.5f) }
            "close" -> { line(.25f,.25f,.75f,.75f); line(.75f,.25f,.25f,.75f) }
            "down", "up" -> { val y=if(kind=="down") .7f else .3f; line(.2f,1-y,.5f,y); line(.5f,y,.8f,1-y) }
            "undo" -> { line(.4f,.2f,.15f,.43f);line(.15f,.43f,.4f,.65f); val p=Path().apply { moveTo(w*.15f,h*.43f);lineTo(w*.6f,h*.43f);cubicTo(w*.99f,h*.43f,w*.99f,h*.9f,w*.6f,h*.9f);lineTo(w*.4f,h*.9f) }; drawPath(p,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round)) }
            "center" -> { drawCircle(color,w*.22f,style=Stroke(2.dp.toPx()));line(.5f,.06f,.5f,.19f);line(.5f,.81f,.5f,.94f);line(.06f,.5f,.19f,.5f);line(.81f,.5f,.94f,.5f) }
            "polygon" -> { val p=Path().apply {moveTo(w*.5f,h*.1f);lineTo(w*.85f,h*.3f);lineTo(w*.85f,h*.7f);lineTo(w*.5f,h*.9f);lineTo(w*.15f,h*.7f);lineTo(w*.15f,h*.3f);close()};drawPath(p,color,style=Stroke(1.6.dp.toPx())) }
            else -> { drawCircle(color,w*.4f,style=Stroke(1.6.dp.toPx())); line(.5f,.45f,.5f,.7f); drawCircle(color,1.3.dp.toPx(),Offset(w*.5f,h*.28f)) }
        }
    }
}
