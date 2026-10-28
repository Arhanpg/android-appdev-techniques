package com.example.test

import org.junit.Assert.assertEquals
import org.junit.Test

class AffineMatrixTest {

    @Test
    fun testTranslationMatrixValues() {
        // Translation by (tx, ty)
        val tx = 50f
        val ty = -30f

        // Matrix structure for 2D Affine Translation:
        // [ 1  0  tx ]
        // [ 0  1  ty ]
        // [ 0  0  1  ]
        val matrix = floatArrayOf(
            1f, 0f, tx,
            0f, 1f, ty,
            0f, 0f, 1f
        )

        assertEquals(50f, matrix[2], 0.001f)
        assertEquals(-30f, matrix[5], 0.001f)
    }

    @Test
    fun testScaleMatrixValues() {
        val sx = 2.5f
        val sy = 0.5f

        // Matrix structure for 2D Scaling:
        // [ sx  0   0 ]
        // [ 0   sy  0 ]
        // [ 0   0   1 ]
        val matrix = floatArrayOf(
            sx, 0f, 0f,
            0f, sy, 0f,
            0f, 0f, 1f
        )

        assertEquals(2.5f, matrix[0], 0.001f)
        assertEquals(0.5f, matrix[4], 0.001f)
    }

    @Test
    fun testShearMatrixValues() {
        val kx = 1.2f
        val ky = -0.8f

        // Matrix structure for 2D Shearing/Skewing:
        // [ 1   kx  0 ]
        // [ ky  1   0 ]
        // [ 0   0   1  ]
        val matrix = floatArrayOf(
            1f, kx, 0f,
            ky, 1f, 0f,
            0f, 0f, 1f
        )

        assertEquals(1.2f, matrix[1], 0.001f)
        assertEquals(-0.8f, matrix[3], 0.001f)
    }
}
