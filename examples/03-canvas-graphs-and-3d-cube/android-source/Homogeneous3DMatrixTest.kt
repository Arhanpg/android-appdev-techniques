package com.example.test

import org.junit.Assert.assertEquals
import org.junit.Test

class Homogeneous3DMatrixTest {

    @Test
    fun testHomogeneousCoordinatesDivision() {
        // Homogeneous vector (X, Y, Z, W) = (200, -100, 50, 2.0)
        val x = 200f
        val y = -100f
        val z = 50f
        val w = 2.0f

        // Screen projection via homogeneous division by W
        val screenX = x / w
        val screenY = y / w
        val projZ = z / w

        assertEquals(100f, screenX, 0.001f)
        assertEquals(-50f, screenY, 0.001f)
        assertEquals(25f, projZ, 0.001f)
    }

    @Test
    fun test4x4HomogeneousTranslationMatrix() {
        val tx = 15f
        val ty = -25f
        val tz = 100f

        // 4x4 Homogeneous Matrix:
        // [ 1  0  0  tx ]
        // [ 0  1  0  ty ]
        // [ 0  0  1  tz ]
        // [ 0  0  0  1  ]
        val m = floatArrayOf(
            1f, 0f, 0f, tx,
            0f, 1f, 0f, ty,
            0f, 0f, 1f, tz,
            0f, 0f, 0f, 1f
        )

        assertEquals(15f, m[3], 0.001f)
        assertEquals(-25f, m[7], 0.001f)
        assertEquals(100f, m[11], 0.001f)
        assertEquals(1f, m[15], 0.001f)
    }
}
