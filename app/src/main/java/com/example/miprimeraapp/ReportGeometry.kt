package com.example.miprimeraapp

import java.util.Locale
import kotlin.math.abs

internal fun reportCoordinates(point: GeoVertex): String = String.format(Locale.US,
    "%.4f° %s  ·  %.4f° %s", abs(point.latitude), if (point.latitude >= 0) "N" else "S",
    abs(point.longitude), if (point.longitude >= 0) "E" else "O")

internal data class ReportMapPoint(val position: GeoVertex, val label: String, val color: String)

/** Puntos ilustrativos dentro del polígono; no son observaciones biológicas. */
internal fun reportDemoPoints(area: AreaGeometry, labels: List<String>, colors: List<String>): List<ReportMapPoint> {
    val points = area.vertices
    val west = points.minOf { it.longitude }; val east = points.maxOf { it.longitude }
    val south = points.minOf { it.latitude }; val north = points.maxOf { it.latitude }
    fun inside(p: GeoVertex): Boolean {
        var result = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]; val b = points[j]
            if ((a.latitude > p.latitude) != (b.latitude > p.latitude) &&
                p.longitude < (b.longitude - a.longitude) * (p.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude) result = !result
            j = i
        }
        return result
    }
    val candidates = (1..9).flatMap { row -> (1..9).map { col -> GeoVertex(west + (east - west) * col / 10, south + (north - south) * row / 10) } }.filter(::inside)
    if (candidates.isEmpty()) return emptyList()
    return (0 until minOf(9, candidates.size)).map { i ->
        ReportMapPoint(candidates[i * candidates.size / minOf(9, candidates.size)], labels[i % labels.size], colors[i % colors.size])
    }
}

