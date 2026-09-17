package com.example.miprimeraapp

import org.junit.Assert.*
import org.junit.Test

class ReportGeometryTest {
    @Test fun coordinatesUseCorrectHemispheres() {
        assertEquals("24.5000° N  ·  110.2500° O", reportCoordinates(GeoVertex(-110.25, 24.5)))
        assertEquals("12.5000° S  ·  45.2500° E", reportCoordinates(GeoVertex(45.25, -12.5)))
    }

    @Test fun markersStayInsideConcaveUserPolygon() {
        val vertices = listOf(GeoVertex(0.0, 0.0), GeoVertex(3.0, 0.0), GeoVertex(3.0, 1.0),
            GeoVertex(1.0, 1.0), GeoVertex(1.0, 3.0), GeoVertex(0.0, 3.0))
        val area = AreaMath.measure(vertices)
        val markers = reportDemoPoints(area, listOf("Coral", "Manglar", "Especies"), listOf("blue", "green", "orange"))
        assertEquals(9, markers.size)
        assertEquals(9, markers.map { it.position }.distinct().size)
        assertEquals(3, markers.map { it.label }.distinct().size)
        assertTrue(markers.all { it.position.longitude < 1 || it.position.latitude < 1 })
        assertTrue(markers.all { it.position.longitude in 0.0..3.0 && it.position.latitude in 0.0..3.0 })
        assertEquals(vertices, area.vertices)
        assertEquals(markers, reportDemoPoints(area, listOf("Coral", "Manglar", "Especies"), listOf("blue", "green", "orange")))
    }
}
