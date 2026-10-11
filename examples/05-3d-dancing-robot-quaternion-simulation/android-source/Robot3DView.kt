package com.example.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import java.util.Timer
import java.util.TimerTask
import kotlin.math.cos
import kotlin.math.sin

class Robot3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DanceStyle { MARCHING, ARM_SIDE_SWING, DIAGONAL_WAVE, FULL_DISCO }

    var currentDanceStyle = DanceStyle.FULL_DISCO
    var isWireframeMode = false
    var danceSpeedScale = 1.0f

    private var danceTime = 0f
    private var timer: Timer? = null

    // Helper data class for a 3D face to be depth sorted
    private data class TransformedFace(
        val vertices2D: Array<Pair<Float, Float>>,
        val avgZ: Float,
        val fillColor: Int,
        val strokeColor: Int
    )

    // 6-faced Cuboid Geometry Definition
    private class Cuboid3D(
        val halfDx: Float,
        val halfDy: Float,
        val halfDz: Float,
        val faceColors: IntArray
    ) {
        val localVertices = arrayOf(
            Vector3(-halfDx, -halfDy, -halfDz), // 0
            Vector3( halfDx, -halfDy, -halfDz), // 1
            Vector3( halfDx,  halfDy, -halfDz), // 2
            Vector3(-halfDx,  halfDy, -halfDz), // 3
            Vector3(-halfDx, -halfDy,  halfDz), // 4
            Vector3( halfDx, -halfDy,  halfDz), // 5
            Vector3( halfDx,  halfDy,  halfDz), // 6
            Vector3(-halfDx,  halfDy,  halfDz)  // 7
        )

        val faceIndices = arrayOf(
            intArrayOf(4, 5, 6, 7), // Front
            intArrayOf(1, 0, 3, 2), // Back
            intArrayOf(4, 0, 1, 5), // Top
            intArrayOf(7, 6, 2, 3), // Bottom
            intArrayOf(0, 4, 7, 3), // Left
            intArrayOf(5, 1, 2, 6)  // Right
        )
    }

    companion object {
        private fun createColorPalette(baseHex: String): IntArray {
            val base = baseHex.toColorInt()
            val r = Color.red(base)
            val g = Color.green(base)
            val b = Color.blue(base)

            return intArrayOf(
                Color.rgb(r, g, b),
                Color.rgb((r * 0.85f).toInt(), (g * 0.85f).toInt(), (b * 0.85f).toInt()),
                Color.rgb((r * 0.70f).toInt(), (g * 0.70f).toInt(), (b * 0.70f).toInt()),
                Color.rgb((r * 0.90f).toInt(), (g * 0.90f).toInt(), (b * 0.90f).toInt()),
                Color.rgb((r * 0.75f).toInt(), (g * 0.75f).toInt(), (b * 0.75f).toInt()),
                Color.rgb((r * 0.95f).toInt(), (g * 0.95f).toInt(), (b * 0.95f).toInt())
            )
        }
    }

    // --- ROBOT BODY PARTS ---
    private val headCuboid = Cuboid3D(35f, 35f, 35f, createColorPalette("#00E676"))       // Bright Green
    private val neckCuboid = Cuboid3D(12f, 15f, 12f, createColorPalette("#FFEB3B"))       // Yellow
    private val torsoCuboid = Cuboid3D(65f, 90f, 40f, createColorPalette("#1E88E5"))      // Blue
    private val pelvisCuboid = Cuboid3D(55f, 25f, 35f, createColorPalette("#8E24AA"))     // Purple

    private val upperArmCuboid = Cuboid3D(18f, 45f, 18f, createColorPalette("#FF9800"))   // Orange
    private val forearmCuboid = Cuboid3D(15f, 40f, 15f, createColorPalette("#00BCD4"))    // Cyan

    private val upperLegCuboid = Cuboid3D(22f, 55f, 22f, createColorPalette("#E53935"))   // Red
    private val lowerLegCuboid = Cuboid3D(18f, 50f, 18f, createColorPalette("#7CB342"))   // Light Green

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.BLACK
    }

    private val facePath = Path()

    init {
        startDanceTimer()
    }

    fun startDanceTimer() {
        stopDanceTimer()
        timer = Timer()
        val task = object : TimerTask() {
            override fun run() {
                danceTime += 0.05f * danceSpeedScale
                postInvalidate() // Refresh view on UI thread
            }
        }
        timer?.schedule(task, 100, 30) // ~33 FPS
    }

    fun stopDanceTimer() {
        timer?.cancel()
        timer = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDanceTimer()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f - 40f

        val facesToRender = mutableListOf<TransformedFace>()

        // --- CALCULATE QUATERNION ROTATIONS BASED ON TIME & CHOREOGRAPHY ---

        // 1. Y-axis Body Oscillation Rotation (-45° to +45°)
        val bodyAngleY = sin(danceTime.toDouble()).toFloat() * Math.toRadians(45.0).toFloat()
        val qBodyY = Quaternion.fromAxisAngle(0f, 1f, 0f, bodyAngleY)

        // 2. Leg Marching Rotations (X-axis)
        val marchAngle = sin((danceTime * 2.5f).toDouble()).toFloat() * Math.toRadians(35.0).toFloat()
        val qLegLeftX = Quaternion.fromAxisAngle(1f, 0f, 0f, if (currentDanceStyle == DanceStyle.ARM_SIDE_SWING) 0f else marchAngle)
        val qLegRightX = Quaternion.fromAxisAngle(1f, 0f, 0f, if (currentDanceStyle == DanceStyle.ARM_SIDE_SWING) 0f else -marchAngle)

        // 3. Arm Side Swings (Z-axis)
        val armSwingZ = cos((danceTime * 2.5f).toDouble()).toFloat() * Math.toRadians(40.0).toFloat()
        val qArmLeftZ = Quaternion.fromAxisAngle(0f, 0f, 1f, armSwingZ)
        val qArmRightZ = Quaternion.fromAxisAngle(0f, 0f, 1f, -armSwingZ)

        // 4. Diagonal Arm Wave Rotations (Axes (1,1,0) and (1,-1,0))
        val diagAngle = sin((danceTime * 3.0f).toDouble()).toFloat() * Math.toRadians(45.0).toFloat()
        val qDiagLeft = Quaternion.fromAxisAngle(1f, 1f, 0f, diagAngle)
        val qDiagRight = Quaternion.fromAxisAngle(1f, -1f, 0f, -diagAngle)

        // Combine Arm Rotations based on selected Dance Style
        val qArmLeftFinal = when (currentDanceStyle) {
            DanceStyle.MARCHING -> qLegRightX
            DanceStyle.ARM_SIDE_SWING -> qArmLeftZ
            DanceStyle.DIAGONAL_WAVE -> qDiagLeft
            DanceStyle.FULL_DISCO -> qArmLeftZ * qDiagLeft * qLegRightX
        }

        val qArmRightFinal = when (currentDanceStyle) {
            DanceStyle.MARCHING -> qLegLeftX
            DanceStyle.ARM_SIDE_SWING -> qArmRightZ
            DanceStyle.DIAGONAL_WAVE -> qDiagRight
            DanceStyle.FULL_DISCO -> qArmRightZ * qDiagRight * qLegLeftX
        }

        // --- HIERARCHICAL BODY TRANSFORMATIONS ---

        // Torso / Body Base
        val torsoPos = Vector3(0f, -20f, 0f)
        transformAndCollectCuboid(torsoCuboid, torsoPos, qBodyY, cx, cy, facesToRender)

        // Head & Neck
        val neckPos = Vector3(0f, -125f, 0f)
        val headPos = Vector3(0f, -175f, 0f)
        transformAndCollectCuboid(neckCuboid, neckPos, qBodyY, cx, cy, facesToRender)
        transformAndCollectCuboid(headCuboid, headPos, qBodyY, cx, cy, facesToRender)

        // Pelvis
        val pelvisPos = Vector3(0f, 80f, 0f)
        transformAndCollectCuboid(pelvisCuboid, pelvisPos, qBodyY, cx, cy, facesToRender)

        // Left Arm (Upper + Forearm)
        val leftShoulderPivot = Vector3(-85f, -80f, 0f)
        transformAndCollectLimb(upperArmCuboid, leftShoulderPivot, Vector3(0f, 40f, 0f), qArmLeftFinal, qBodyY, cx, cy, facesToRender)
        val leftElbowPivot = leftShoulderPivot.add(qArmLeftFinal.rotateVector(Vector3(0f, 80f, 0f)))
        transformAndCollectLimb(forearmCuboid, leftElbowPivot, Vector3(0f, 35f, 0f), qArmLeftFinal, qBodyY, cx, cy, facesToRender)

        // Right Arm (Upper + Forearm)
        val rightShoulderPivot = Vector3(85f, -80f, 0f)
        transformAndCollectLimb(upperArmCuboid, rightShoulderPivot, Vector3(0f, 40f, 0f), qArmRightFinal, qBodyY, cx, cy, facesToRender)
        val rightElbowPivot = rightShoulderPivot.add(qArmRightFinal.rotateVector(Vector3(0f, 80f, 0f)))
        transformAndCollectLimb(forearmCuboid, rightElbowPivot, Vector3(0f, 35f, 0f), qArmRightFinal, qBodyY, cx, cy, facesToRender)

        // Left Leg (Thigh + Shin)
        val leftHipPivot = Vector3(-32f, 105f, 0f)
        transformAndCollectLimb(upperLegCuboid, leftHipPivot, Vector3(0f, 50f, 0f), qLegLeftX, qBodyY, cx, cy, facesToRender)
        val leftKneePivot = leftHipPivot.add(qLegLeftX.rotateVector(Vector3(0f, 100f, 0f)))
        transformAndCollectLimb(lowerLegCuboid, leftKneePivot, Vector3(0f, 45f, 0f), qLegLeftX, qBodyY, cx, cy, facesToRender)

        // Right Leg (Thigh + Shin)
        val rightHipPivot = Vector3(32f, 105f, 0f)
        transformAndCollectLimb(upperLegCuboid, rightHipPivot, Vector3(0f, 50f, 0f), qLegRightX, qBodyY, cx, cy, facesToRender)
        val rightKneePivot = rightHipPivot.add(qLegRightX.rotateVector(Vector3(0f, 100f, 0f)))
        transformAndCollectLimb(lowerLegCuboid, rightKneePivot, Vector3(0f, 45f, 0f), qLegRightX, qBodyY, cx, cy, facesToRender)

        // --- PAINTERS ALGORITHM Z-DEPTH SORTING ---
        // Sort all cuboid faces from furthest Z (back) to nearest Z (front)
        facesToRender.sortBy { it.avgZ }

        // --- RENDER SORTED FACES ---
        for (tf in facesToRender) {
            facePath.reset()
            facePath.moveTo(tf.vertices2D[0].first, tf.vertices2D[0].second)
            for (k in 1 until tf.vertices2D.size) {
                facePath.lineTo(tf.vertices2D[k].first, tf.vertices2D[k].second)
            }
            facePath.close()

            if (isWireframeMode) {
                strokePaint.color = tf.strokeColor
                strokePaint.strokeWidth = 4f
                canvas.drawPath(facePath, strokePaint)
            } else {
                facePaint.style = Paint.Style.FILL_AND_STROKE
                facePaint.color = tf.fillColor
                canvas.drawPath(facePath, facePaint)

                strokePaint.color = Color.BLACK
                strokePaint.strokeWidth = 2f
                canvas.drawPath(facePath, strokePaint)
            }
        }
    }

    private fun transformAndCollectCuboid(
        cuboid: Cuboid3D,
        centerPos: Vector3,
        bodyQuat: Quaternion,
        cx: Float,
        cy: Float,
        outList: MutableList<TransformedFace>
    ) {
        val transformedWorldVerts = Array(cuboid.localVertices.size) { i ->
            val rotatedLocal = bodyQuat.rotateVector(cuboid.localVertices[i])
            val worldPos = bodyQuat.rotateVector(centerPos)
            rotatedLocal.add(worldPos)
        }

        projectAndCollectFaces(cuboid, transformedWorldVerts, cx, cy, outList)
    }

    private fun transformAndCollectLimb(
        cuboid: Cuboid3D,
        pivotPos: Vector3,
        offsetFromPivot: Vector3,
        jointQuat: Quaternion,
        bodyQuat: Quaternion,
        cx: Float,
        cy: Float,
        outList: MutableList<TransformedFace>
    ) {
        val totalQuat = bodyQuat * jointQuat
        val worldPivot = bodyQuat.rotateVector(pivotPos)
        val rotatedOffset = totalQuat.rotateVector(offsetFromPivot)
        val centerPos = worldPivot.add(rotatedOffset)

        val transformedWorldVerts = Array(cuboid.localVertices.size) { i ->
            val rotatedLocal = totalQuat.rotateVector(cuboid.localVertices[i])
            rotatedLocal.add(centerPos)
        }

        projectAndCollectFaces(cuboid, transformedWorldVerts, cx, cy, outList)
    }

    private fun projectAndCollectFaces(
        cuboid: Cuboid3D,
        worldVerts: Array<Vector3>,
        cx: Float,
        cy: Float,
        outList: MutableList<TransformedFace>
    ) {
        val distance = 600f

        val screenVerts = Array(worldVerts.size) { i ->
            val v = worldVerts[i]
            val zPerspective = (distance + v.z) / distance
            val sx = cx + (v.x / zPerspective)
            val sy = cy + (v.y / zPerspective)
            Pair(sx, sy)
        }

        for (fIdx in cuboid.faceIndices.indices) {
            val idxs = cuboid.faceIndices[fIdx]
            val face2D = Array(idxs.size) { k -> screenVerts[idxs[k]] }
            val avgZ = idxs.map { k -> worldVerts[k].z }.average().toFloat()

            val fillColor = cuboid.faceColors[fIdx % cuboid.faceColors.size]
            val strokeColor = cuboid.faceColors[0]

            outList.add(TransformedFace(face2D, avgZ, fillColor, strokeColor))
        }
    }
}
