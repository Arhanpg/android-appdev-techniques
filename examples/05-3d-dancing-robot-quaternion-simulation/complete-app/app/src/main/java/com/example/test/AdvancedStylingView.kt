package com.example.test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.hypot
import kotlin.math.max

class AdvancedStylingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class FillMode { FILL, STROKE, FILL_AND_STROKE }
    enum class ShaderType { SOLID, LINEAR_GRADIENT, RADIAL_GRADIENT, SWEEP_GRADIENT, TEXTURE_PATTERN }
    enum class TexturePattern { CHECKERBOARD, DIAGONAL_HATCH, POLKA_DOTS, BRICK_WALL }

    private val points = mutableListOf<PointF>()
    private var isClosed = false

    var currentFillMode = FillMode.FILL_AND_STROKE
    var currentShaderType = ShaderType.LINEAR_GRADIENT
    var currentTexturePattern = TexturePattern.CHECKERBOARD

    var primaryColor = "#1E88E5".toColorInt()
    var secondaryColor = "#E53935".toColorInt()
    var strokeColor = Color.BLACK
    var strokeWidthPx = 8f

    private val closeTouchThresholdPx = 60f
    var onPolygonStateChangedListener: ((isClosed: Boolean, pointCount: Int) -> Unit)? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = strokeColor
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
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
    private val polygonBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (points.isEmpty()) return

        polygonPath.reset()
        polygonPath.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            polygonPath.lineTo(points[i].x, points[i].y)
        }

        if (isClosed) {
            polygonPath.close() // Draws closing line back to initial vertex

            // Compute bounds and build shader
            polygonPath.computeBounds(polygonBounds, true)
            applyShaderToFillPaint()

            strokePaint.strokeWidth = strokeWidthPx
            strokePaint.color = strokeColor

            when (currentFillMode) {
                FillMode.FILL -> {
                    canvas.drawPath(polygonPath, fillPaint)
                }
                FillMode.STROKE -> {
                    canvas.drawPath(polygonPath, strokePaint)
                }
                FillMode.FILL_AND_STROKE -> {
                    canvas.drawPath(polygonPath, fillPaint)
                    canvas.drawPath(polygonPath, strokePaint)
                }
            }
        } else {
            strokePaint.strokeWidth = strokeWidthPx
            strokePaint.color = strokeColor
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

    private fun applyShaderToFillPaint() {
        val width = max(10f, polygonBounds.width())
        val height = max(10f, polygonBounds.height())
        val radius = max(width, height) / 2f
        val cx = polygonBounds.centerX()
        val cy = polygonBounds.centerY()

        when (currentShaderType) {
            ShaderType.SOLID -> {
                fillPaint.shader = null
                fillPaint.color = primaryColor
            }
            ShaderType.LINEAR_GRADIENT -> {
                fillPaint.shader = LinearGradient(
                    polygonBounds.left, polygonBounds.top,
                    polygonBounds.right, polygonBounds.bottom,
                    intArrayOf(primaryColor, secondaryColor, Color.YELLOW),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            ShaderType.RADIAL_GRADIENT -> {
                fillPaint.shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(primaryColor, secondaryColor, Color.WHITE),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            ShaderType.SWEEP_GRADIENT -> {
                fillPaint.shader = SweepGradient(
                    cx, cy,
                    intArrayOf(primaryColor, secondaryColor, Color.GREEN, primaryColor),
                    null
                )
            }
            ShaderType.TEXTURE_PATTERN -> {
                val patternBmp = generatePatternBitmap(currentTexturePattern)
                fillPaint.shader = BitmapShader(
                    patternBmp,
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT
                )
            }
        }
    }

    private fun generatePatternBitmap(pattern: TexturePattern): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        when (pattern) {
            TexturePattern.CHECKERBOARD -> {
                canvas.drawColor(primaryColor)
                p.color = secondaryColor
                canvas.drawRect(0f, 0f, 32f, 32f, p)
                canvas.drawRect(32f, 32f, 64f, 64f, p)
            }
            TexturePattern.DIAGONAL_HATCH -> {
                canvas.drawColor(primaryColor)
                p.color = secondaryColor
                p.strokeWidth = 8f
                p.style = Paint.Style.STROKE
                for (i in -64..128 step 16) {
                    canvas.drawLine(i.toFloat(), 0f, (i + 64).toFloat(), 64f, p)
                }
            }
            TexturePattern.POLKA_DOTS -> {
                canvas.drawColor(primaryColor)
                p.color = secondaryColor
                canvas.drawCircle(16f, 16f, 10f, p)
                canvas.drawCircle(48f, 48f, 10f, p)
            }
            TexturePattern.BRICK_WALL -> {
                canvas.drawColor(primaryColor)
                p.color = secondaryColor
                p.strokeWidth = 4f
                p.style = Paint.Style.STROKE
                // Horizontal lines
                canvas.drawLine(0f, 16f, 64f, 16f, p)
                canvas.drawLine(0f, 32f, 64f, 32f, p)
                canvas.drawLine(0f, 48f, 64f, 48f, p)
                // Vertical staggered lines
                canvas.drawLine(16f, 0f, 16f, 16f, p)
                canvas.drawLine(48f, 16f, 48f, 32f, p)
                canvas.drawLine(16f, 32f, 16f, 48f, p)
                canvas.drawLine(48f, 48f, 48f, 64f, p)
            }
        }
        return bitmap
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
}
