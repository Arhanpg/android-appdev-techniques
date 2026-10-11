package com.example.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.hypot

class PolygonDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val points = mutableListOf<PointF>()
    private var isClosed = false

    private var polygonFillColor = Color.argb(140, 30, 144, 255) // Semi-transparent Dodger Blue
    private var polygonStrokeColor = "#1E88E5".toColorInt()
    private var startPointHighlightColor = "#4CAF50".toColorInt() // Green

    private val closeTouchThresholdPx = 60f

    var onPolygonStateChangedListener: ((isClosed: Boolean, pointCount: Int) -> Unit)? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = polygonStrokeColor
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = polygonFillColor
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#D81B60".toColorInt() // Dark Pink/Red
    }

    private val startPointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = startPointHighlightColor
    }

    private val closingPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.GRAY
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val polygonPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (points.isEmpty()) return

        polygonPath.reset()
        polygonPath.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            polygonPath.lineTo(points[i].x, points[i].y)
        }

        if (isClosed) {
            // Draw closing line and close the path back to the starting point
            polygonPath.close()

            // 1. Fill/Color the interior of the closed polygon
            canvas.drawPath(polygonPath, fillPaint)

            // 2. Draw the outline including the closing line
            canvas.drawPath(polygonPath, strokePaint)
        } else {
            // Draw open line path connecting current vertices
            canvas.drawPath(polygonPath, strokePaint)

            // If we have at least 3 points, draw a dashed preview of the line that will close the polygon
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
                // Highlight start point when ready to close
                canvas.drawCircle(pt.x, pt.y, 22f, startPointRingPaint)
                canvas.drawCircle(pt.x, pt.y, 12f, pointPaint)
            } else {
                canvas.drawCircle(pt.x, pt.y, 10f, pointPaint)
            }
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

            if (isClosed) {
                return true
            }

            // If user taps near the starting point when there are at least 3 points, close polygon!
            if (points.size >= 3) {
                val startPt = points.first()
                val distance = hypot((x - startPt.x).toDouble(), (y - startPt.y).toDouble()).toFloat()
                if (distance <= closeTouchThresholdPx) {
                    closePolygon()
                    return true
                }
            }

            // Otherwise add vertex
            points.add(PointF(x, y))
            onPolygonStateChangedListener?.invoke(isClosed, points.size)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Closes the polygon by drawing the line from the last point to the start point
     * and filling the polygon with the chosen fill color.
     */
    fun closePolygon(): Boolean {
        if (points.size < 3 || isClosed) {
            return false
        }
        isClosed = true
        onPolygonStateChangedListener?.invoke(isClosed, points.size)
        invalidate()
        return true
    }

    /**
     * Updates the fill color used to color the inside of the polygon.
     */
    fun setFillColor(color: Int) {
        polygonFillColor = color
        fillPaint.color = polygonFillColor
        invalidate()
    }

    fun setStrokeColor(color: Int) {
        polygonStrokeColor = color
        strokePaint.color = polygonStrokeColor
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
        onPolygonStateChangedListener?.invoke(isClosed, points.size)
        invalidate()
    }

    fun isPolygonClosed(): Boolean = isClosed

    fun getPointCount(): Int = points.size

    fun getFillColor(): Int = polygonFillColor
}
