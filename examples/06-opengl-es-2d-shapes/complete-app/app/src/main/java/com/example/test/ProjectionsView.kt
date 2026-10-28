package com.example.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class ProjectionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ProjectionType { PERSPECTIVE, ORTHOGRAPHIC }

    var currentProjectionType = ProjectionType.PERSPECTIVE

    var fovDegrees = 60f
    var nearPlane = 100f
    var farPlane = 1200f
    var scaleSx = 1.0f
    var scaleSy = 1.0f
    var objectDistanceZ = 500f
    var objectRotationY = 30f

    data class Vector4(var x: Float, var y: Float, var z: Float, var w: Float = 1.0f) {
        fun transform(m: FloatArray): Vector4 {
            val rx = m[0] * x + m[1] * y + m[2] * z + m[3] * w
            val ry = m[4] * x + m[5] * y + m[6] * z + m[7] * w
            val rz = m[8] * x + m[9] * y + m[10] * z + m[11] * w
            val rw = m[12] * x + m[13] * y + m[14] * z + m[15] * w
            return Vector4(rx, ry, rz, rw)
        }
    }

    // 3D House Mesh Vertices (Base Box + Triangular Roof)
    private val houseVertices = arrayOf(
        // Base Box
        Vector4(-80f, -80f, -80f), // 0: Bottom-Left-Back
        Vector4( 80f, -80f, -80f), // 1: Bottom-Right-Back
        Vector4( 80f,  40f, -80f), // 2: Top-Right-Back
        Vector4(-80f,  40f, -80f), // 3: Top-Left-Back
        Vector4(-80f, -80f,  80f), // 4: Bottom-Left-Front
        Vector4( 80f, -80f,  80f), // 5: Bottom-Right-Front
        Vector4( 80f,  40f,  80f), // 6: Top-Right-Front
        Vector4(-80f,  40f,  80f), // 7: Top-Left-Front
        // Roof Peak
        Vector4(  0f, 120f, -80f), // 8: Roof-Peak-Back
        Vector4(  0f, 120f,  80f)  // 9: Roof-Peak-Front
    )

    data class MeshFace(val indices: IntArray, val color: Int)

    private val houseFaces = arrayOf(
        MeshFace(intArrayOf(4, 5, 6, 7), Color.argb(190, 229, 57, 53)),   // Front Wall (Red)
        MeshFace(intArrayOf(1, 0, 3, 2), Color.argb(190, 30, 136, 229)),  // Back Wall (Blue)
        MeshFace(intArrayOf(0, 4, 7, 3), Color.argb(190, 142, 36, 170)),  // Left Wall (Purple)
        MeshFace(intArrayOf(5, 1, 2, 6), Color.argb(190, 251, 140, 0)),   // Right Wall (Orange)
        MeshFace(intArrayOf(7, 6, 9), Color.argb(210, 76, 175, 80)),      // Front Roof Triangle (Green)
        MeshFace(intArrayOf(2, 3, 8), Color.argb(210, 0, 150, 136)),     // Back Roof Triangle (Teal)
        MeshFace(intArrayOf(7, 9, 8, 3), Color.argb(210, 251, 192, 45)),  // Left Roof Slope (Yellow)
        MeshFace(intArrayOf(6, 9, 8, 2), Color.argb(210, 255, 112, 67))   // Right Roof Slope (Coral)
    )

    private val projectionMatrix = FloatArray(16)
    var onProjectionMatrixUpdatedListener: ((m: FloatArray, clippedCount: Int) -> Unit)? = null

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.BLACK
    }
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = "#00E676".toColorInt()
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val vertexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val facePath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val aspect = w / Math.max(1f, h)

        // 1. Build Projection Matrix
        when (currentProjectionType) {
            ProjectionType.PERSPECTIVE -> buildPerspectiveMatrix(projectionMatrix, aspect)
            ProjectionType.ORTHOGRAPHIC -> buildOrthographicMatrix(projectionMatrix, w, h)
        }

        // 2. Rotate & Translate 3D Object in Camera Space
        val radY = Math.toRadians(objectRotationY.toDouble())
        val cosY = cos(radY).toFloat()
        val sinY = sin(radY).toFloat()

        val cameraSpaceVertices = Array(houseVertices.size) { i ->
            val v = houseVertices[i]
            // Rotate around Y axis
            val rx = v.x * cosY + v.z * sinY
            val ry = v.y
            val rz = -v.x * sinY + v.z * cosY - objectDistanceZ
            Vector4(rx, ry, rz, 1.0f)
        }

        // 3. Multiply by 4x4 Projection Matrix
        val projectedHomogeneous = Array(cameraSpaceVertices.size) { i ->
            cameraSpaceVertices[i].transform(projectionMatrix)
        }

        // 4. Perform Homogeneous Division (X'/W', Y'/W', Z'/W') & Calculate Screen Position + Depth NDC
        var clippedCount = 0
        val screenPoints = Array(projectedHomogeneous.size) { i ->
            val p = projectedHomogeneous[i]
            val wDiv = if (p.w != 0f) p.w else 1.0f

            val xNdc = p.x / wDiv
            val yNdc = p.y / wDiv
            val zNdc = p.z / wDiv // Depth NDC in [-1, +1]

            val isClipped = zNdc < -1.0f || zNdc > 1.0f
            if (isClipped) clippedCount++

            val sx = cx + xNdc * (w / 2f)
            val sy = cy - yNdc * (h / 2f)

            Triple(sx, sy, isClipped)
        }

        onProjectionMatrixUpdatedListener?.invoke(projectionMatrix.clone(), clippedCount)

        // 5. Depth Sorting (Painters Algorithm)
        val sortedFaces = houseFaces.map { face ->
            val avgCameraZ = face.indices.map { idx -> cameraSpaceVertices[idx].z }.average().toFloat()
            Pair(face, avgCameraZ)
        }.sortedBy { it.second } // Far (-Z) to near

        // 6. Draw Projection Rays from Camera Center (0,0) to Projected Points in Perspective Mode
        if (currentProjectionType == ProjectionType.PERSPECTIVE) {
            for (pt in screenPoints) {
                if (!pt.third) { // If not clipped
                    canvas.drawLine(cx, cy, pt.first, pt.second, rayPaint)
                }
            }
        }

        // 7. Render 3D Mesh Faces
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

        // 8. Draw Vertices (Green if inside frustum, Red if clipped)
        for (pt in screenPoints) {
            vertexPaint.color = if (pt.third) "#E53935".toColorInt() else "#00E676".toColorInt()
            canvas.drawCircle(pt.first, pt.second, 9f, vertexPaint)
        }
    }

    private fun buildPerspectiveMatrix(m: FloatArray, aspect: Float) {
        val fovRad = Math.toRadians(fovDegrees.toDouble())
        val fY = (1.0 / tan(fovRad / 2.0)).toFloat()
        val fX = (fY / aspect) * scaleSx

        val n = nearPlane
        val f = farPlane
        val nf = n - f

        m[0] = fX
        m[1] = 0f
        m[2] = 0f
        m[3] = 0f

        m[4] = 0f
        m[5] = fY * scaleSy
        m[6] = 0f
        m[7] = 0f

        m[8] = 0f
        m[9] = 0f
        m[10] = (f + n) / nf
        m[11] = (2f * f * n) / nf

        m[12] = 0f
        m[13] = 0f
        m[14] = -1.0f
        m[15] = 0f
    }

    private fun buildOrthographicMatrix(m: FloatArray, width: Float, height: Float) {
        val n = nearPlane
        val f = farPlane
        val nf = n - f

        val orthoW = width * 0.5f
        val orthoH = height * 0.5f

        m[0] = (2f / orthoW) * scaleSx
        m[1] = 0f
        m[2] = 0f
        m[3] = 0f

        m[4] = 0f
        m[5] = (2f / orthoH) * scaleSy
        m[6] = 0f
        m[7] = 0f

        m[8] = 0f
        m[9] = 0f
        m[10] = 2f / (n - f)
        m[11] = (f + n) / nf

        m[12] = 0f
        m[13] = 0f
        m[14] = 0f
        m[15] = 1.0f
    }
}
