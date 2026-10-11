# 04. 3D Camera Projections — Perspective vs Orthographic

This module adds a camera-projection learning screen to the existing Polygon Studio application. It is the next step after drawing 2D shapes, applying affine transformations, and transforming a 3D cube.

The focus is to understand **how a 3D mesh becomes a 2D image**: camera-space coordinates, 4×4 projection matrices, field of view, aspect ratio, homogeneous (w)-division, normalized device coordinates (NDC), near/far depth mapping, and the visual differences between perspective and orthographic projection.

> The source below documents the uploaded implementation. It intentionally identifies implementation limitations rather than pretending the sample is a complete production 3D renderer.

## Repository contents

~~~text
examples/04-3d-camera-projections/
├── README.md
├── MainActivity-integration.md
├── android-source/
│   ├── ProjectionsView.kt
│   ├── MainActivity.kt
│   ├── CameraProjectionMatrixTest.kt
│   ├── fragment_projections.xml
│   ├── activity_main.xml
│   └── nav_menu.xml
└── complete-app/
    ├── README.md
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    ├── gradle/libs.versions.toml
    └── app/
        ├── build.gradle.kts
        └── src/
            ├── main/java/com/example/test/
            │   ├── PolygonDrawingView.kt
            │   ├── AdvancedStylingView.kt
            │   ├── AffineTransformView.kt
            │   ├── CanvasGraphsView.kt
            │   ├── Cube3DView.kt
            │   ├── ProjectionsView.kt
            │   └── MainActivity.kt
            ├── main/res/layout/
            ├── main/res/menu/nav_menu.xml
            └── test/java/com/example/test/
                ├── PolygonLogicTest.kt
                ├── AffineMatrixTest.kt
                ├── Homogeneous3DMatrixTest.kt
                └── CameraProjectionMatrixTest.kt
~~~

The `android-source/` folder isolates the new feature for focused study. `complete-app/` includes the earlier five sections and the new projection section together, including the modified Activity, projection layout, navigation, tests and build configuration. Generated Gradle caches, build intermediates and the generated APK are intentionally not treated as source files.

---

# 1. What the feature does

The projection screen renders a small, colored 3D house mesh and lets the user switch between two projection modes:

- **Perspective projection:** objects appear smaller as their camera-space distance increases.
- **Orthographic projection:** projected size does not depend on depth; parallel edges remain parallel in the image.

Controls are exposed for field of view, object distance, rotation about the Y axis, horizontal/vertical projection scale, and near/far depth planes. The active 4×4 matrix is displayed live, along with a count of vertices whose normalized depth falls outside the code's ([-1,1]) interval.

## The rendering pipeline

~~~text
House vertices (local/model coordinates)
        |
        v
Rotate around the Y axis and translate along Z
        |
        v
Camera-space vertices
        |
        v
Build perspective OR orthographic 4×4 matrix
        |
        v
Transform each Vector4 into clip/homogeneous coordinates
        |
        v
Divide X, Y and Z by W to get NDC coordinates
        |
        v
Convert NDC X/Y into Android screen coordinates
        |
        +--> draw perspective projection rays (perspective mode only)
        |
        v
Sort mesh faces by average camera-space Z
        |
        v
Build and draw one Android Path per face
        |
        v
Draw green/red vertex markers and update matrix/depth UI
~~~

This is a software-rendered graphics exercise built on an Android custom `View`, `Canvas`, `Path` and `Paint`. It is not using OpenGL, a hardware depth buffer, or a full GPU rasterization pipeline.

---

# 2. Android custom View architecture

## 2.1 The custom View declaration

**File:** `android-source/ProjectionsView.kt`

~~~kotlin
class ProjectionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    // Projection state, 3D mesh, matrix math and drawing live here.
}
~~~

### Why each part matters

- `View` is the Android UI component responsible for drawing the feature.
- `context` provides the Android environment.
- `AttributeSet?` carries XML attributes when Android inflates the custom View from a layout.
- `defStyleAttr` supports Android's style/theme mechanism.
- `@JvmOverloads` generates useful Java-compatible constructor overloads. In this case it also allows the View to be instantiated from XML using the constructor shape Android expects.

The XML declares the view directly:

~~~xml
<com.example.test.ProjectionsView
    android:id="@+id/projectionsView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
~~~

**Android concept:** a custom View owns a rendering surface. The Activity configures its state through properties and listeners; the View turns the current state into pixels during `onDraw()`.

## 2.2 Projection mode as typed state

~~~kotlin
enum class ProjectionType { PERSPECTIVE, ORTHOGRAPHIC }

var currentProjectionType = ProjectionType.PERSPECTIVE
~~~

An enum limits the selection to two named modes instead of relying on loosely typed strings such as `"perspective"` or `"ortho"`. The default is perspective projection.

## 2.3 The reusable matrix and Paint/Path objects

~~~kotlin
private val projectionMatrix = FloatArray(16)

private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}
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
private val vertexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}
private val facePath = Path()
~~~

The 16-element array stores the 4×4 matrix. Reusing the matrix, Paints and Path avoids rebuilding these helper objects as local variables for every face on every draw. The mesh has only eight faces, so the current design is appropriate for an educational demo; a larger renderer would also pay attention to the temporary collections allocated later in `onDraw()`.

---

# 3. The 3D house: vertices and faces

## 3.1 Ten vertices describe the house

~~~kotlin
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
~~~

Each vertex is a point ($$(x,y,z)$$). Indices 0–7 describe the base box: a rear rectangle at (z=-80), a front rectangle at (z=+80), and the corresponding corners. Indices 8 and 9 are the roof peaks at the back and front. The roof peak is centered horizontally at (x=0) and higher vertically at (y=120).

Coordinates are not Android screen pixels yet. They are the mesh's model-space coordinates, later rotated, translated, projected and mapped onto the screen.

## 3.2 A face is stored as vertex indices plus a color

~~~kotlin
data class MeshFace(val indices: IntArray, val color: Int)

private val houseFaces = arrayOf(
    MeshFace(intArrayOf(4, 5, 6, 7), Color.argb(190, 229, 57, 53)),  // Front Wall
    MeshFace(intArrayOf(1, 0, 3, 2), Color.argb(190, 30, 136, 229)), // Back Wall
    MeshFace(intArrayOf(0, 4, 7, 3), Color.argb(190, 142, 36, 170)),// Left Wall
    MeshFace(intArrayOf(5, 1, 2, 6), Color.argb(190, 251, 140, 0)),// Right Wall
    MeshFace(intArrayOf(7, 6, 9), Color.argb(210, 76, 175, 80)),   // Front Roof Triangle
    MeshFace(intArrayOf(2, 3, 8), Color.argb(210, 0, 150, 136)),   // Back Roof Triangle
    MeshFace(intArrayOf(7, 9, 8, 3), Color.argb(210, 251, 192, 45)), // Left Roof Slope
    MeshFace(intArrayOf(6, 9, 8, 2), Color.argb(210, 255, 112, 67)) // Right Roof Slope
)
~~~

The actual file uses these arrays of index values, with comments and color values. A face does not duplicate its points; it points back to shared vertices. Four indices define each wall or roof slope, while three indices define the triangular roof ends.

This is a common mesh representation:

- **Vertex array:** the geometric positions.
- **Face/index array:** which vertices connect to make each surface.
- **Face material/color:** how that surface is drawn.

The code uses semi-transparent ARGB colors (alpha values 190 or 210) to fill surfaces, followed by a black outline.

---

# 4. Homogeneous coordinates and 4×4 matrix multiplication

## 4.1 Why does the point have four components?

The feature uses a `Vector4` instead of only `Vector3`:

~~~kotlin
data class Vector4(
    var x: Float,
    var y: Float,
    var z: Float,
    var w: Float = 1.0f
) {
    fun transform(m: FloatArray): Vector4 {
        val rx = m[0] * x + m[1] * y + m[2] * z + m[3] * w
        val ry = m[4] * x + m[5] * y + m[6] * z + m[7] * w
        val rz = m[8] * x + m[9] * y + m[10] * z + m[11] * w
        val rw = m[12] * x + m[13] * y + m[14] * z + m[15] * w
        return Vector4(rx, ry, rz, rw)
    }
}
~~~

Homogeneous coordinates make translation and projection fit into matrix multiplication. A normal 3D position is represented as ($(x,y,z,1)$). After a perspective projection, the output (w') is generally not 1; the later division by (w') creates perspective foreshortening.

The code stores a 4×4 matrix in a flat array, row by row:

~~~text
[ m[0]   m[1]   m[2]   m[3]  ]
[ m[4]   m[5]   m[6]   m[7]  ]
[ m[8]   m[9]   m[10]  m[11] ]
[ m[12]  m[13]  m[14]  m[15] ]
~~~

The output is the matrix multiplied by the vector:

$$
x'=m_0x+m_1y+m_2z+m_3w
$$

$$
y'=m_4x+m_5y+m_6z+m_7w
$$

$$
z'=m_8x+m_9y+m_{10}z+m_{11}w
$$

$$
w'=m_{12}x+m_{13}y+m_{14}z+m_{15}w
$$

The method performs four dot products, each with four terms. Its time complexity is $O(1)$ per point because the matrix dimension is fixed.

## 4.2 Camera-space transform: rotate and move the house away from the camera

~~~kotlin
val radY = Math.toRadians(objectRotationY.toDouble())
val cosY = cos(radY).toFloat()
val sinY = sin(radY).toFloat()

val cameraSpaceVertices = Array(houseVertices.size) { i ->
    val v = houseVertices[i]
    val rx = v.x * cosY + v.z * sinY
    val ry = v.y
    val rz = -v.x * sinY + v.z * cosY - objectDistanceZ
    Vector4(rx, ry, rz, 1.0f)
}
~~~

The Y-axis rotation is the ordinary 3D rotation:

$$
x_r=xcos	heta+zsin	heta
$$

$$
y_r=y
$$

$$
z_r=-xsin	heta+zcos	heta
$$

Then the code subtracts `objectDistanceZ` from Z. In this sample's right-handed camera convention, the house is moved into negative Z, so points in front of the camera have (z<0). The projection matrix assumes that convention.

`Math.toRadians()` converts the angle from the UI, which is in degrees, into radians for Kotlin's `sin()` and `cos()`.

**Important scope detail:** the house is rotated only around Y in this feature. The scale sliders modify projection coefficients, not the mesh's 3D vertex coordinates. This is not a complete model-view-camera system with freely adjustable X/Y/Z object rotation.

---

# 5. Perspective projection

## 5.1 The exact matrix builder

**Code location:** `ProjectionsView.kt`, method `buildPerspectiveMatrix(m, aspect)`.

~~~kotlin
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
~~~

### The matrix's shape

The nonzero entries correspond to this matrix:

$$
P=
\begin{bmatrix}
f_x & 0 & 0 & 0 \\
0 & f_y s_y & 0 & 0 \\
0 & 0 & \frac{F+N}{N-F} & \frac{2FN}{N-F} \\
0 & 0 & -1 & 0
\end{bmatrix}
$$

Here (N) is the near plane and (F) is the far plane (the README uses capital letters in equations to distinguish the far distance from a generic variable).

For an input camera-space point ($(x,y,z,1)$), the outputs are:

$$
x'=f_xx, \quad y'=f_y s_y y
$$

$$
z'=\frac{F+N}{N-F}z+\frac{2FN}{N-F}
$$

$$
w'=-z
$$

Notice the matrix places $-1$ at index 14 and $0$ at index 15. Thus W depends on the point's camera-space Z coordinate. This is the key behavior that makes division by W dependent on distance.

## 5.2 Field of view and focal scale

The code computes:

$$
f_y=\frac{1}{\tan(\text{FOV}/2)}
$$

This is why changing the field of view changes apparent size. A smaller FOV produces a larger $f_y$, magnifying the center region; a wider FOV produces a smaller $f_y$, showing a wider angular region with objects appearing smaller.

The FOV is treated as a **vertical** field of view. It is converted from degrees to radians before taking the tangent.

The aspect ratio is:

~~~kotlin
val aspect = w / Math.max(1f, h)
~~~

where (w) and (h) are the custom View's current width and height. Dividing the horizontal focal scale by the aspect ratio compensates for the shape of the viewport:

$$
f_x=\frac{f_y}{\text{aspect}}s_x
$$

The call to `Math.max(1f, h)` prevents a division by zero if the View is temporarily measured with zero height.

The `scaleSx` and `scaleSy` properties multiply the horizontal and vertical projection gains:

$$
x_{NDC}=\frac{f_xx}{-z},qquad
y_{NDC}=\frac{f_ys_yy}{-z}
$$

These settings are projection-scale controls; they do not rescale the stored mesh vertices.

## 5.3 Why objects become smaller with distance

The first part of the perspective projection produces:

$$
x_{NDC}=\frac{f_xx}{-z}
$$

$$
y_{NDC}=\frac{f_ys_yy}{-z}
$$

If the same (x,y) position is moved farther away (larger (-z)), its NDC offset gets smaller. This is perspective foreshortening.

A simplified intuition:

~~~text
same-sized object near camera  -> larger projected offsets
same-sized object far away     -> smaller projected offsets
~~~

The actual view also rotates the object and has a near/far depth transformation, so its entire output depends on more than a single scalar depth.

---

# 6. Orthographic projection

## 6.1 The exact matrix builder

**Code location:** `ProjectionsView.kt`, method `buildOrthographicMatrix(m, width, height)`.

~~~kotlin
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
~~~

This mode keeps the homogeneous W equal to the input W (normally 1) because the bottom row is ([0,0,0,1]). The projection does not divide X/Y by a depth-dependent W value. As a result, depth does not shrink the object's projected width or height.

### Conceptual comparison

| Property | Perspective | Orthographic |
|---|---|---|
| W output | (w'=-z) for this matrix | (w'=w), normally 1 |
| X/Y divide by depth-dependent W | Yes | No |
| Same-size object farther away | Appears smaller | Stays the same projected size |
| Parallel lines | Can converge in the image | Remain parallel under the projection |
| Main controls in this app | FOV, distance, rotation, scale, near/far | Distance, rotation, scale, near/far; FOV is not used by the orthographic matrix |

**Important implementation note:** the uploaded orthographic matrix calculates `orthoW = width * 0.5f` and then uses `2f / orthoW`. That results in a horizontal coefficient of (4/width); after the later screen-coordinate multiplication by (width/2), the source X offset becomes (2x) before `scaleSx). The vertical calculation has the same factor-of-two behavior. A conventional mapping in which one model-space unit is one screen-space unit would normally use a coefficient of (2/width) against the full width, or (1/(width/2)) against a half-width. The current code is preserved from the ZIP; the behavior is called out here rather than silently corrected.

The orthographic depth coefficients are still built from the near and far values, so the depth diagnostic changes when the object crosses those planes even though apparent X/Y size is independent of depth.

---

# 7. Homogeneous division and Normalized Device Coordinates

## 7.1 The projection dispatch in onDraw

~~~kotlin
when (currentProjectionType) {
    ProjectionType.PERSPECTIVE -> buildPerspectiveMatrix(projectionMatrix, aspect)
    ProjectionType.ORTHOGRAPHIC -> buildOrthographicMatrix(projectionMatrix, w, h)
}

val projectedHomogeneous = Array(cameraSpaceVertices.size) { i ->
    cameraSpaceVertices[i].transform(projectionMatrix)
}
~~~

The selected projection method fills the same 16-element array. Every camera-space vertex is then transformed with the `Vector4.transform()` method.

## 7.2 Divide by W

~~~kotlin
val screenPoints = Array(projectedHomogeneous.size) { i ->
    val p = projectedHomogeneous[i]
    val wDiv = if (p.w != 0f) p.w else 1.0f

    val xNdc = p.x / wDiv
    val yNdc = p.y / wDiv
    val zNdc = p.z / wDiv

    val isClipped = zNdc < -1.0f || zNdc > 1.0f
    if (isClipped) clippedCount++

    val sx = cx + xNdc * (w / 2f)
    val sy = cy - yNdc * (h / 2f)

    Triple(sx, sy, isClipped)
}
~~~

The normalized device coordinates are:

$$
x_{NDC}=\frac{x'}{w'},quad
y_{NDC}=\frac{y'}{w'},quad
z_{NDC}=\frac{z'}{w'}
$$

NDC is a normalized coordinate space; for the depth convention in these matrices, the near and far planes map to (-1) and (+1).

The implementation uses `wDiv = 1.0f` as a fallback when (w') is zero. This avoids division by zero in the arithmetic, but it is not a physically correct treatment of a point at the projection singularity. A production renderer would explicitly reject or clip that case.

## 7.3 Convert normalized coordinates to Android screen coordinates

~~~kotlin
val sx = cx + xNdc * (w / 2f)
val sy = cy - yNdc * (h / 2f)
~~~

The screen center is ((c_x,c_y)=(width/2,height/2)).

- NDC X is added to the screen center, moving right for positive X.
- NDC Y is subtracted from the screen center, because Android screen coordinates increase downward while conventional projected Y increases upward.
- Multiplying by half the View width/height maps normalized coordinates into pixel-like screen offsets.

The output data structure is a `Triple`: screen X, screen Y and the depth-outside-range Boolean.

---

# 8. Near plane, far plane, and depth mapping

In perspective mode, the source uses:

$$
A=\frac{F+N}{N-F},qquad B=\frac{2FN}{N-F}
$$

and:

$$
z'=Az+B,qquad w'=-z
$$

For camera-space Z values under this source's negative-Z-forward convention:

- at the near plane, (z=-N): (z_{NDC}=-1)
- at the far plane, (z=-F): (z_{NDC}=+1)

The orthographic Z coefficients are:

$$
z'=\frac{2}{N-F}z+\frac{F+N}{N-F}
$$

with (w'=1); substituting (z=-N) and (z=-F) produces the same endpoints (-1) and (+1).

These mappings let the sample identify vertices whose normalized depth is outside ([-1,1]).

## What does the code call "clipping"?

The count is calculated here:

~~~kotlin
val isClipped = zNdc < -1.0f || zNdc > 1.0f
if (isClipped) clippedCount++
~~~

And the count is passed to the Activity using a callback:

~~~kotlin
onProjectionMatrixUpdatedListener?.invoke(projectionMatrix.clone(), clippedCount)
~~~

The Activity updates the text color to green when the count is zero and red when any of the ten vertices lies outside this depth interval.

**Accuracy limit:** this is a **near/far depth diagnostic**, not full view-frustum clipping. The current implementation does not test X/Y NDC against the horizontal/vertical frustum boundaries. It also does not clip a polygon against a plane or remove clipped vertices from faces: face drawing later still uses every point. The ray-drawing pass skips vertices flagged by the depth check, and the vertex marker turns red, but that visual flag should not be confused with a complete geometric clipper.

This distinction is very important when learning graphics programming.

---

# 9. The rendering order inside onDraw

The method has several explicit phases.

## 9.1 Calculate viewport information

~~~kotlin
val w = width.toFloat()
val h = height.toFloat()
val cx = w / 2f
val cy = h / 2f
val aspect = w / Math.max(1f, h)
~~~

The current measured View size determines the projection aspect ratio and screen center. Using runtime dimensions is more resilient than assuming every device has a fixed screen size.

## 9.2 Build the chosen projection matrix, then transform the mesh

The projection matrix is recomputed from the current UI state each time `onDraw()` runs. Next, the rotated/translated model points are multiplied by that matrix. This is why moving a SeekBar and calling `invalidate()` makes the next frame change.

## 9.3 Display the matrix safely

~~~kotlin
onProjectionMatrixUpdatedListener?.invoke(projectionMatrix.clone(), clippedCount)
~~~

The `clone()` passes a snapshot of the coefficients to the Activity. It avoids exposing the same mutable array that the custom View will overwrite during a later draw. The Activity formats those 16 numbers into four rows of text using a monospace TextView.

The callback is invoked during drawing, so this is a compact teaching implementation. A larger app could separate computation from presentation and update the label only when matrix values actually change.

## 9.4 Sort faces using the Painter's Algorithm

~~~kotlin
val sortedFaces = houseFaces.map { face ->
    val avgCameraZ = face.indices
        .map { idx -> cameraSpaceVertices[idx].z }
        .average()
        .toFloat()
    Pair(face, avgCameraZ)
}.sortedBy { it.second } // Far (-Z) to near
~~~

The average camera-space Z is calculated for every face. Since the camera looks along negative Z in this example, more negative average Z values are farther away. Sorting in ascending order draws far faces first and nearer faces later.

This is the Painter's Algorithm: later faces paint over earlier faces. It is a simple approach for this small fixed mesh. It is **not** a depth buffer, per-pixel visibility test, or robust solution for intersecting/overlapping polygons; averaging depth can place a face incorrectly for more complicated shapes.

## 9.5 Projection rays in perspective mode

~~~kotlin
if (currentProjectionType == ProjectionType.PERSPECTIVE) {
    for (pt in screenPoints) {
        if (!pt.third) {
            canvas.drawLine(cx, cy, pt.first, pt.second, rayPaint)
        }
    }
}
~~~

The green dashed lines make the perspective idea visible by connecting the screen center to each projected vertex that passes the depth check. They are an educational overlay, not a complete visual representation of every camera ray or clipping plane. The ray overlay is not drawn in orthographic mode.

## 9.6 Build and draw each face

~~~kotlin
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

    facePaint.color = face.color
    canvas.drawPath(facePath, facePaint)
    canvas.drawPath(facePath, strokePaint)
}
~~~

- `reset()` clears the previous face geometry.
- `moveTo()` moves to the first corner.
- `lineTo()` connects remaining corners.
- `close()` closes the polygon.
- First draw a filled face, then draw the black outline.

Android's `Path` is 2D. The 3D work was already done: the face's vertex indices now refer to projected 2D points. That separation—3D math first, 2D drawing second—is central to understanding this demo.

## 9.7 Vertex marker colors

~~~kotlin
for (pt in screenPoints) {
    vertexPaint.color =
        if (pt.third) "#E53935".toColorInt() else "#00E676".toColorInt()
    canvas.drawCircle(pt.first, pt.second, 9f, vertexPaint)
}
~~~

The point flag selects red for a depth-outside vertex and green for a vertex whose depth is within the code's accepted interval. These are depth-status colors only; as mentioned above, "green" does not guarantee the vertex is also inside the X/Y frustum bounds.

---

# 10. Android UI wiring — exactly how the controls work

## 10.1 The screen layout

**File:** `android-source/fragment_projections.xml`

It consists of:

1. A status/header TextView.
2. A FrameLayout containing the custom `ProjectionsView`.
3. A ScrollView for projection mode, matrix display, status and SeekBars.

The custom view occupies the flexible area:

~~~xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1"
    android:background="#FFFFFF">

    <com.example.test.ProjectionsView
        android:id="@+id/projectionsView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
~~~

The controls are inside a ScrollView so their combined height can exceed the available screen area.

## 10.2 Projection type RadioGroup

In `MainActivity.kt` the selection updates the typed property:

~~~kotlin
rgProjectionType.setOnCheckedChangeListener { _, checkedId ->
    when (checkedId) {
        R.id.rbPerspective ->
            projectionsView.currentProjectionType = ProjectionsView.ProjectionType.PERSPECTIVE
        R.id.rbOrthographic ->
            projectionsView.currentProjectionType = ProjectionsView.ProjectionType.ORTHOGRAPHIC
    }
    projectionsView.invalidate()
}
~~~

The `RadioGroup` produces a resource ID. The `when` maps that ID to one of the two enum values. `invalidate()` schedules another draw with the selected matrix.

## 10.3 Field of view slider

~~~kotlin
sbFov.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    val fov = 20f + progress
    tvFovLabel.text = "Field of View (FOV): ${fov.toInt()}°"
    projectionsView.fovDegrees = fov
    projectionsView.invalidate()
})
~~~

The XML gives the SeekBar a maximum of 100 and initial progress 40. The mapping is:

$$
FOV=20^circ+progress
$$

so the slider represents 20° through 120°; its initial value is 60°. FOV changes perspective focal coefficients. In orthographic mode the matrix builder does not read `fovDegrees`, so moving that slider doesn't change the orthographic projection matrix.

## 10.4 Object distance slider

~~~kotlin
sbObjectDistance.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    val dist = 200f + progress
    tvObjectDistanceLabel.text = "Object Z Distance: ${dist.toInt()} px"
    projectionsView.objectDistanceZ = dist
    projectionsView.invalidate()
})
~~~

With maximum progress 1300, the effective distance ranges from 200 to 1500. It is subtracted from each rotated vertex's Z coordinate, moving the whole object along the camera Z axis. In perspective mode, this also affects apparent size through (x/w) and (y/w).

## 10.5 Object Y rotation

~~~kotlin
sbObjectRotation.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    tvObjectRotationLabel.text = "3D Y Rotation: ${progress}°"
    projectionsView.objectRotationY = progress.toFloat()
    projectionsView.invalidate()
})
~~~

The XML gives a 0–360 range. It changes the angle used in the Y-axis rotation equations in section 4.2, revealing different faces of the house.

## 10.6 Horizontal and vertical projection scale

~~~kotlin
sbScaleSx.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    val sx = progress.coerceAtLeast(10) / 80f
    tvScaleSxLabel.text = String.format(Locale.US, "Scale Sx: %.2fx", sx)
    projectionsView.scaleSx = sx
    projectionsView.invalidate()
})
~~~

The Y-scale listener follows the same mapping for `scaleSy`. A minimum progress of 10 prevents an exact zero coefficient. With max progress 280, both sliders map to 0.125× through 3.5×. These values multiply the projection matrix's X/Y coefficients; they are not model-space geometry scaling.

## 10.7 Near and far planes

~~~kotlin
sbNearPlane.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    val n = 10f + progress
    tvNearPlaneLabel.text = "Near Clipping Plane (N): ${n.toInt()} px"
    projectionsView.nearPlane = n
    projectionsView.invalidate()
})

sbFarPlane.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    val f = 500f + progress
    tvFarPlaneLabel.text = "Far Clipping Plane (F): ${f.toInt()} px"
    projectionsView.farPlane = f
    projectionsView.invalidate()
})
~~~

- Near (N=10+progress), giving 10–400 with max progress 390.
- Far (F=500+progress), giving 500–3000 with max progress 2500.

These ranges make sure that the UI-controlled far plane stays beyond the near plane. The matrix coefficients include both values, and the depth mapping changes when the controls move.

## 10.8 Why one helper is shared by the listeners

~~~kotlin
private fun createSeekBarChangeListener(
    onProgressChanged: (progress: Int) -> Unit
): SeekBar.OnSeekBarChangeListener {
    return object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: SeekBar?,
            progress: Int,
            fromUser: Boolean
        ) {
            if (fromUser) onProgressChanged(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
~~~

This helper avoids repeating the same listener boilerplate seven times. The callback runs only when the user is moving the control. This is useful because the app may set SeekBar progress programmatically during setup/reset; those changes should not always be interpreted as new user intent.

## 10.9 Showing the matrix to the learner

The projection matrix callback updates the text:

~~~kotlin
projectionsView.onProjectionMatrixUpdatedListener = { m, clippedCount ->
    updateMatrix4Display(tvProjectionMatrixDisplay, m)
    // Also update status text/color based on clippedCount.
}
~~~

The complete Activity formats the 16 coefficients as four text rows. `Locale.US` gives consistent decimal separators, and two decimal places keep the matrix readable. Watching those entries while moving FOV, near/far or scale sliders helps connect the UI to the actual matrix calculations.

---

# 11. Navigation and app integration

The projection screen is integrated with the existing five-screen app rather than replacing them.

### Host layout

The integrated host layout includes:

~~~xml
<include
    android:id="@+id/layoutProjections"
    layout="@layout/fragment_projections"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone" />
~~~

It begins hidden while the basic polygon screen is visible. The navigation selection makes the selected included layout visible and hides the other sections.

### Navigation menu

~~~xml
<item
    android:id="@+id/nav_projections"
    android:icon="@android:drawable/ic_menu_view"
    android:title="3D Projections (Perspective vs. Ortho)" />
~~~

The Activity adds a `nav_projections` case, locates `layoutProjections`, calls `setupProjectionsSection()`, and handles its controls. Detailed integration snippets are in [MainActivity-integration.md](MainActivity-integration.md); the full integrated `MainActivity.kt` is also in `complete-app/app/src/main/java/com/example/test/MainActivity.kt`.

---

# 12. Programming and DSA fundamentals demonstrated

This example is mostly graphics math, but it uses several core data structures and algorithms.

| Fundamental | Exact implementation | Purpose |
|---|---|---|
| Fixed array | `houseVertices`, `houseFaces` | Stores the mesh's points and connectivity |
| Flat numeric array | `FloatArray(16)` | Stores a 4×4 matrix |
| Data class | `Vector4`, `MeshFace` | Gives structure to vertex and face data |
| Indexed traversal | `Array(size) { i -> ... }` | Transforms each vertex by index |
| Mapping | `houseFaces.map { face -> ... }` | Associates every face with a depth value |
| Sorting | `sortedBy { it.second }` | Chooses a far-to-near drawing order |
| Aggregation | `average()` | Estimates a face's depth from its vertices |
| Enum/state | `ProjectionType` | Makes rendering mode explicit |
| Callback/lambda | `onProjectionMatrixUpdatedListener` | Sends matrix/status values to UI |
| Reusable helpers | `Paint`, `Path`, matrix array | Keeps drawing configuration and workspace objects |

### Big-O notes

Let (V) be the number of vertices and (F) the number of faces.

- Applying the fixed 4×4 matrix to every vertex is (O(V)) because each vertex requires a constant number of arithmetic operations.
- Calculating average depth for all faces is (O(V_f)), where (V_f) is the total number of face-index references visited.
- Sorting the faces is (O(Flog F)).
- Drawing loops are proportional to the total number of vertices/face corners drawn.

Here (V=10) and (F=8), so runtime is tiny. For a large mesh, temporary arrays/lists created by `Array`, `map`, `average`, `Pair` and sorting would deserve closer performance attention.

---

# 13. Tests: what they verify and what they do not

**File:** `android-source/CameraProjectionMatrixTest.kt`

The unit tests cover three things:

1. **Perspective matrix structure:** computes the FOV scale factors and checks (m[0]), (m[5]), (m[14]), and that the depth coefficient is negative for the configured right-handed convention.
2. **Orthographic matrix structure:** checks X/Y scaling coefficients and (m[15]=1).
3. **Perspective division and depth NDC:** checks sample values (300/500=0.6), (-150/500=-0.3), and (-250/500=-0.5), then verifies the sample Z lies inside ([-1,+1]).

### Test limitation

These tests reconstruct representative matrix values in test code. They do not instantiate `ProjectionsView` and call its private matrix-building functions, nor do they render a View and assert its pixels. They are useful formula/index checks, but they are not end-to-end projection or clipping tests.

### Test results present in the uploaded ZIP

The archive contains generated `testDebugUnitTest` XML reports for five suites: `ExampleUnitTest` (1 test), `Homogeneous3DMatrixTest` (2), `CameraProjectionMatrixTest` (3), `PolygonLogicTest` (2), and `AffineMatrixTest` (3). Those stored results show **11 tests with 0 failures and 0 errors**.

I attempted a fresh Gradle run while preparing this module. It did **not** rerun here: the wrapper attempted to download Gradle 9.4.1 from `services.gradle.org`, but outbound network resolution was unavailable. The 11-test count above is reported from the submitted archive's existing test-result files, not from a successful new test run during this update.

---

# 14. Important limitations of this implementation

The feature is a useful teaching example; keep these implementation details in mind when extending it.

1. **Depth-only clipping diagnostic.** The code compares only `zNdc` to ([-1,+1]). It does not test X/Y against the side planes, nor does it cut faces at plane intersections.
2. **Faces are always drawn.** Even if a vertex has the red clipping flag, the mesh face path still uses its projected coordinates. Only the projection-ray loop skips flagged points.
3. **Orthographic scale factor.** The code sets `orthoW=width/2` and then uses `2/orthoW`, producing a factor of two in the final screen offsets. See section 6.1.
4. **Painter's Algorithm approximation.** One average Z per face is not a depth buffer and can fail with intersecting or complicated overlapping surfaces.
5. **W=0 fallback.** Replacing a zero W with 1 prevents arithmetic division by zero but does not correctly clip a point on the perspective singularity.
6. **Only Y object rotation.** The house's camera-space preprocessing exposes Y rotation rather than the full six-degree camera/model transform pipeline.
7. **No camera X/Y movement or look-at matrix.** The camera is conceptually fixed and the house is translated along Z.
8. **Fixed mesh and fixed topology.** The house is a small example with ten points and eight surfaces, not a general arbitrary-model loader.
9. **UI callback during drawing.** The matrix/status callback fires from `onDraw()`. This is adequate for the demo, but a production implementation would usually avoid repeatedly setting TextView text unless the values change.

These are not claims that the screen implements more than it does; they are the key next engineering steps if you continue this feature.

---

# 15. Recommended learning order

1. Read the vertex list and sketch the ten points.
2. Trace each face's index array to locate its corners.
3. Write the Y-rotation equations on paper and calculate one vertex manually.
4. Draw the 4×4 perspective matrix by rows, matching each value to its array index.
5. Substitute one camera-space point into `Vector4.transform()`.
6. Calculate (w'=-z), then divide X/Y/Z by W.
7. Map NDC coordinates to screen coordinates and explain why Y uses subtraction.
8. Substitute (z=-N) and (z=-F) into the depth mapping to derive (-1) and (+1).
9. Switch to orthographic mode and compare what happens when distance changes.
10. Read the clipping code and distinguish “flagging a vertex” from actually clipping geometry.
11. Move the controls one at a time and watch the matrix coefficients.
12. Read the tests, then extend them so they test shared pure matrix-building functions instead of duplicating matrix expressions inside the tests.

**Learning focus:** understand how the view transforms a point, why homogeneous W matters, how the projection matrix differs between perspective and orthographic modes, and which parts of a true 3D rendering pipeline this sample has not implemented.
