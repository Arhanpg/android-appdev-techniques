package com.example.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.hypot

class AffineTransformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val points = mutableListOf<PointF>()
    private var isClosed = false

    var translateX = 0f
    var translateY = 0f
    var rotationDegrees = 0f
    var affineScaleX = 1.0f
    var affineScaleY = 1.0f
    var skewX = 0f
    var skewY = 0f

    private val affineMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private val closeTouchThresholdPx = 60f
    var onPolygonStateChangedListener: ((isClosed: Boolean, pointCount: Int) -> Unit)? = null
    var onMatrixUpdatedListener: ((matrixValues: FloatArray) -> Unit)? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = "#1E88E5".toColorInt()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(140, 30, 144, 255)
    }

    private val referencePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.LTGRAY
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = "#E0E0E0".toColorInt()
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#D81B60".toColorInt()
    }

    private val startPointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = "#4CAF50".toColorInt()
    }

    private val closingPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.GRAY
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val polygonPath = Path()
    private val transformedPath = Path()
    private val polygonBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background orientation grid
        drawGrid(canvas)

        if (points.isEmpty()) return

        polygonPath.reset()
        polygonPath.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            polygonPath.lineTo(points[i].x, points[i].y)
        }

        if (isClosed) {
            polygonPath.close() // Draws closing line back to start point

            // Draw faint reference path of original untransformed polygon
            canvas.drawPath(polygonPath, referencePathPaint)

            // Compute center centroid for transformation pivot
            polygonPath.computeBounds(polygonBounds, true)
            val cx = polygonBounds.centerX()
            val cy = polygonBounds.centerY()

            // Reset and apply Matrix affine transformations in order
            affineMatrix.reset()
            affineMatrix.postTranslate(translateX, translateY)
            affineMatrix.postRotate(rotationDegrees, cx + translateX, cy + translateY)
            affineMatrix.postScale(affineScaleX, affineScaleY, cx + translateX, cy + translateY)
            affineMatrix.postSkew(skewX, skewY, cx + translateX, cy + translateY)

            // Read current 3x3 matrix coefficients
            affineMatrix.getValues(matrixValues)
            onMatrixUpdatedListener?.invoke(matrixValues)

            // Apply matrix transformation to path
            transformedPath.set(polygonPath)
            transformedPath.transform(affineMatrix)

            // Render transformed path
            canvas.drawPath(transformedPath, fillPaint)
            canvas.drawPath(transformedPath, strokePaint)
        } else {
            canvas.drawPath(polygonPath, strokePaint)

            if (points.size >= 3) {
                val last = points.last()
                val first = points.first()
                canvas.drawLine(last.x, last.y, first.x, first.y, closingPreviewPaint)
            }
        }

        // Draw vertex dots
        for (i in points.indices) {
            val pt = points[i]
            if (i == 0 && !isClosed && points.size >= 3) {
                canvas.drawCircle(pt.x, pt.y, 22f, startPointRingPaint)
                canvas.drawCircle(pt.x, pt.y, 12f, pointPaint)
            } else {
                canvas.drawCircle(pt.x, pt.y, 10f, pointPaint)
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val step = 80f

        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }

        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            val x = event.x
            val y = event.y

            if (isClosed) return true

            if (points.size >= 3) {
                val startPt = points.first()
                val dist = hypot(
                    (x - startPt.x).toDouble(),
                    (y - startPt.y).toDouble()
                ).toFloat()
                if (dist <= closeTouchThresholdPx) {
                    closePolygon()
                    return true
                }
            }

            points.add(PointF(x, y))
            onPolygonStateChangedListener?.invoke(isClosed, points.size)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun closePolygon(): Boolean {
        if (points.size < 3 || isClosed) return false
        isClosed = true
        onPolygonStateChangedListener?.invoke(isClosed, points.size)
        invalidate()
        return true
    }

    fun resetTransformations() {
        translateX = 0f
        translateY = 0f
        rotationDegrees = 0f
        affineScaleX = 1.0f
        affineScaleY = 1.0f
        skewX = 0f
        skewY = 0f
        affineMatrix.reset()
        affineMatrix.getValues(matrixValues)
        onMatrixUpdatedListener?.invoke(matrixValues)
        invalidate()
    }

    fun undoLastPoint() {
        if (points.isNotEmpty()) {
            points.removeAt(points.size - 1)
            isClosed = false
            onPolygonStateChangedListener?.invoke(isClosed, points.size)
            invalidate()
        }
    }

    fun clear() {
        points.clear()
        isClosed = false
        resetTransformations()
        onPolygonStateChangedListener?.invoke(isClosed, points.size)
        invalidate()
    }

    fun isPolygonClosed(): Boolean = isClosed
    fun getPointCount(): Int = points.size
    fun getMatrixValues(): FloatArray = matrixValues.clone()
}
