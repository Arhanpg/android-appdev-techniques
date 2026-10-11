package com.example.test

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3(var x: Float, var y: Float, var z: Float) {
    fun add(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    fun subtract(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
}

data class Quaternion(var w: Float = 1.0f, var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {

    companion object {
        fun identity(): Quaternion = Quaternion(1.0f, 0f, 0f, 0f)

        /**
         * Creates a unit quaternion representing rotation by [angleRad] radians around the 3D axis ([axisX], [axisY], [axisZ]).
         */
        fun fromAxisAngle(axisX: Float, axisY: Float, axisZ: Float, angleRad: Float): Quaternion {
            val len = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
            if (len < 1e-6f) return identity()

            val normX = axisX / len
            val normY = axisY / len
            val normZ = axisZ / len

            val halfAngle = angleRad / 2.0f
            val s = sin(halfAngle)
            return Quaternion(
                cos(halfAngle),
                normX * s,
                normY * s,
                normZ * s
            )
        }
    }

    /**
     * Quaternion multiplication: result = this * other
     */
    operator fun times(other: Quaternion): Quaternion {
        val nw = w * other.w - x * other.x - y * other.y - z * other.z
        val nx = w * other.x + x * other.w + y * other.z - z * other.y
        val ny = w * other.y - x * other.z + y * other.w + z * other.x
        val nz = w * other.z + x * other.y - y * other.x + z * other.w
        return Quaternion(nw, nx, ny, nz)
    }

    /**
     * Rotates a 3D point [v] using this quaternion rotation: v' = q * v * q^-1
     */
    fun rotateVector(v: Vector3): Vector3 {
        // q_vec x v
        val cx1 = y * v.z - z * v.y
        val cy1 = z * v.x - x * v.z
        val cz1 = x * v.y - y * v.x

        // q_vec x (q_vec x v)
        val cx2 = y * cz1 - z * cy1
        val cy2 = z * cx1 - x * cz1
        val cz2 = x * cy1 - y * cx1

        val rx = v.x + 2f * (w * cx1 + cx2)
        val ry = v.y + 2f * (w * cy1 + cy2)
        val rz = v.z + 2f * (w * cz1 + cz2)

        return Vector3(rx, ry, rz)
    }
}
