package com.example.miprimeraapp

import android.view.Gravity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.*
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

/** Mapbox owns projection, gestures and georeferenced annotations; Compose owns the controls. */
internal class AreaMapController {
    var view: MapView? = null
    private var fills: PolygonAnnotationManager? = null
    private var lines: PolylineAnnotationManager? = null
    private var circles: CircleAnnotationManager? = null
    private var lastPoints: List<GeoVertex> = emptyList()
    private var closed = false
    private var fitRequest = 0
    fun loaded(map: MapView) {
        fills = map.annotations.createPolygonAnnotationManager()
        lines = map.annotations.createPolylineAnnotationManager()
        circles = map.annotations.createCircleAnnotationManager()
        render()
    }
    fun update(points: List<GeoVertex>, complete: Boolean, interactive: Boolean) {
        view?.gestures?.updateSettings {
            scrollEnabled = interactive; pinchToZoomEnabled = interactive
            rotateEnabled = false; pitchEnabled = false; doubleTapToZoomInEnabled = false
        }
        if (points != lastPoints || complete != closed) {
            val fit = complete && !closed
            lastPoints = points.toList(); closed = complete
            render()
            if (fit) view?.post { fit() }
        }
    }
    private fun render() {
        fills?.deleteAll(); lines?.deleteAll(); circles?.deleteAll()
        val coordinates = lastPoints.map { it.point() }
        if (closed && coordinates.size >= 3) fills?.create(PolygonAnnotationOptions()
            .withPoints(listOf(coordinates + coordinates.first())).withFillColor("#0085FF").withFillOpacity(0.12))
        if (coordinates.size >= 2) lines?.create(PolylineAnnotationOptions()
            .withPoints(if (closed) coordinates + coordinates.first() else coordinates)
            .withLineColor("#0085FF").withLineWidth(3.0))
        circles?.create(coordinates.map { CircleAnnotationOptions().withPoint(it)
            .withCircleRadius(if (closed) 7.0 else 5.0).withCircleColor("#80BEDB")
            .withCircleStrokeColor("#FFFFFF").withCircleStrokeWidth(3.0) })
    }
    fun isFirst(point: GeoVertex): Boolean {
        val map = view ?: return false
        if (lastPoints.size < 3) return false
        val a = map.mapboxMap.pixelForCoordinate(lastPoints.first().point())
        val b = map.mapboxMap.pixelForCoordinate(point.point())
        return hypot(a.x-b.x,a.y-b.y) < 22 * map.resources.displayMetrics.density
    }
    fun zoom(delta: Double) { view?.mapboxMap?.let { it.setCamera(CameraOptions.Builder().zoom((it.cameraState.zoom + delta).coerceIn(3.0,20.0)).build()) } }
    fun reset() { view?.mapboxMap?.setCamera(CameraOptions.Builder().center(OceanCenter.point()).zoom(10.46011603411317).bearing(0.0).pitch(0.0).build()) }
    fun fit() {
        val map = view ?: return
        if (lastPoints.size < 3 || map.width == 0 || map.height == 0) return
        val request = ++fitRequest
        map.mapboxMap.cameraForCoordinates(lastPoints.map { it.point() },
            CameraOptions.Builder().bearing(0.0).pitch(0.0).build(),
            EdgeInsets(map.height * .22, map.width * .20, map.height * .25, map.width * .20),
            18.0, null) { camera ->
            if (view === map && closed && request == fitRequest) map.mapboxMap.setCamera(camera)
        }
    }
    fun layoutChanged() { if (closed) fit() }
    fun release() { fills = null; lines = null; circles = null; view = null }
}

@Composable
internal fun NativeAreaMap(
    controller: AreaMapController, points: List<GeoVertex>, closed: Boolean,
    interactive: Boolean, modifier: Modifier, miniature: Boolean = false,
    onTap: (GeoVertex) -> Unit = {}, onReady: () -> Unit = {}, onError: () -> Unit = {},
) {
    val tap by rememberUpdatedState(onTap)
    val ready by rememberUpdatedState(onReady)
    val error by rememberUpdatedState(onError)
    val canTap by rememberUpdatedState(interactive)
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
