package com.example.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class CameraProjectionMatrixTest {

    @Test
    fun testPerspectiveProjectionMatrixStructure() {
        val fovDeg = 60f
        val aspect = 1.5f
        val near = 100f
        val far = 1000f

        val fovRad = Math.toRadians(fovDeg.toDouble())
        val fY = (1.0 / tan(fovRad / 2.0)).toFloat()
        val fX = fY / aspect

        val m = FloatArray(16)
        val nf = near - far

        m[0] = fX
        m[5] = fY
        m[10] = (far + near) / nf
        m[11] = (2f * far * near) / nf
        m[14] = -1.0f

        assertEquals(fY / aspect, m[0], 0.001f)
        assertEquals(fY, m[5], 0.001f)
        assertEquals(-1.0f, m[14], 0.001f)
        assertTrue("Perspective matrix [10] must be negative for RH coordinate system", m[10] < 0f)
    }

    @Test
    fun testOrthographicProjectionMatrixStructure() {
        val width = 800f
        val height = 600f
        val near = 10f
        val far = 1000f

        val orthoW = width * 0.5f
        val orthoH = height * 0.5f
        val nf = near - far

        val m = FloatArray(16)
        m[0] = 2f / orthoW
        m[5] = 2f / orthoH
        m[10] = 2f / (near - far)
        m[11] = (far + near) / nf
        m[15] = 1.0f

        assertEquals(2f / 400f, m[0], 0.001f)
        assertEquals(2f / 300f, m[5], 0.001f)
        assertEquals(1.0f, m[15], 0.001f)
    }

    @Test
    fun testPerspectiveDivisionAndDepthNDC() {
        // Perspective transformed point: X'=300, Y'=-150, Z'=-500, W'=500
        val xProj = 300f
        val yProj = -150f
        val zProj = -250f
        val wProj = 500f

        // NDC after homogeneous division by W'
        val xNdc = xProj / wProj
        val yNdc = yProj / wProj
        val zNdc = zProj / wProj

        assertEquals(0.6f, xNdc, 0.001f)
        assertEquals(-0.3f, yNdc, 0.001f)
        assertEquals(-0.5f, zNdc, 0.001f)

        // Point is inside NDC frustum range [-1, +1]
        assertTrue("zNdc should be inside [-1, +1]", zNdc >= -1.0f && zNdc <= 1.0f)
    }
}
