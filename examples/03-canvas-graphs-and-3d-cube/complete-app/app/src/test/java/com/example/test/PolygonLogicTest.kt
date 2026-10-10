package com.example.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolygonLogicTest {

    @Test
    fun testPolygonClosingRules() {
        var pointsCount = 2
        var canClose = pointsCount >= 3
        assertFalse("Polygon cannot be closed with less than 3 points", canClose)

        pointsCount = 3
        canClose = pointsCount >= 3
        assertTrue("Polygon can be closed with 3 points", canClose)

        pointsCount = 6
        canClose = pointsCount >= 3
        assertTrue("Polygon can be closed with 6 points", canClose)
    }

    @Test
    fun testSemiTransparentColorCalculation() {
        val rgbRed = 0xFF0000
        val alpha = 150
        val argbColor = (alpha shl 24) or (rgbRed and 0xFFFFFF)

        val extractedAlpha = (argbColor ushr 24) and 0xFF
        val extractedRed = (argbColor ushr 16) and 0xFF
        val extractedGreen = (argbColor ushr 8) and 0xFF
        val extractedBlue = argbColor and 0xFF

        assertEquals(150, extractedAlpha)
        assertEquals(255, extractedRed)
        assertEquals(0, extractedGreen)
        assertEquals(0, extractedBlue)
    }
}
