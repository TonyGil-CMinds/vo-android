package com.example.miprimeraapp

import org.junit.Assert.*
import org.junit.Test

class AreaMathTest {
    private val square = listOf(GeoVertex(0.0,0.0),GeoVertex(0.01,0.0),GeoVertex(0.01,0.01),GeoVertex(0.0,0.01))
    @Test fun measuresClosedRingInEitherDirection() {
        val area = AreaMath.measure(square)
        assertEquals(1_236_435.0, area.squareMeters, 100.0)
        assertEquals(4447.8, area.perimeterMeters, 1.0)
        assertEquals(area.squareMeters, AreaMath.measure(square.reversed()).squareMeters, 0.01)
        assertNull(AreaMath.validationError(square))
        assertTrue(area.toGeoJson().contains("[0.0,0.01],[0.0,0.0]"))
    }
    @Test fun rejectsCrossingsDuplicatesAndCollinearPoints() {
        assertNotNull(AreaMath.validationError(listOf(square[0],square[2],square[1],square[3])))
        assertNotNull(AreaMath.validationError(square + square[0]))
        assertNotNull(AreaMath.validationError(listOf(GeoVertex(0.0,0.0),GeoVertex(1.0,0.0),GeoVertex(2.0,0.0))))
        assertNotNull(AreaMath.validationError(square.take(2)))
    }
}
