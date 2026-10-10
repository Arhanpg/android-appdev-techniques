package com.example.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.cos
import kotlin.math.sin

class Cube3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Vector4(var x: Float, var y: Float, var z: Float, var w: Float = 1.0f) {
        fun transform(m: FloatArray): Vector4 {
            val rx = m[0] * x + m[1] * y + m[2] * z + m[3] * w
            val ry = m[4] * x + m[5] * y + m[6] * z + m[7] * w
            val rz = m[8] * x + m[9] * y + m[10] * z + m[11] * w
            val rw = m[12] * x + m[13] * y + m[14] * z + m[15] * w
            return Vector4(rx, ry, rz, rw)
        }
    }

    private val cubeVertices = arrayOf(
        Vector4(-100f, -100f, -100f), // 0
        Vector4( 100f, -100f, -100f), // 1
        Vector4( 100f,  100f, -100f), // 2
        Vector4(-100f,  100f, -100f), // 3
        Vector4(-100f, -100f,  100f), // 4
        Vector4( 100f, -100f,  100f), // 5
        Vector4( 100f,  100f,  100f), // 6
        Vector4(-100f,  100f,  100f)  // 7
    )

    data class CubeFace(val indices: IntArray, val color: Int)

    private val cubeFaces = arrayOf(
        CubeFace(intArrayOf(4, 5, 6, 7), Color.argb(180, 229, 57, 53)),   // Front (Red)
        CubeFace(intArrayOf(1, 0, 3, 2), Color.argb(180, 30, 136, 229)),  // Back (Blue)
        CubeFace(intArrayOf(4, 0, 1, 5), Color.argb(180, 76, 175, 80)),   // Top (Green)
        CubeFace(intArrayOf(7, 6, 2, 3), Color.argb(180, 251, 192, 45)),  // Bottom (Yellow)
        CubeFace(intArrayOf(0, 4, 7, 3), Color.argb(180, 142, 36, 170)),  // Left (Purple)
        CubeFace(intArrayOf(5, 1, 2, 6), Color.argb(180, 251, 140, 0))    // Right (Orange)
    )

    var translateX = 0f
    var translateY = 0f
    var translateZ = 0f

    var rotX = 25f
    var rotY = 45f
    var rotZ = 0f

    var cubeScaleX = 1.0f
    var cubeScaleY = 1.0f
    var cubeScaleZ = 1.0f

    var perspectiveW = 1.0f

    private val matrix4x4 = FloatArray(16)
    var onMatrix4UpdatedListener: ((m: FloatArray) -> Unit)? = null

    private var autoSpinDirection = 0 // 0: off, 1: CW, -1: CCW
    private val spinRunnable = object : Runnable {
        override fun run() {
            if (autoSpinDirection != 0) {
                rotY = (rotY + autoSpinDirection * 2f) % 360f
                if (rotY < 0) rotY += 360f
                invalidate()
                postDelayed(this, 16)
            }
        }
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.BLACK
    }

    private val vertexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#D81B60".toColorInt()
    }

    private val facePath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        // 1. Build 4x4 Homogeneous Transformation Matrix
        build4x4Matrix(matrix4x4)
        onMatrix4UpdatedListener?.invoke(matrix4x4.clone())

        // 2. Transform all 8 3D vertices and perform Homogeneous W Division
        val transformedVertices = Array(cubeVertices.size) { i ->
            cubeVertices[i].transform(matrix4x4)
        }

        // 3. Project 3D -> 2D Screen points using Homogeneous Division
        val screenPoints = Array(transformedVertices.size) { i ->
            val v = transformedVertices[i]
            // Homogeneous Coordinate Division by W
            val wDiv = if (v.w != 0f) v.w / perspectiveW else 1.0f
            val sx = cx + (v.x / wDiv)
            val sy = cy + (v.y / wDiv)
            Pair(sx, sy)
        }

        // 4. Depth sort faces (Painters Algorithm based on average Z depth)
        val sortedFaces = cubeFaces.map { face ->
            val avgZ = face.indices.map { idx -> transformedVertices[idx].z }.average().toFloat()
            Pair(face, avgZ)
        }.sortedBy { it.second } // Far to near

        // 5. Draw sorted 3D cube faces
        for (pair in sortedFaces) {
            val face = pair.first
            facePath.reset()

            val p0 = screenPoints[face.indices[0]]
            facePath.moveTo(p0.first, p0.second)

            for (k in 1 until face.indices.size) {
                val p = screenPoints[face.indices[k]]
                facePath.lineTo(p.first, p.second)
            }
            facePath.close()

            // Draw filled face
            facePaint.color = face.color
            canvas.drawPath(facePath, facePaint)

            // Draw wireframe outline
            canvas.drawPath(facePath, strokePaint)
        }

        // 6. Draw 3D vertex points
        for (pt in screenPoints) {
            canvas.drawCircle(pt.first, pt.second, 8f, vertexPaint)
        }
    }

    private fun build4x4Matrix(out: FloatArray) {
        val radX = Math.toRadians(rotX.toDouble())
        val radY = Math.toRadians(rotY.toDouble())
        val radZ = Math.toRadians(rotZ.toDouble())

        val cx = cos(radX).toFloat()
        val sx = sin(radX).toFloat()
        val cy = cos(radY).toFloat()
        val sy = sin(radY).toFloat()
        val cz = cos(radZ).toFloat()
        val sz = sin(radZ).toFloat()

        // Combined 4x4 Homogeneous Rotation Matrix (Rz * Ry * Rx) with Scaling and Translation
        out[0]  = (cy * cz) * cubeScaleX
        out[1]  = (cz * sx * sy - cx * sz) * cubeScaleX
        out[2]  = (cx * cz * sy + sx * sz) * cubeScaleX
        out[3]  = translateX

        out[4]  = (cy * sz) * cubeScaleY
        out[5]  = (cx * cz + sx * sy * sz) * cubeScaleY
        out[6]  = (-cz * sx + cx * sy * sz) * cubeScaleY
        out[7]  = translateY

        out[8]  = (-sy) * cubeScaleZ
        out[9]  = (cy * sx) * cubeScaleZ
        out[10] = (cx * cy) * cubeScaleZ
        out[11] = translateZ

        out[12] = 0f
        out[13] = 0f
        out[14] = 0f
        out[15] = 1.0f
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                lastTouchX = event.x
                lastTouchY = event.y
                stopAutoSpin()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                // Interactive 360° Horizontal (around Y axis) and Vertical (around X axis) rotation
                rotY = (rotY + dx * 0.5f) % 360f
                if (rotY < 0) rotY += 360f

                rotX = (rotX - dy * 0.5f) % 360f
                if (rotX < 0) rotX += 360f

                lastTouchX = event.x
                lastTouchY = event.y

                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setAutoSpin(direction: Int) { // 1: CW, -1: CCW, 0: stop
        autoSpinDirection = direction
        removeCallbacks(spinRunnable)
        if (autoSpinDirection != 0) {
            post(spinRunnable)
        }
    }

    fun stopAutoSpin() {
        autoSpinDirection = 0
        removeCallbacks(spinRunnable)
    }

    fun resetCube() {
        stopAutoSpin()
        translateX = 0f
        translateY = 0f
        translateZ = 0f
        rotX = 25f
        rotY = 45f
        rotZ = 0f
        cubeScaleX = 1.0f
        cubeScaleY = 1.0f
        cubeScaleZ = 1.0f
        perspectiveW = 1.0f
        invalidate()
    }
}
