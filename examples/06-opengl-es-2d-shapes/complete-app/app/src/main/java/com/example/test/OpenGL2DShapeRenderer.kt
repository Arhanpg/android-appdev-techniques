package com.example.test

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class OpenGL2DShapeRenderer : GLSurfaceView.Renderer {

    enum class ShapeType { TRIANGLE, SQUARE, PENTAGON, HEXAGON, OCTAGON, CIRCLE }
    enum class ColorMode { SOLID, RAINBOW_GRADIENT }

    var currentShape = ShapeType.TRIANGLE
    var currentColorMode = ColorMode.SOLID

    var solidColorRGBA = floatArrayOf(0.12f, 0.53f, 0.90f, 1.0f) // Dodger Blue default
    var scaleScale = 1.0f
    var translateX = 0f
    var translateY = 0f
    var rotationAngle = 0f
    var isAutoRotating = false

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vColor = aColor;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        uniform vec4 uSolidColor;
        uniform int uUseVertexColor;
        void main() {
            if (uUseVertexColor == 1) {
                gl_FragColor = vColor;
            } else {
                gl_FragColor = uSolidColor;
            }
        }
    """.trimIndent()

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    private var solidColorHandle = 0
    private var useVertexColorHandle = 0

    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // --- SHAPE BUFFERS ---
    private lateinit var triangleVertexBuffer: FloatBuffer
    private lateinit var triangleColorBuffer: FloatBuffer

    private lateinit var squareVertexBuffer: FloatBuffer
    private lateinit var squareColorBuffer: FloatBuffer
    private lateinit var squareIndexBuffer: ShortBuffer

    private lateinit var pentagonVertexBuffer: FloatBuffer
    private lateinit var pentagonColorBuffer: FloatBuffer

    private lateinit var hexagonVertexBuffer: FloatBuffer
    private lateinit var hexagonColorBuffer: FloatBuffer

    private lateinit var octagonVertexBuffer: FloatBuffer
    private lateinit var octagonColorBuffer: FloatBuffer

    private lateinit var circleVertexBuffer: FloatBuffer
    private lateinit var circleColorBuffer: FloatBuffer
    private var circleVertexCount = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        initShapeBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / Math.max(1, height).toFloat()

        // Orthographic projection matrix
        Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1.0f, 1.0f, -1.0f, 1.0f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (isAutoRotating) {
            rotationAngle = (rotationAngle + 1.5f) % 360f
        }

        GLES20.glUseProgram(program)

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        solidColorHandle = GLES20.glGetUniformLocation(program, "uSolidColor")
        useVertexColorHandle = GLES20.glGetUniformLocation(program, "uUseVertexColor")

        // Build Model Matrix
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, translateX, translateY, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationAngle, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, scaleScale, scaleScale, 1.0f)

        // Combine MVP = Projection * View * Model
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        if (currentColorMode == ColorMode.SOLID) {
            GLES20.glUniform1i(useVertexColorHandle, 0)
            GLES20.glUniform4fv(solidColorHandle, 1, solidColorRGBA, 0)
            GLES20.glDisableVertexAttribArray(colorHandle)
        } else {
            GLES20.glUniform1i(useVertexColorHandle, 1)
            GLES20.glEnableVertexAttribArray(colorHandle)
        }

        // Draw Selected 2D Shape
        when (currentShape) {
            ShapeType.TRIANGLE -> drawTriangle()
            ShapeType.SQUARE -> drawSquare()
            ShapeType.PENTAGON -> drawRegularPolygon(pentagonVertexBuffer, pentagonColorBuffer, 5)
            ShapeType.HEXAGON -> drawRegularPolygon(hexagonVertexBuffer, hexagonColorBuffer, 6)
            ShapeType.OCTAGON -> drawRegularPolygon(octagonVertexBuffer, octagonColorBuffer, 8)
            ShapeType.CIRCLE -> drawRegularPolygon(circleVertexBuffer, circleColorBuffer, 36)
        }
    }

    private fun drawTriangle() {
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, triangleVertexBuffer)

        if (currentColorMode == ColorMode.RAINBOW_GRADIENT) {
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, triangleColorBuffer)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun drawSquare() {
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, squareVertexBuffer)

        if (currentColorMode == ColorMode.RAINBOW_GRADIENT) {
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, squareColorBuffer)
        }

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, squareIndexBuffer)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun drawRegularPolygon(vBuf: FloatBuffer, cBuf: FloatBuffer, sides: Int) {
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vBuf)

        if (currentColorMode == ColorMode.RAINBOW_GRADIENT) {
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, cBuf)
        }

        // Render polygon using TRIANGLE_FAN
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, sides + 2)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun initShapeBuffers() {
        // Triangle (3 vertices)
        val triCoords = floatArrayOf(
            0.0f,  0.5f, 0.0f,
           -0.5f, -0.4f, 0.0f,
            0.5f, -0.4f, 0.0f
        )
        val triColors = floatArrayOf(
            1f, 0f, 0f, 1f, // Red
            0f, 1f, 0f, 1f, // Green
            0f, 0f, 1f, 1f  // Blue
        )
        triangleVertexBuffer = createFloatBuffer(triCoords)
        triangleColorBuffer = createFloatBuffer(triColors)

        // Square (4 vertices + 6 indices)
        val sqCoords = floatArrayOf(
           -0.45f,  0.45f, 0.0f,
           -0.45f, -0.45f, 0.0f,
            0.45f, -0.45f, 0.0f,
            0.45f,  0.45f, 0.0f
        )
        val sqColors = floatArrayOf(
            1f, 0f, 0f, 1f, // Red
            1f, 1f, 0f, 1f, // Yellow
            0f, 1f, 0f, 1f, // Green
            0f, 0f, 1f, 1f  // Blue
        )
        val sqIndices = shortArrayOf(0, 1, 2, 0, 2, 3)

        squareVertexBuffer = createFloatBuffer(sqCoords)
        squareColorBuffer = createFloatBuffer(sqColors)
        squareIndexBuffer = ByteBuffer.allocateDirect(sqIndices.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(sqIndices)
                position(0)
            }
        }

        // Regular Polygons
        buildRegularPolygonBuffers(5, 0.5f).also { pentagonVertexBuffer = it.first; pentagonColorBuffer = it.second }
        buildRegularPolygonBuffers(6, 0.5f).also { hexagonVertexBuffer = it.first; hexagonColorBuffer = it.second }
        buildRegularPolygonBuffers(8, 0.5f).also { octagonVertexBuffer = it.first; octagonColorBuffer = it.second }
        buildRegularPolygonBuffers(36, 0.5f).also { circleVertexBuffer = it.first; circleColorBuffer = it.second }
    }

    private fun buildRegularPolygonBuffers(sides: Int, radius: Float): Pair<FloatBuffer, FloatBuffer> {
        val totalVerts = sides + 2
        val coords = FloatArray(totalVerts * 3)
        val colors = FloatArray(totalVerts * 4)

        // Center point
        coords[0] = 0f; coords[1] = 0f; coords[2] = 0f
        colors[0] = 1f; colors[1] = 1f; colors[2] = 1f; colors[3] = 1f // White center

        for (i in 0..sides) {
            val angle = (2.0 * Math.PI * i / sides).toFloat()
            val x = radius * cos(angle.toDouble()).toFloat()
            val y = radius * sin(angle.toDouble()).toFloat()

            val vIdx = (i + 1) * 3
            coords[vIdx] = x
            coords[vIdx + 1] = y
            coords[vIdx + 2] = 0f

            val cIdx = (i + 1) * 4
            // Rainbow gradient interpolation along perimeter
            colors[cIdx]     = (sin(angle.toDouble()) * 0.5 + 0.5).toFloat()
            colors[cIdx + 1] = (cos(angle.toDouble()) * 0.5 + 0.5).toFloat()
            colors[cIdx + 2] = (1.0 - (colors[cIdx] + colors[cIdx + 1]) / 2.0).toFloat()
            colors[cIdx + 3] = 1.0f
        }

        return Pair(createFloatBuffer(coords), createFloatBuffer(colors))
    }

    private fun createFloatBuffer(arr: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(arr.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(arr)
                position(0)
            }
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
