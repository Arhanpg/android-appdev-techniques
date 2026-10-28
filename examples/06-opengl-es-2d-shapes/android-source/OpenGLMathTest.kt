package com.example.test

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OpenGLMathTest {

    @Test
    fun testRegularPolygonVertexAngleCoordinates() {
        val sides = 6
        val radius = 0.5f

        val coords = FloatArray((sides + 2) * 3)
        // Center vertex
        coords[0] = 0f; coords[1] = 0f; coords[2] = 0f

        for (i in 0..sides) {
            val angle = (2.0 * Math.PI * i / sides).toFloat()
            val x = radius * cos(angle.toDouble()).toFloat()
            val y = radius * sin(angle.toDouble()).toFloat()

            val idx = (i + 1) * 3
            coords[idx] = x
            coords[idx + 1] = y
            coords[idx + 2] = 0f
        }

        // First perimeter vertex at angle 0° -> (0.5, 0.0, 0.0)
        assertEquals(0.5f, coords[3], 0.001f)
        assertEquals(0.0f, coords[4], 0.001f)

        // Total float entries: (6 + 2) * 3 = 24 floats
        assertEquals(24, coords.size)
    }

    @Test
    fun testFloatBufferByteAllocationSize() {
        val floatCount = 24
        val bytesPerFloat = 4
        val totalBytes = floatCount * bytesPerFloat

        assertEquals(96, totalBytes)
    }
}
