# 06. OpenGL ES 2.0 — GPU-Rendered 2D Shapes, GLSL Shaders, Buffers and Transforms

This module introduces a different rendering model from the earlier examples. The polygon, graph, cube, projection and robot features draw through Android's Canvas API. This feature instead asks **OpenGL ES 2.0** to render geometry using a programmable graphics pipeline: Kotlin creates vertex data, shader programs run on graphics hardware, vertex attributes feed shader inputs, transformation matrices map local coordinates into clip space, and primitive commands define which vertices form triangles.

This guide is grounded in the implementation from the uploaded project. It pins the important code, derives the mathematics, traces the Activity-to-renderer flow, and identifies production concerns visible in the source.

> Scope: this is a local 2D graphics demo using OpenGL ES 2.0. The shader and matrix techniques extend to 3D, but this module does not implement textures, lighting, imported 3D meshes, a depth-tested 3D scene, or a game engine.

## Direct source links

- [OpenGL2DShapeRenderer.kt](android-source/OpenGL2DShapeRenderer.kt) — renderer callbacks, GLSL, matrices, buffers, shape generation and draw calls.
- [OpenGLSurfaceView.kt](android-source/OpenGLSurfaceView.kt) — GLSurfaceView, OpenGL ES context setup, touch transforms and helper methods.
- [fragment_opengl_shapes.xml](android-source/fragment_opengl_shapes.xml) — drawing surface and controls.
- [MainActivity.kt](android-source/MainActivity.kt) — complete Activity wiring for the OpenGL section.
- [activity_main.xml](android-source/activity_main.xml) — host layout with the OpenGL screen include.
- [nav_menu.xml](android-source/nav_menu.xml) — drawer entry **nav_opengl**.
- [OpenGLMathTest.kt](android-source/OpenGLMathTest.kt) — polygon-coordinate and buffer-size tests.
- [Integrated project snapshot](complete-app/README.md) — source for all existing screens plus OpenGL.
- [Activity integration guide](MainActivity-integration.md) — a focused explanation of the UI bindings.

---

# 1. What the feature renders

The UI offers six shape choices:

| Selector | Implementation | Geometry |
|---|---|---|
| Triangle | **drawTriangle()** | 3 vertices, 1 triangle |
| Square | **drawSquare()** | 4 vertices, 6 indices, 2 triangles |
| Pentagon | **drawRegularPolygon(..., 5)** | Convex triangle fan |
| Hexagon | **drawRegularPolygon(..., 6)** | Convex triangle fan |
| Octagon | **drawRegularPolygon(..., 8)** | Convex triangle fan |
| Circle | **drawRegularPolygon(..., 36)** | 36-sided polygon approximation |

The user can choose a solid RGBA color, enable a per-vertex rainbow gradient, toggle auto-rotation, reset the transform, change scale, and drag over the drawing surface to rotate and translate the shape.

The learning value is the pipeline, not the number of shapes.

## Frame pipeline

~~~text
Activity controls / XML
        |
        | shape, color, scale, rotation, translation, mode
        v
OpenGLSurfaceView (GLSurfaceView)
        |
        | EGL/OpenGL surface + GL rendering thread
        v
OpenGL2DShapeRenderer : GLSurfaceView.Renderer
        |
        +--> onSurfaceCreated(): compile shaders, link program, build buffers
        |
        +--> onSurfaceChanged(): viewport + projection and view matrices
        |
        +--> onDrawFrame(): clear buffers, update animation, build MVP
                                  |
                                  +--> upload matrix and color uniforms
                                  +--> configure vertex attributes
                                  +--> submit glDrawArrays/glDrawElements
                                                |
                                                v
                                  Vertex shader transforms positions
                                                |
                                                v
                                  Primitive assembly and rasterization
                                                |
                                                v
                                  Fragment shader selects fragment color
                                                |
                                                v
                                       Surface framebuffer
~~~

This is fundamentally different from the earlier Canvas modules: instead of issuing a high-level drawPath call for each shape, the application prepares vertex data, configures the shader program and its inputs, and submits primitives to OpenGL ES.

---

# 2. Android architecture: why GLSurfaceView?

The custom wrapper is in **OpenGLSurfaceView.kt**.

~~~kotlin
class OpenGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = OpenGL2DShapeRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
~~~

- **GLSurfaceView** manages the Android surface used by OpenGL ES and coordinates a rendering thread.
- **setEGLContextClientVersion(2)** requests an OpenGL ES 2.0 context. It must be configured before the renderer is attached.
- **setRenderer()** registers the callback object that performs GL work.
- **RENDERMODE_CONTINUOUSLY** asks the view to request frames repeatedly instead of rendering only when explicitly invalidated.

The renderer implements **GLSurfaceView.Renderer**, which provides three lifecycle callbacks:

~~~kotlin
override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) { /* GL resources */ }
override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { /* viewport / projection */ }
override fun onDrawFrame(gl: GL10?) { /* one frame */ }
~~~

**onSurfaceCreated()** is for creating context-specific resources; **onSurfaceChanged()** reacts to a size change; **onDrawFrame()** draws one frame. The surface/context can be recreated, so resources such as shader programs must be rebuilt when that happens.

The host Activity is still a normal XML-based Android Activity. The Activity owns navigation and controls; the surface wrapper handles interaction and exposes state-setting methods; the renderer owns graphics data, matrices, shader interface state, and draw calls.

---

# 3. Coordinate spaces and homogeneous positions

The geometry values are not pixel positions. For example, the triangle is defined as:

~~~kotlin
val triCoords = floatArrayOf(
    0.0f,  0.5f, 0.0f,
   -0.5f, -0.4f, 0.0f,
    0.5f, -0.4f, 0.0f
)
~~~

Each vertex record has X, Y and Z components. The top vertex is (0, 0.5, 0), and the lower vertices are (−0.5, −0.4, 0) and (0.5, −0.4, 0). Z is zero for the shapes in this demo, which makes them planar.

The vertex shader's position input is a **vec4**, even though **glVertexAttribPointer()** supplies three components. OpenGL supplies the missing homogeneous W component as 1 for a three-component floating-point position attribute. Therefore the shader receives an equivalent position (x, y, z, 1).

Keep these spaces distinct:

1. **Model space:** the coordinates authored for the shape.
2. **World space:** where an object is placed in a scene. This example does not have a separate world transform.
3. **View/eye space:** coordinates relative to the camera.
4. **Clip space:** the result after multiplying by the model-view-projection matrix.
5. **Normalized device coordinates:** clip X/Y/Z divided by clip W.
6. **Framebuffer/viewport space:** pixel positions produced by the rasterization and viewport stages.

Mixing these spaces is one of the most common causes of graphics bugs. A model-space unit, a clip-space coordinate and a framebuffer pixel are not interchangeable.

---

# 4. Geometry, vertex records and index buffers

## 4.1 Triangle layout

The triangle's positions contain 3 vertices × 3 components = 9 floats. Its colors contain 3 vertices × 4 RGBA channels = 12 floats:

~~~kotlin
val triColors = floatArrayOf(
    1f, 0f, 0f, 1f,
    0f, 1f, 0f, 1f,
    0f, 0f, 1f, 1f
)
~~~

RGBA is normalized to [0,1]. The values (1,0,0,1), (0,1,0,1) and (0,0,1,1) represent opaque red, green and blue vertices. In rainbow mode the rasterizer interpolates these values over the triangle.

A colour's alpha component does not automatically create translucency. Blending needs to be enabled/configured if fragment alpha is to combine with the framebuffer. This demo does not enable **GL_BLEND**, and the prepared alpha values are all 1.0, so the output is opaque.

## 4.2 Square with indexed triangles

~~~kotlin
val sqCoords = floatArrayOf(
   -0.45f,  0.45f, 0.0f,
   -0.45f, -0.45f, 0.0f,
    0.45f, -0.45f, 0.0f,
    0.45f,  0.45f, 0.0f
)
val sqIndices = shortArrayOf(0, 1, 2, 0, 2, 3)
~~~

Four corners define the square, but the filled primitives used here are triangles. The index list defines two triangles: (0,1,2) and (0,2,3). They share the diagonal from vertex 0 to vertex 2.

This is an **indexed mesh**: the vertex record is stored once, and indices reference the records needed by each primitive. It prevents duplicating shared corners.

~~~kotlin
GLES20.glDrawElements(
    GLES20.GL_TRIANGLES,
    6,
    GLES20.GL_UNSIGNED_SHORT,
    squareIndexBuffer
)
~~~

The count is six because the index buffer contains six index values, i.e. two triangles. **GL_UNSIGNED_SHORT** matches the 16-bit values in the ShortBuffer. Larger index values require selecting an appropriate index type and supported OpenGL ES version/extension.

## 4.3 Triangles versus triangle fans

The standalone triangle uses **GL_TRIANGLES**: each group of three vertices forms one independent triangle.

The regular polygon draw function uses:

~~~kotlin
GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, sides + 2)
~~~

A triangle fan uses vertex 0 as the shared center; each later pair of perimeter vertices forms another triangle around that center.

~~~text
             perimeter point
                   /\
                  /  \
      previous -- center -- next
~~~

This is convenient for filling a convex regular polygon. It is not a general triangulation solution for arbitrary concave polygons.

---

# 5. Deriving regular polygon geometry

**Exact code location:** **OpenGL2DShapeRenderer.kt**, **buildRegularPolygonBuffers(sides, radius)**.

~~~kotlin
private fun buildRegularPolygonBuffers(
    sides: Int,
    radius: Float
): Pair<FloatBuffer, FloatBuffer> {
    val totalVerts = sides + 2
    val coords = FloatArray(totalVerts * 3)
    val colors = FloatArray(totalVerts * 4)

    coords[0] = 0f; coords[1] = 0f; coords[2] = 0f
    colors[0] = 1f; colors[1] = 1f; colors[2] = 1f; colors[3] = 1f

    for (i in 0..sides) {
        val angle = (2.0 * Math.PI * i / sides).toFloat()
        val x = radius * cos(angle.toDouble()).toFloat()
        val y = radius * sin(angle.toDouble()).toFloat()

        val vIdx = (i + 1) * 3
        coords[vIdx] = x
        coords[vIdx + 1] = y
        coords[vIdx + 2] = 0f

        val cIdx = (i + 1) * 4
        colors[cIdx] = (sin(angle.toDouble()) * 0.5 + 0.5).toFloat()
        colors[cIdx + 1] = (cos(angle.toDouble()) * 0.5 + 0.5).toFloat()
        colors[cIdx + 2] = (1.0 - (colors[cIdx] + colors[cIdx + 1]) / 2.0).toFloat()
        colors[cIdx + 3] = 1.0f
    }

    return Pair(createFloatBuffer(coords), createFloatBuffer(colors))
}
~~~

For N sides and radius r, the angle of perimeter sample i is:

$$
\theta_i=\frac{2\pi i}{N}
$$

The point on the circle is:

$$
x_i=r\cos(\theta_i), \qquad y_i=r\sin(\theta_i)
$$

The code samples from i = 0 through i = N inclusive. The final sample has angle 2π and repeats the first perimeter position (within floating-point precision), closing the fan boundary.

There is one center vertex plus N+1 perimeter records:

$$
V=1+(N+1)=N+2
$$

Each vertex has three position components and four color components. Therefore:

$$
\text{positionFloats}=3(N+2)
$$

$$
\text{colorFloats}=4(N+2)
$$

### Example: hexagon

For N = 6, the generator creates 8 vertex records: one center, six unique perimeter corners, and a repeated closing corner. Position storage contains 8 × 3 = 24 floats; color storage contains 8 × 4 = 32 floats. The fan generates six triangles.

### The "circle" is an approximation

The CIRCLE enum uses N = 36. It is a filled regular 36-gon with straight edges, not a native circular primitive. Increasing N improves the visual approximation but raises vertex and fragment work slightly. A shader-based signed-distance circle would be another approach, but it would use a different rendering technique.

---

# 6. Direct NIO buffers: memory layout and byte arithmetic

OpenGL ES consumes numeric buffers with a well-defined layout. The renderer uses **FloatBuffer** for positions/colors and **ShortBuffer** for square indices.

~~~kotlin
private fun createFloatBuffer(arr: FloatArray): FloatBuffer {
    return ByteBuffer.allocateDirect(arr.size * 4).run {
        order(ByteOrder.nativeOrder())
        asFloatBuffer().apply {
            put(arr)
            position(0)
        }
    }
}
~~~

- **arr.size * 4** allocates four bytes per float.
- **allocateDirect()** creates a direct NIO buffer suitable for native access.
- **nativeOrder()** selects the machine's native byte order.
- **asFloatBuffer()** views the allocated bytes as float elements.
- **put(arr)** copies the geometry data into the buffer.
- **position(0)** resets the cursor so the first record is the next element read.

The index buffer performs the analogous operation with **asShortBuffer()** and two bytes per index.

For example, 24 floats occupy:

$$
24 \times 4=96\text{ bytes}
$$

This is the arithmetic checked by **OpenGLMathTest**. The buffer byte count is not the number of vertices: those 24 position floats represent eight XYZ vertices.

## Structure of arrays versus interleaved records

This demo keeps positions and colors in separate buffers:

~~~text
Position: x0,y0,z0, x1,y1,z1, ...
Color:    r0,g0,b0,a0, r1,g1,b1,a1, ...
~~~

This is a structure-of-arrays layout. An interleaved alternative stores XYZ and RGBA together for each vertex. Both approaches are valid; the current separate-buffer design is easier to follow for learning and explains why each attribute has its own pointer.

---

# 7. The vertex shader: inputs, uniforms, varyings and clip position

The GLSL source inside the Kotlin renderer is:

~~~glsl
uniform mat4 uMVPMatrix;
attribute vec4 aPosition;
attribute vec4 aColor;
varying vec4 vColor;

void main() {
    gl_Position = uMVPMatrix * aPosition;
    vColor = aColor;
}
~~~

### Attribute

**aPosition** and **aColor** are per-vertex input values. The position data provides XYZ, while the shader input is a vec4; OpenGL fills missing W with 1.0 for this pointer configuration.

### Uniform

**uMVPMatrix** is shared by all vertices for the draw. The fragment shader's solid color and color-mode flag are also uniforms. Uniforms are suitable for values common to the whole draw operation; they are not repeated in every vertex buffer record.

### Varying

**vColor** carries a value from the vertex shader to the fragment shader. The rasterizer interpolates the values across each primitive's covered fragments. In rainbow mode this interpolation creates smooth transitions between vertex colors.

### gl_Position

**gl_Position** is the built-in clip-space position output. Assigning it does not directly choose a final pixel. Clipping, perspective division and viewport mapping happen in later stages of the graphics pipeline.

The mathematical transformation is:

$$
\mathbf p_{clip}=PVM\mathbf p_{model}
$$

The shape happens to be planar, but the same conceptual pipeline can support 3D positions.

---

# 8. Fragment shader: two color pipelines

The second shader is:

~~~glsl
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
~~~

It chooses between two color sources:

- **Solid:** the same uSolidColor for each fragment in the draw.
- **Rainbow:** the rasterizer-interpolated vColor varying.

**precision mediump float** selects medium floating-point precision in the fragment shader, a common OpenGL ES 2.0 practice on mobile GPUs. **gl_FragColor** is the ES 2.0-era fragment output variable; later shader versions use different output declarations.

The Kotlin mode switch is:

~~~kotlin
if (currentColorMode == ColorMode.SOLID) {
    GLES20.glUniform1i(useVertexColorHandle, 0)
    GLES20.glUniform4fv(solidColorHandle, 1, solidColorRGBA, 0)
    GLES20.glDisableVertexAttribArray(colorHandle)
} else {
    GLES20.glUniform1i(useVertexColorHandle, 1)
    GLES20.glEnableVertexAttribArray(colorHandle)
}
~~~

In solid mode the renderer sets the integer mode uniform to 0 and uploads four RGBA floats. In gradient mode it selects mode 1 and enables the vertex color array.

OpenGL is stateful: program, uniforms, attribute enable flags and pointer definitions govern later draws. The code therefore sets mode-dependent state before calling a draw method.

The vertex shader still assigns vColor = aColor in both modes. In solid mode the color array is disabled and the default generic attribute value is used, but the fragment shader follows the solid-color branch, so that varying is not the chosen output. A more advanced implementation might use separate shader variants or make attribute setup more explicit.

---

# 9. Creating and linking the shader program

The source performs these steps in **onSurfaceCreated()**:

~~~kotlin
val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

program = GLES20.glCreateProgram().also {
    GLES20.glAttachShader(it, vertexShader)
    GLES20.glAttachShader(it, fragmentShader)
    GLES20.glLinkProgram(it)
}
~~~

A shader object holds a compiled stage. The program combines the vertex and fragment stages into one linked executable. **glUseProgram(program)** later selects that program for draw calls.

### Critical reliability gap in the submitted implementation

The loadShader helper creates a shader, provides source and compiles it, but does not inspect GL_COMPILE_STATUS or read the shader info log. The program is also linked without checking GL_LINK_STATUS or retrieving the program info log. A compilation or link failure could therefore become a blank screen with no useful diagnostic.

A professional implementation should validate shader/program IDs and statuses, log compiler/linker errors, handle failure explicitly, and delete GL resources when they are no longer needed. Shader/program object lifetimes must be managed in the correct current GL context.

---

# 10. Vertex attributes and the meaning of stride

The triangle draw method configures positions as:

~~~kotlin
GLES20.glEnableVertexAttribArray(positionHandle)
GLES20.glVertexAttribPointer(
    positionHandle, 3, GLES20.GL_FLOAT, false, 12, triangleVertexBuffer
)
~~~

The parameters mean:

- **positionHandle:** the linked shader input location for aPosition.
- **3:** three values per vertex (XYZ).
- **GL_FLOAT:** each component is a floating-point value.
- **false:** do not normalize the stored numbers.
- **12:** byte offset between successive vertex records (stride).
- **triangleVertexBuffer:** source buffer.

Because each tightly packed record contains three 4-byte floats:

$$
stride_{position}=3\times4=12\text{ bytes}
$$

For RGBA color:

$$
stride_{color}=4\times4=16\text{ bytes}
$$

A stride is in bytes. The number of components is not. Confusing these units is a common cause of corrupted vertex layouts.

The renderer looks up attribute/uniform locations in **onDrawFrame()**:

~~~kotlin
positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
colorHandle = GLES20.glGetAttribLocation(program, "aColor")
mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
solidColorHandle = GLES20.glGetUniformLocation(program, "uSolidColor")
useVertexColorHandle = GLES20.glGetUniformLocation(program, "uUseVertexColor")
~~~

These locations are stable for a particular linked program. The current code queries them every frame; an optimization would cache them after successful linking and refresh them when the GL context/program is recreated. A location may be −1 if an input is optimized out or absent from the active shader, so robust code checks it.

---

# 11. Model, view, projection and MVP

**Exact model/MVP section:**

~~~kotlin
Matrix.setIdentityM(modelMatrix, 0)
Matrix.translateM(modelMatrix, 0, translateX, translateY, 0f)
Matrix.rotateM(modelMatrix, 0, rotationAngle, 0f, 0f, 1f)
Matrix.scaleM(modelMatrix, 0, scaleScale, scaleScale, 1.0f)

val tempMatrix = FloatArray(16)
Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
~~~

Three matrices have separate responsibilities:

- **Model M:** scale, rotate and translate the shape.
- **View V:** place the camera and establish its orientation.
- **Projection P:** map view coordinates to clip space.
- **MVP:** precombine them so each vertex needs just one shader matrix multiplication.

The pipeline is:

$$
\mathbf p_{clip}=PVM\mathbf p_{model}
$$

The code calculates tempMatrix = V × M and mvpMatrix = P × tempMatrix, so the effective product is P × V × M for the column-vector convention used in the vertex shader.

Matrix multiplication is not commutative:

$$
AB\ne BA\quad\text{in general}
$$

Therefore the transform order matters. The calls to Android's Matrix.translateM, rotateM, and scaleM compose the current matrix. For this call sequence, the effective model matrix is T × R × S; a column-vector point is scaled first, then rotated, then translated. The fact that translation is called first in Kotlin does not mean it is necessarily applied first to the input point.

The final upload sends one 4×4 matrix to the shader's uMVPMatrix uniform. count=1 means one matrix, false means no transpose, and offset 0 selects the start of the float array.

---

# 12. Viewport and orthographic projection

The surface callback configures both viewport and matrices:

~~~kotlin
override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    val ratio = width.toFloat() / Math.max(1, height).toFloat()

    Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1.0f, 1.0f, -1.0f, 1.0f)
    Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
}
~~~

**glViewport(0,0,width,height)** sets the pixel rectangle used for rendering. The aspect ratio is width divided by height; **Math.max(1,height)** prevents division by zero during temporary size callbacks.

The projection's horizontal range is −ratio to +ratio, while its vertical range is −1 to +1. For a wide surface, the wider horizontal range compensates for the larger framebuffer width so a circle-like shape is not stretched purely because the screen is landscape.

This is orthographic projection. It does not make the shapes shrink with depth. All demo vertices have Z=0, and there is no perspective frustum.

The view matrix uses an eye at (0,0,1), targets the origin (0,0,0), and defines +Y as up. It creates a simple front-facing camera for the 2D scene.

---

# 13. Per-vertex color generation and interpolation

The generator uses periodic RGB functions:

$$
r(\theta)=0.5\sin\theta+0.5
$$

$$
g(\theta)=0.5\cos\theta+0.5
$$

$$
b(\theta)=1-\frac{r(\theta)+g(\theta)}{2}
$$

Sine and cosine lie between −1 and +1. Multiplying by 0.5 and adding 0.5 maps the values into [0,1]. Alpha is fixed at 1.0. These expressions create a cyclic, rainbow-like set of colors around the perimeter, not a perceptually uniform color space or a physically accurate spectrum.

The center vertex is white. In a triangle fan, each triangle includes that center plus two adjacent perimeter vertices. GPU interpolation blends the three vertex colors across each triangle's fragments, so each region transitions from the white center toward its neighboring perimeter colors.

This is the distinction between vertex coloring and a solid uniform: the renderer does not calculate every pixel color in Kotlin. The vertex outputs are interpolated by the graphics pipeline before the fragment shader selects them.

---

# 14. The render loop and automatic rotation

The frame starts by clearing buffers, optionally updating rotation, and activating the shader program:

~~~kotlin
override fun onDrawFrame(gl: GL10?) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    if (isAutoRotating) {
        rotationAngle = (rotationAngle + 1.5f) % 360f
    }

    GLES20.glUseProgram(program)
    // Build/upload matrices and mode uniforms, bind attributes and issue a draw.
}
~~~

The background is initialized to white using **glClearColor(1,1,1,1)**. The current geometry is planar and depth testing is not enabled, so clearing the depth buffer is not essential for this 2D scene, although it is harmless.

Auto-rotation adds 1.5 degrees per rendered frame. This is a frame-based animation, so effective angular speed varies with frame rate. A time-based approach uses elapsed time:

$$
\theta(t+\Delta t)=\theta(t)+\omega\Delta t
$$

Here ω is angular velocity and Δt is elapsed seconds. This is the recommended design when consistent motion matters across different display refresh rates.

The surface uses continuous rendering, meaning it requests frames even when the shape is idle. That is simple for learning and animation, but can waste power for static scenes. RENDERMODE_WHEN_DIRTY combined with explicit frame requests can be preferable when nothing is animating.

---

# 15. Touch input and transforms

The touch handler stores the previous touch coordinate, then applies incremental deltas:

~~~kotlin
val dx = event.x - lastTouchX
val dy = event.y - lastTouchY

renderer.rotationAngle = (renderer.rotationAngle + dx * 0.5f) % 360f
renderer.translateX += dx * 0.002f
renderer.translateY -= dy * 0.002f

lastTouchX = event.x
lastTouchY = event.y
~~~

Horizontal drag adds 0.5 degrees per pixel to the rotation and also changes X translation by 0.002 per pixel. Vertical drag subtracts its delta from Y translation because Android screen Y grows downward whereas the model's conventional positive Y direction points upward.

The deltas are incremental because the handler updates lastTouchX and lastTouchY after applying them. This prevents the movement from being repeatedly applied relative to the gesture's original start.

Limitations:
- The translation factor is in raw pixels and is not normalized by viewport width/height or display density, so the same finger movement may have different normalized effects on different devices.
- Kotlin's remainder operator is not a positive modulo; leftward dragging can yield a negative rotation angle. The shape still rotates for negative angles, but a displayed normalized angle should be adjusted.
- performClick() is called on touch-down, even when the gesture may become a drag. More complete accessibility behavior separates click and drag semantics.
- The touch handler changes renderer state on the UI thread, while onDrawFrame() reads it on the GL thread. See the next section.

---

# 16. Thread ownership: UI thread versus GL thread

This is an important engineering issue in the submitted implementation.

Activity click listeners and onTouchEvent() usually run on the Android UI thread. GLSurfaceView.Renderer callbacks run on a dedicated GL thread. The current helper methods directly mutate properties such as currentShape, scaleScale, translateX, translateY, rotationAngle, currentColorMode, solidColorRGBA and isAutoRotating; the renderer reads those properties while drawing.

That means renderer state is shared between threads without an explicit synchronization boundary. It may appear to work for simple interactions, but unsynchronized cross-thread access does not guarantee consistent visibility or atomic grouping of related state changes.

A standard fix is to enqueue changes onto the GL thread with queueEvent, for example:

~~~kotlin
fun setShape(shape: OpenGL2DShapeRenderer.ShapeType) {
    queueEvent {
        renderer.currentShape = shape
    }
}
~~~

The same idea should be applied consistently for multi-field changes. UI labels should still be changed on the main thread. If the Activity needs a value from the renderer, send it back using a UI-thread callback instead of reading live GL-owned state directly from the UI thread.

The source in this module preserves the implementation from the ZIP; the thread-safe approach above is a recommended engineering improvement.

---

# 17. Shader validation and GPU resource lifecycle

The current loadShader() helper creates a shader, provides source and compiles it, but does not inspect GL_COMPILE_STATUS or read the shader info log. Program linking similarly does not validate GL_LINK_STATUS or read the program info log.

A production implementation should:
1. Validate each shader ID before using it.
2. Check compile status and log compilation diagnostics.
3. Check program link status and log link diagnostics.
4. Avoid issuing draw calls with an invalid/unlinked program.
5. Delete temporary shader objects when appropriate and delete programs when the GL context is being torn down.
6. Rebuild program and buffer resources after GL context recreation.

The source also resolves attribute/uniform locations inside every onDrawFrame() even though they remain stable for a linked program. Cache them after a successful link. The draw loop allocates a new 16-float temporary matrix each frame; make it a reusable renderer field to avoid repeated short-lived allocations.

These performance differences may be small for one triangle but matter more as the number of draw calls and frame rate grow.

---

# 18. Activity wiring and controls

The OpenGL destination is included in the common root layout with ID **layoutOpenGL**, initially set to GONE. Its navigation ID is **nav_opengl**. The Activity's setup method resolves the custom surface, shape radio group, six color buttons, rainbow button, auto-rotate button, reset button, scale slider, and scale label.

The scale mapping is:

$$
scale=\frac{\max(progress,10)}{80}
$$

With slider maximum 280 and initial progress 80, the scale range is 0.125× to 3.5× and starts at 1.0×. The minimum progress clamp avoids a zero scale. Choosing a shape updates the enum; choosing a solid-color button updates the RGBA uniform values and solid mode; rainbow mode enables per-vertex color input; auto-rotation changes frame state; reset restores scale, translation and angle.

The XML puts the OpenGL surface in a weighted FrameLayout and the controls in a ScrollView. The drawing region expands to the remaining height, while the selector/color/transform panel remains scrollable on smaller displays.

---

# 19. Algorithms and complexity

Let N be the number of sides in a generated regular polygon and V be the number of generated vertex records.

- Vertex and color generation is O(N): one pass over N+1 perimeter samples.
- Buffer storage is O(N): 3 position floats and 4 color floats per vertex record.
- Triangle fan submission describes N triangles for N sides.
- The square uses four unique vertex records and six indices to submit two triangles.
- Matrix operations are fixed-size 4×4 operations, so the transform setup is O(1) per frame for this single-shape scene.
- Actual GPU work also depends on covered fragments/pixels. Scaling up a polygon can increase fragment work without changing its vertex count.

For professional performance work, measure frame time and GPU/CPU load rather than assuming a tiny vertex count means the whole frame is inexpensive. Shader execution, fill rate, repeated state queries, surface refresh rate and thread synchronization also matter.

---

# 20. Tests and what they establish

**OpenGLMathTest.kt** contains two host-side unit tests:

1. A six-sided regular polygon generator test confirms that the first perimeter point at angle zero is approximately (0.5,0) and that center plus seven perimeter entries requires 24 position floats.
2. A buffer-allocation test confirms 24 floats × 4 bytes = 96 bytes.

These tests exercise geometry/data-layout arithmetic. They do not create an OpenGL context, compile the GLSL source, assert successful program linking, validate GPU buffer binding, or compare rendered pixels. A GL smoke test on a compatible emulator/device is needed to cover those behaviors.

The uploaded ZIP includes saved Gradle XML reports timestamped 2026-10-28 05:37 UTC. The seven suites list 17 tests, zero failures, zero errors and zero skipped. These results come from the archive, not a fresh run performed in this workflow.

---

# 21. Production gaps visible in this implementation

1. **Unchecked shader compile and program link status.** Failure can become a silent blank/incorrect frame.
2. **GL handles queried every frame.** Cache them until program recreation.
3. **Per-frame temporary array allocation.** Reuse the tempMatrix array.
4. **Unsynchronized UI/GL shared state.** Use queueEvent or an explicit renderer-state hand-off.
5. **Frame-rate-dependent auto-rotation.** Use elapsed time for consistent angular speed.
6. **Continuous rendering while idle.** Consider when-dirty mode for static scenes.
7. **No explicit GL resource deletion.** Manage shader/program lifetimes across context loss.
8. **36-gon circle approximation.** A higher segment count or shader-based circle can improve edge smoothness.
9. **No blending.** Add explicit blend state if transparency is introduced.
10. **Touch is pixel-factor based.** Normalize deltas for cross-device consistency.
11. **No GL pipeline tests.** Add context/device-based smoke tests and error reporting.
12. **No 3D material/texture/light model.** The current gradients are vertex colors, not textures or lighting.

These are targeted next steps, not claims that the submitted source already implements them.

---

# 22. Suggested engineering exercises

1. Draw the triangle's vertex table and manually count position/color floats.
2. Derive the square's two triangles from its index buffer.
3. Generate a 10-gon and calculate expected vertex, float and triangle counts.
4. Explain why the polygon has N+2 vertex records and why the perimeter loop repeats the first corner.
5. Add unit tests for a pentagon's first and opposite vertex coordinates.
6. Modify the rainbow color formula and predict its values at 0, π/2, π and 3π/2.
7. Trace one position from model coordinates through the MVP matrix and the vertex shader.
8. Change the aspect ratio and explain why the orthographic horizontal bounds change.
9. Implement shader compilation/link status checks and report compiler info logs.
10. Move uniform/attribute lookups out of the frame loop and reuse the temporary matrix.
11. Change the renderer wrapper setters to enqueue updates through queueEvent().
12. Convert auto-rotation to elapsed-time-based angular velocity.
13. Add GLSurfaceView lifecycle forwarding in the Activity and observe behavior across pause/resume and navigation.
14. Add blending and a test for RGBA colors before introducing transparency.
15. Add a device/emulator smoke test that verifies the program links and at least one shape draw completes.

**Core mental model:** the CPU prepares geometry and state, but the programmable graphics pipeline transforms vertices, assembles primitives, rasterizes their coverage and invokes fragment shading. Understanding the boundary between Kotlin, buffers, GL state, shader inputs and frame lifecycle is the foundation for moving from Android Canvas drawing to GPU graphics.
