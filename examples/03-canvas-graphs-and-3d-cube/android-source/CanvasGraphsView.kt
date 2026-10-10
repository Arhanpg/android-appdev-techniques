package com.example.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class CanvasGraphsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class GraphType { ALL, ARC_PIE, RECT_BAR, PATH_LINE }

    var currentGraphType = GraphType.ALL

    var arcStartAngle = 0f
    var arcSweepAngle = 270f

    var rectCornerRadius = 24f
    var rectHeightScale = 1.0f

    var pathCurviness = 1.0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.BLACK
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#212121".toColorInt()
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val arcBounds = RectF()
    private val barBounds = RectF()
    private val graphPath = Path()
    private val areaPath = Path()

    private val sliceColors = intArrayOf(
        "#1E88E5".toColorInt(), // Blue
        "#E53935".toColorInt(), // Red
        "#4CAF50".toColorInt(), // Green
        "#FBC02D".toColorInt(), // Yellow
        "#8E24AA".toColorInt()  // Purple
    )

    private val barValues = floatArrayOf(0.4f, 0.85f, 0.6f, 0.95f, 0.3f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        when (currentGraphType) {
            GraphType.ARC_PIE -> {
                drawArcPieChart(canvas, w / 2f, h / 2f, Math.min(w, h) * 0.35f)
            }
            GraphType.RECT_BAR -> {
                drawRectBarChart(canvas, 40f, 60f, w - 80f, h - 120f)
            }
            GraphType.PATH_LINE -> {
                drawPathLineChart(canvas, 40f, 60f, w - 80f, h - 120f)
            }
            GraphType.ALL -> {
                // Multi-panel layout showing all three graph primitives
                val halfW = w / 2f
                val halfH = h / 2f

                // Top Left: Arc Chart
                drawArcPieChart(canvas, halfW / 2f, halfH / 2f, Math.min(halfW, halfH) * 0.35f)

                // Top Right: Bar Chart
                drawRectBarChart(canvas, halfW + 20f, 30f, halfW - 40f, halfH - 60f)

                // Bottom: Line Graph Path
                drawPathLineChart(canvas, 40f, halfH + 40f, w - 80f, halfH - 80f)
            }
        }
    }

    private fun drawArcPieChart(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        var currentAngle = arcStartAngle
        val sliceAngle = arcSweepAngle / sliceColors.size

        for (i in sliceColors.indices) {
            fillPaint.color = sliceColors[i]

            // 1. Primitive Canvas.drawArc (Fills pie slice)
            canvas.drawArc(arcBounds, currentAngle, sliceAngle, true, fillPaint)

            // Outline stroke
            strokePaint.strokeWidth = 4f
            strokePaint.color = Color.WHITE
            canvas.drawArc(arcBounds, currentAngle, sliceAngle, true, strokePaint)

            currentAngle += sliceAngle
        }

        // Draw outer arc accent ring using Canvas.drawArc (stroke mode)
        strokePaint.strokeWidth = 8f
        strokePaint.color = "#1565C0".toColorInt()
        canvas.drawArc(arcBounds, arcStartAngle, arcSweepAngle, false, strokePaint)

        // Title
        textPaint.textSize = 24f
        canvas.drawText("Canvas.drawArc() Pie/Gauge", cx, cy + radius + 32f, textPaint)
    }

    private fun drawRectBarChart(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val count = barValues.size
        val barGap = 16f
        val totalGaps = barGap * (count + 1)
        val barWidth = (width - totalGaps) / count

        val baselineY = y + height

        for (i in 0 until count) {
            val barLeft = x + barGap + i * (barWidth + barGap)
            val scaledHeight = height * barValues[i] * rectHeightScale
            val barTop = baselineY - scaledHeight
            val barRight = barLeft + barWidth

            barBounds.set(barLeft, barTop, barRight, baselineY)

            // Primitive Canvas.drawRoundRect or drawRect
            fillPaint.color = sliceColors[i % sliceColors.size]
            canvas.drawRoundRect(barBounds, rectCornerRadius, rectCornerRadius, fillPaint)

            // Stroke outline
            strokePaint.strokeWidth = 3f
            strokePaint.color = Color.BLACK
            canvas.drawRoundRect(barBounds, rectCornerRadius, rectCornerRadius, strokePaint)

            // Value text
            textPaint.textSize = 20f
            val label = "${(barValues[i] * 100).toInt()}%"
            canvas.drawText(label, barLeft + barWidth / 2f, barTop - 8f, textPaint)
        }

        // Baseline
        strokePaint.strokeWidth = 5f
        strokePaint.color = Color.GRAY
        canvas.drawLine(x, baselineY, x + width, baselineY, strokePaint)

        // Title
        textPaint.textSize = 24f
        canvas.drawText("Canvas.drawRect() Bar Graph", x + width / 2f, baselineY + 36f, textPaint)
    }

    private fun drawPathLineChart(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val samplePoints = arrayOf(
            Pair(0f, 0.2f),
            Pair(0.2f, 0.7f),
            Pair(0.4f, 0.35f),
            Pair(0.6f, 0.9f),
            Pair(0.8f, 0.5f),
            Pair(1.0f, 0.8f)
        )

        val baselineY = y + height

        graphPath.reset()
        areaPath.reset()

        val startX = x + samplePoints[0].first * width
        val startY = baselineY - (samplePoints[0].second * height * pathCurviness)

        graphPath.moveTo(startX, startY)
        areaPath.moveTo(startX, baselineY)
        areaPath.lineTo(startX, startY)

        for (i in 0 until samplePoints.size - 1) {
            val p1X = x + samplePoints[i].first * width
            val p1Y = baselineY - (samplePoints[i].second * height * pathCurviness)
            val p2X = x + samplePoints[i + 1].first * width
            val p2Y = baselineY - (samplePoints[i + 1].second * height * pathCurviness)

            // Smooth cubic Bezier path curve
            val controlX1 = p1X + (p2X - p1X) / 2f
            val controlY1 = p1Y
            val controlX2 = p1X + (p2X - p1X) / 2f
            val controlY2 = p2Y

            graphPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2X, p2Y)
            areaPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2X, p2Y)
        }

        val lastX = x + samplePoints.last().first * width
        areaPath.lineTo(lastX, baselineY)
        areaPath.close()

        // 1. Draw Area Fill below Path
        fillPaint.shader = LinearGradient(
            x, y, x, baselineY,
            Color.argb(160, 30, 136, 229), Color.argb(20, 30, 136, 229),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(areaPath, fillPaint)
        fillPaint.shader = null

        // 2. Draw Primitive Canvas.drawPath (Smooth curve line)
        strokePaint.strokeWidth = 8f
        strokePaint.color = "#1565C0".toColorInt()
        canvas.drawPath(graphPath, strokePaint)

        // 3. Draw Data Point Circles
        fillPaint.color = "#D81B60".toColorInt()
        for (pt in samplePoints) {
            val px = x + pt.first * width
            val py = baselineY - (pt.second * height * pathCurviness)
            canvas.drawCircle(px, py, 10f, fillPaint)
        }

        // Title
        textPaint.textSize = 24f
        canvas.drawText("Canvas.drawPath() Line Spline Graph", x + width / 2f, baselineY + 36f, textPaint)
    }
}
