package com.example.test

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

class OpenGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = OpenGL2DShapeRenderer()

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init {
        setEGLContextClientVersion(2) // OpenGL ES 2.0
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
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
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                renderer.rotationAngle = (renderer.rotationAngle + dx * 0.5f) % 360f
                renderer.translateX += dx * 0.002f
                renderer.translateY -= dy * 0.002f

                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setShape(shape: OpenGL2DShapeRenderer.ShapeType) {
        renderer.currentShape = shape
    }

    fun setColorMode(mode: OpenGL2DShapeRenderer.ColorMode) {
        renderer.currentColorMode = mode
    }

    fun setSolidColorRGBA(r: Float, g: Float, b: Float, a: Float = 1.0f) {
        renderer.solidColorRGBA[0] = r
        renderer.solidColorRGBA[1] = g
        renderer.solidColorRGBA[2] = b
        renderer.solidColorRGBA[3] = a
        renderer.currentColorMode = OpenGL2DShapeRenderer.ColorMode.SOLID
    }

    fun setScale(scale: Float) {
        renderer.scaleScale = scale
    }

    fun setAutoRotate(enabled: Boolean) {
        renderer.isAutoRotating = enabled
    }

    fun resetShapeTransform() {
        renderer.scaleScale = 1.0f
        renderer.translateX = 0f
        renderer.translateY = 0f
        renderer.rotationAngle = 0f
    }
}
