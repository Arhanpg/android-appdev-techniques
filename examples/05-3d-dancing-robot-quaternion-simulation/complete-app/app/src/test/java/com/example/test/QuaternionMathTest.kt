package com.example.test

import org.junit.Assert.assertEquals
import org.junit.Test

class QuaternionMathTest {

    @Test
    fun testQuaternionIdentity() {
        val q = Quaternion.identity()
        val v = Vector3(10f, -20f, 30f)
        val rotated = q.rotateVector(v)

        assertEquals(v.x, rotated.x, 0.001f)
        assertEquals(v.y, rotated.y, 0.001f)
        assertEquals(v.z, rotated.z, 0.001f)
    }

    @Test
    fun testQuaternionYAxisRotation() {
        // Rotate (100, 0, 0) by 90 degrees around Y-axis (0, 1, 0) -> Should result in (0, 0, -100)
        val angleRad = Math.toRadians(90.0).toFloat()
        val qY = Quaternion.fromAxisAngle(0f, 1f, 0f, angleRad)
        val v = Vector3(100f, 0f, 0f)
        val rotated = qY.rotateVector(v)

        assertEquals(0f, rotated.x, 0.01f)
        assertEquals(0f, rotated.y, 0.01f)
        assertEquals(-100f, rotated.z, 0.01f)
    }

    @Test
    fun testQuaternionXAxisLegMarchingRotation() {
        // Rotate (0, 100, 0) by 90 degrees around X-axis (1, 0, 0) -> Should result in (0, 0, 100)
        val angleRad = Math.toRadians(90.0).toFloat()
        val qX = Quaternion.fromAxisAngle(1f, 0f, 0f, angleRad)
        val v = Vector3(0f, 100f, 0f)
        val rotated = qX.rotateVector(v)

        assertEquals(0f, rotated.x, 0.01f)
        assertEquals(0f, rotated.y, 0.01f)
        assertEquals(100f, rotated.z, 0.01f)
    }

    @Test
    fun testQuaternionDiagonalAxisArmRotation() {
        // Rotate along diagonal axis (1, 1, 0)
        val angleRad = Math.toRadians(45.0).toFloat()
        val qDiag = Quaternion.fromAxisAngle(1f, 1f, 0f, angleRad)
        val v = Vector3(0f, 100f, 0f)
        val rotated = qDiag.rotateVector(v)

        // Verifies valid 3D vector rotation along diagonal axis
        assertEquals(100f * 100f, rotated.x * rotated.x + rotated.y * rotated.y + rotated.z * rotated.z, 1.0f)
    }
}
