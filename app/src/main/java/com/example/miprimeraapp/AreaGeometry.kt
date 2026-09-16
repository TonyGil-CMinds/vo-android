package com.example.miprimeraapp

import kotlin.math.*

data class GeoVertex(val longitude: Double, val latitude: Double)
data class AreaGeometry(val vertices: List<GeoVertex>, val squareMeters: Double, val perimeterMeters: Double) {
    val center get() = GeoVertex(vertices.map { it.longitude }.average(), vertices.map { it.latitude }.average())
    /** GeoJSON uses longitude, latitude and an explicitly closed linear ring. */
    fun toGeoJson(): String = """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[${(vertices + vertices.first()).joinToString(",") { "[${it.longitude},${it.latitude}]" }}]]}}"""
}

object AreaMath {
    private const val R = 6371008.8
    fun distance(a: GeoVertex, b: GeoVertex): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 2 * R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    private fun cross(a: GeoVertex, b: GeoVertex, c: GeoVertex) =
        (b.longitude - a.longitude) * (c.latitude - a.latitude) - (b.latitude - a.latitude) * (c.longitude - a.longitude)
    private fun onSegment(a: GeoVertex, b: GeoVertex, p: GeoVertex) =
        abs(cross(a, b, p)) < 1e-12 && p.longitude in min(a.longitude,b.longitude)..max(a.longitude,b.longitude) &&
            p.latitude in min(a.latitude,b.latitude)..max(a.latitude,b.latitude)
    private fun intersects(a: GeoVertex, b: GeoVertex, c: GeoVertex, d: GeoVertex): Boolean {
        val x = cross(a,b,c); val y = cross(a,b,d); val z = cross(c,d,a); val w = cross(c,d,b)
        return (x * y < 0 && z * w < 0) || onSegment(a,b,c) || onSegment(a,b,d) || onSegment(c,d,a) || onSegment(c,d,b)
    }
    fun validationError(points: List<GeoVertex>): String? {
        if (points.size < 3) return "Añade al menos 3 puntos."
        if (points.any { !it.latitude.isFinite() || !it.longitude.isFinite() || abs(it.latitude) > 85 || abs(it.longitude) > 180 }) return "Las coordenadas no son válidas."
        for (i in points.indices) for (j in i + 1 until points.size) {
            if (distance(points[i], points[j]) < 1) return "Separa los puntos al menos un metro."
            if (j == i + 1 || (i == 0 && j == points.lastIndex)) continue
            if (intersects(points[i], points[(i + 1) % points.size], points[j], points[(j + 1) % points.size]))
                return "Las líneas se cruzan. Deshaz el último punto y ajusta el trazado."
        }
        if (measure(points).squareMeters < 10) return "Traza un área mayor; los puntos están demasiado alineados o juntos."
        return null
    }
    fun measure(points: List<GeoVertex>): AreaGeometry {
        require(points.size >= 3)
        var area = 0.0; var perimeter = 0.0
        points.indices.forEach { i ->
            val a = points[i]; val b = points[(i + 1) % points.size]
            val delta = Math.toRadians(b.longitude - a.longitude)
            area += delta * (2 + sin(Math.toRadians(a.latitude)) + sin(Math.toRadians(b.latitude)))
            perimeter += distance(a,b)
        }
        return AreaGeometry(points.toList(), abs(area) * R * R / 2, perimeter)
    }
}

enum class InformationLevel { GOOD, LIMITED, UNAVAILABLE }
data class SourceQuality(val name: String, val percent: Int, val level: InformationLevel, val logo: Int)
data class AreaInformation(val quality: Int, val sources: List<SourceQuality>, val simulated: Boolean)

/** Replace this boundary with a service receiving area.toGeoJson(); no map-style fields are assumed. */
interface AreaInformationRepository { suspend fun evaluate(area: AreaGeometry): AreaInformation }
class DemoAreaInformationRepository : AreaInformationRepository {
    override suspend fun evaluate(area: AreaGeometry): AreaInformation = AreaInformation(78, listOf(
        SourceQuality("Sentinel-2", 97, InformationLevel.GOOD, R.drawable.logo_sentinel2),
        SourceQuality("Copernicus Marine", 93, InformationLevel.GOOD, R.drawable.logo_copernicusmarine),
        SourceQuality("OBIS", 84, InformationLevel.LIMITED, R.drawable.logo_bis),
        SourceQuality("NASA EarthData", 38, InformationLevel.UNAVAILABLE, R.drawable.logo_nasaearthdata),
        SourceQuality("NOAA", 78, InformationLevel.LIMITED, R.drawable.logo_noaa),
    ), simulated = true)
}
