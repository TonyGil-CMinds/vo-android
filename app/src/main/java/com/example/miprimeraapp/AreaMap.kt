package com.example.miprimeraapp

import android.os.SystemClock
import android.view.Gravity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.plugin.annotation.Annotation
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlin.math.*

internal const val OceanStyle = "mapbox://styles/tonygil1/cmu4p1p5t006b01sa847xh68d"
internal val OceanCenter = GeoVertex(-110.35242858294092, 24.25292356062559)
private fun GeoVertex.point() = Point.fromLngLat(longitude, latitude)
private fun lerp(a: GeoVertex, b: GeoVertex, t: Float) = GeoVertex(
    a.longitude + (b.longitude - a.longitude) * t,
    a.latitude + (b.latitude - a.latitude) * t,
)

/** Entrada del punto con un rebote corto: nace en 0 y se pasa un poco antes de asentarse. */
private fun pop(t: Float): Float {
    val c = 2.70158f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

private fun easeOut(t: Float) = 1f - (1f - t).pow(3)

private const val VertexMillis = 260f
private const val EdgeMillis = 190f
private const val SeamMillis = 320f

/** Mapbox owns projection, gestures and georeferenced annotations; Compose owns the controls. */
internal class AreaMapController(private val miniature: Boolean = false) {
    var view: MapView? = null
    private var fills: PolygonAnnotationManager? = null
    private var lines: PolylineAnnotationManager? = null
    private var circles: CircleAnnotationManager? = null
    private var reportCircles: CircleAnnotationManager? = null
    private var reportPoints: List<ReportMapPoint> = emptyList()
    var onReportPoint: (ReportMapPoint) -> Unit = {}

    /** Se avisa al soltar, no durante el arrastre: recomponer a cada frame rompería el gesto. */
    var onVerticesMoved: (List<GeoVertex>) -> Unit = {}
    var verticesDraggable = false

    private var lastPoints: List<GeoVertex> = emptyList()
    private var closed = false
    private var fitRequest = 0
    private var fitUntil = 0L

    private var vertices: List<CircleAnnotation> = emptyList()
    private var outline: PolylineAnnotation? = null
    private var surface: PolygonAnnotation? = null
    private var appear = FloatArray(0)
    private var edge = 1f
    private var seam = 1f
    private var dragging = -1
    private var frameQueued = false
    private var lastFrame = 0L

    fun loaded(map: MapView) {
        fills = map.annotations.createPolygonAnnotationManager()
        lines = map.annotations.createPolylineAnnotationManager()
        circles = map.annotations.createCircleAnnotationManager().also { manager ->
            manager.addDragListener(object : OnCircleAnnotationDragListener {
                override fun onAnnotationDragStarted(annotation: Annotation<*>) {
                    dragging = vertices.indexOfFirst { it.id == annotation.id }
                    paint()
                }
                override fun onAnnotationDrag(annotation: Annotation<*>) {
                    val index = vertices.indexOfFirst { it.id == annotation.id }
                    if (index < 0) return
                    val moved = (annotation as? CircleAnnotation)?.point ?: return
                    lastPoints = lastPoints.toMutableList()
                        .also { it[index] = GeoVertex(moved.longitude(), moved.latitude()) }
                    paint()
                }
                override fun onAnnotationDragFinished(annotation: Annotation<*>) {
                    dragging = -1
                    paint()
                    onVerticesMoved(lastPoints)
                }
            })
        }
        reportCircles = map.annotations.createCircleAnnotationManager().also { manager ->
            manager.addClickListener { annotation ->
                reportPoints.firstOrNull { it.position.point() == annotation.point }?.let(onReportPoint)
                true
            }
        }
        rebuild()
        renderReportPoints()
    }

    fun setReportPoints(points: List<ReportMapPoint>) {
        if (points == reportPoints) return
        reportPoints = points
        renderReportPoints()
    }

    private fun renderReportPoints() {
        reportCircles?.deleteAll()
        reportCircles?.create(reportPoints.map { CircleAnnotationOptions().withPoint(it.position.point())
            .withCircleRadius(if (miniature) 3.0 else 7.0).withCircleColor(it.color)
            .withCircleStrokeColor("#FFFFFF").withCircleStrokeWidth(if (miniature) 1.2 else 2.5) })
    }

    fun update(points: List<GeoVertex>, complete: Boolean, interactive: Boolean) {
        view?.gestures?.updateSettings {
            scrollEnabled = interactive
            pinchToZoomEnabled = interactive
            // Dos dedos cambian la perspectiva: girar y inclinar el plano.
            rotateEnabled = interactive
            pitchEnabled = interactive
            doubleTapToZoomInEnabled = false
        }
        if (dragging >= 0) return
        if (points == lastPoints && complete == closed) return
        val appended = points.size == lastPoints.size + 1 && lastPoints == points.dropLast(1)
        val closing = complete && !closed
        val previous = appear
        lastPoints = points.toList()
        appear = FloatArray(points.size) { if (appended && it == points.lastIndex) 0f else previous.getOrElse(it) { 1f } }
        edge = if (appended && points.size >= 2) 0f else 1f
        if (closing) { seam = 0f; fitUntil = SystemClock.uptimeMillis() + 1200 }
        if (!complete) seam = 1f
        closed = complete
        if (miniature) { appear.fill(1f); edge = 1f; seam = 1f }
        rebuild()
        if (closing) view?.post { fit() }
        schedule()
    }

    /** Recrea las anotaciones sólo cuando cambia la lista; el resto de los frames sólo las actualiza. */
    private fun rebuild() {
        val manager = circles ?: return
        manager.deleteAll()
        vertices = manager.create(lastPoints.map {
            CircleAnnotationOptions().withPoint(it.point()).withCircleRadius(0.1)
                .withCircleColor("#80BEDB").withCircleStrokeColor("#FFFFFF").withCircleStrokeWidth(0.0)
                .withDraggable(verticesDraggable && !miniature)
        })
        lines?.deleteAll()
        outline = if (lastPoints.size >= 2) lines?.create(
            PolylineAnnotationOptions().withPoints(lastPoints.map { it.point() })
                .withLineColor("#0085FF").withLineWidth(3.0),
        ) else null
        fills?.deleteAll()
        surface = if (closed && lastPoints.size >= 3) fills?.create(
            PolygonAnnotationOptions().withPoints(listOf(lastPoints.map { it.point() } + lastPoints.first().point()))
                .withFillColor("#0085FF").withFillOpacity(0.0),
        ) else null
        paint()
    }

    private fun schedule() {
        if (frameQueued || miniature) return
        frameQueued = true
        lastFrame = 0L
        view?.postOnAnimation(::frame)
    }

    private fun frame() {
        frameQueued = false
        val now = SystemClock.uptimeMillis()
        val delta = if (lastFrame == 0L) 16f else (now - lastFrame).toFloat()
        lastFrame = now
        var running = false
        for (i in appear.indices) if (appear[i] < 1f) {
            appear[i] = min(1f, appear[i] + delta / VertexMillis); running = true
        }
        if (edge < 1f) { edge = min(1f, edge + delta / EdgeMillis); running = true }
        if (seam < 1f) { seam = min(1f, seam + delta / SeamMillis); running = true }
        paint()
        if (running) { frameQueued = true; view?.postOnAnimation(::frame) } else lastFrame = 0L
    }

    private fun paint() {
        val base = if (miniature) 3.0 else if (closed) 7.0 else 5.0
        val stroke = if (miniature) 1.2 else 3.0
        vertices.forEachIndexed { index, annotation ->
            val grown = pop(appear.getOrElse(index) { 1f }).coerceAtLeast(0f)
            val held = if (index == dragging) 1.45 else 1.0
            annotation.point = lastPoints.getOrNull(index)?.point() ?: annotation.point
            annotation.circleRadius = base * grown * held
            annotation.circleStrokeWidth = stroke * grown
        }
        if (vertices.isNotEmpty()) circles?.update(vertices)

        outline?.let { line ->
            val drawn = when {
                lastPoints.size < 2 -> lastPoints
                closed -> {
                    val ring = lastPoints + lerp(lastPoints.last(), lastPoints.first(), easeOut(seam))
                    if (seam >= 1f) lastPoints + lastPoints.first() else ring
                }
                edge >= 1f -> lastPoints
                // El último tramo se va pintando hacia el punto recién puesto.
                else -> lastPoints.dropLast(1) +
                    lerp(lastPoints[lastPoints.size - 2], lastPoints.last(), easeOut(edge))
            }
            line.points = drawn.map { it.point() }
            lines?.update(line)
        }
        surface?.let { polygon ->
            polygon.points = listOf(lastPoints.map { it.point() } + lastPoints.first().point())
            polygon.fillOpacity = 0.12 * easeOut(seam)
            fills?.update(polygon)
        }
    }

    fun isFirst(point: GeoVertex): Boolean {
        val map = view ?: return false
        if (lastPoints.size < 3) return false
        val a = map.mapboxMap.pixelForCoordinate(lastPoints.first().point())
        val b = map.mapboxMap.pixelForCoordinate(point.point())
        return hypot(a.x-b.x,a.y-b.y) < 22 * map.resources.displayMetrics.density
    }

    /** Devuelve el trazado al estado que Compose considera válido tras un arrastre rechazado. */
    fun restore(points: List<GeoVertex>) {
        lastPoints = points.toList()
        appear = FloatArray(points.size) { 1f }
        edge = 1f; seam = 1f
        rebuild()
    }

    fun zoom(delta: Double) { view?.mapboxMap?.let { it.setCamera(CameraOptions.Builder().zoom((it.cameraState.zoom + delta).coerceIn(3.0,20.0)).build()) } }
    fun reset() { view?.mapboxMap?.setCamera(CameraOptions.Builder().center(OceanCenter.point()).zoom(10.46011603411317).bearing(0.0).pitch(0.0).build()) }
    fun fit() {
        val map = view ?: return
        if (lastPoints.size < 3 || map.width == 0 || map.height == 0) return
        val request = ++fitRequest
        val camera = map.mapboxMap.cameraState
        // Encuadra conservando la perspectiva que el usuario haya elegido con dos dedos.
        map.mapboxMap.cameraForCoordinates(lastPoints.map { it.point() },
            CameraOptions.Builder().bearing(camera.bearing).pitch(camera.pitch).build(),
            EdgeInsets(map.height * .22, map.width * .20, map.height * .25, map.width * .20),
            18.0, null) { fitted ->
            if (view === map && closed && request == fitRequest) map.mapboxMap.setCamera(fitted)
        }
    }
    // Sólo mientras el mapa se reacomoda al cerrar; después el encuadre es cosa del usuario.
    fun layoutChanged() { if (closed && SystemClock.uptimeMillis() < fitUntil) fit() }
    fun release() {
        fills = null; lines = null; circles = null; reportCircles = null; view = null
        vertices = emptyList(); outline = null; surface = null
    }
}

@Composable
internal fun NativeAreaMap(
    controller: AreaMapController, points: List<GeoVertex>, closed: Boolean,
    interactive: Boolean, modifier: Modifier, miniature: Boolean = false, addPoints: Boolean = interactive,
    draggableVertices: Boolean = false,
    onTap: (GeoVertex) -> Unit = {}, onVerticesMoved: (List<GeoVertex>) -> Unit = {},
    onReady: () -> Unit = {}, onError: () -> Unit = {},
) {
    val tap by rememberUpdatedState(onTap)
    val ready by rememberUpdatedState(onReady)
    val error by rememberUpdatedState(onError)
    val canTap by rememberUpdatedState(addPoints)
    val moved by rememberUpdatedState(onVerticesMoved)
    SideEffect {
        controller.verticesDraggable = draggableVertices
        controller.onVerticesMoved = { moved(it) }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    AndroidView(modifier = modifier, factory = { context ->
        MapView(context, MapInitOptions(context, textureView = true)).apply {
            controller.view = this
            val d = resources.displayMetrics.density
            logo.updateSettings { position = Gravity.TOP or Gravity.START; marginTop = (if (miniature) 8f else 110f)*d; marginLeft = 18*d }
            attribution.updateSettings { position = Gravity.BOTTOM or Gravity.START; marginLeft = 12*d; marginBottom = (if (miniature) 6f else 155f)*d }
            compass.updateSettings { enabled = false }
            scalebar.updateSettings { enabled = false }
            controller.reset()
            gestures.addOnMapClickListener { p -> if (canTap) tap(GeoVertex(p.longitude(),p.latitude())); true }
            mapboxMap.subscribeMapLoadingError { error() }
            mapboxMap.loadStyle(OceanStyle) { controller.loaded(this); ready() }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> controller.layoutChanged() }
        }
    }, update = { controller.update(points, closed, interactive) }, onRelease = { it.onStop(); it.onDestroy() })
}
