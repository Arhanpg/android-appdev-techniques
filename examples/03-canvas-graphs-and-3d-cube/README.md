# 03. 2D Canvas Graphs & 3D Homogeneous Cube

This learning module documents the two newest features from the Polygon Studio Android app:

1. **2D Canvas Graphs** — an arc/pie-style gauge, a rounded bar chart, and a smooth line spline rendered manually with Android Canvas.
2. **3D Homogeneous Cube** — eight 3D vertices, six colored faces, a 4×4 homogeneous transform, 3D-to-2D screen mapping, depth-based face ordering, touch rotation, timed auto-spin, and a live matrix display.

This is a **learning example**, not a charting or 3D-rendering library. The data in the graphs are fixed demo values. The cube is drawn with Android's 2D `Canvas` and `Path`, not OpenGL.

## Folder map

```text
examples/03-canvas-graphs-and-3d-cube/
├── README.md
├── android-source/
│   ├── CanvasGraphsView.kt
│   ├── Cube3DView.kt
│   ├── Homogeneous3DMatrixTest.kt
│   ├── fragment_canvas_graphs.xml
│   ├── fragment_cube_3d.xml
│   └── nav_menu.xml
└── MainActivity-integration.md
```

The two custom View classes are the core implementations. The XML files provide the controls used by the original app. The integration guide pinpoints how the existing `MainActivity` connects them to the side navigation and UI controls.

---

# Part I — 2D Canvas Graphs

## 1. The feature set at a glance

The enum defines the modes that the custom View can display:

```kotlin
enum class GraphType { ALL, ARC_PIE, RECT_BAR, PATH_LINE }

var currentGraphType = GraphType.ALL
```

- `ALL`: places the arc chart in the upper left, bar chart in the upper right, and line graph across the bottom.
- `ARC_PIE`: only draws the arc/pie-style gauge.
- `RECT_BAR`: only draws the bar chart.
- `PATH_LINE`: only draws the smooth line graph and the filled area under it.

This is a small example of **state-driven drawing**. The selected enum value changes, then `invalidate()` requests a redraw, and `onDraw()` selects the corresponding rendering method.

## 2. Important adjustable properties

```kotlin
var currentGraphType = GraphType.ALL

var arcStartAngle = 0f
var arcSweepAngle = 270f

var rectCornerRadius = 24f
var rectHeightScale = 1.0f

var pathCurviness = 1.0f
```

| Property | Purpose in the current implementation |
|---|---|
| `currentGraphType` | Picks one graph or all three |
| `arcStartAngle` | Start angle for the arc/gauge |
| `arcSweepAngle` | Total angle distributed across the colored segments |
| `rectCornerRadius` | X/Y corner radii for rounded bars |
| `rectHeightScale` | Multiplies every bar's calculated height |
| `pathCurviness` | Multiplies the normalized Y-values of the line chart |

The current UI exposes graph type, sweep angle, bar height scale, and path curviness. `arcStartAngle` and `rectCornerRadius` are properties but do not have sliders in this layout.

## 3. Paints, geometry objects, and demo data

The View holds reusable drawing objects:

```kotlin
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
```

**Why keep these as fields?**

- The Paints hold reusable styling choices.
- `RectF` holds the dimensions of the current arc and bar.
- `Path` stores the polyline/curve geometry.
- `areaPath` represents the closed area under the line graph separately from the visible curve.

The demo chart data is:

```kotlin
private val sliceColors = intArrayOf(
    "#1E88E5".toColorInt(), // Blue
    "#E53935".toColorInt(), // Red
    "#4CAF50".toColorInt(), // Green
    "#FBC02D".toColorInt(), // Yellow
    "#8E24AA".toColorInt()  // Purple
)

private val barValues = floatArrayOf(0.4f, 0.85f, 0.6f, 0.95f, 0.3f)
```

These are **hard-coded illustrative values**, not live data from a backend or a data source.

## 4. The main graph dispatcher

The start of `onDraw()` is:

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val w = width.toFloat()
    val h = height.toFloat()

    when (currentGraphType) {
        GraphType.ARC_PIE ->
            drawArcPieChart(canvas, w / 2f, h / 2f, Math.min(w, h) * 0.35f)

        GraphType.RECT_BAR ->
            drawRectBarChart(canvas, 40f, 60f, w - 80f, h - 120f)

        GraphType.PATH_LINE ->
            drawPathLineChart(canvas, 40f, 60f, w - 80f, h - 120f)

        GraphType.ALL -> {
            val halfW = w / 2f
            val halfH = h / 2f

            drawArcPieChart(
                canvas, halfW / 2f, halfH / 2f,
                Math.min(halfW, halfH) * 0.35f
            )
            drawRectBarChart(canvas, halfW + 20f, 30f, halfW - 40f, halfH - 60f)
            drawPathLineChart(canvas, 40f, halfH + 40f, w - 80f, halfH - 80f)
        }
    }
}
```

The important implementation patterns are:

1. Read the measured View width and height at draw time.
2. Derive drawing positions from those dimensions rather than hard-coding a single screen size.
3. Dispatch to a focused method for each graph.
4. Share the same Canvas but give each graph its own bounds.

The `ALL` mode uses a deliberately simple three-panel layout; it is not a constraint-based chart layout engine.

---

## 5. Graph 1 — Arc / pie-style gauge using `Canvas.drawArc()`

### Exact arc geometry and segment calculation

```kotlin
private fun drawArcPieChart(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
    arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

    var currentAngle = arcStartAngle
    val sliceAngle = arcSweepAngle / sliceColors.size

    for (i in sliceColors.indices) {
        fillPaint.color = sliceColors[i]
        canvas.drawArc(arcBounds, currentAngle, sliceAngle, true, fillPaint)

        strokePaint.strokeWidth = 4f
        strokePaint.color = Color.WHITE
        canvas.drawArc(arcBounds, currentAngle, sliceAngle, true, strokePaint)

        currentAngle += sliceAngle
    }

    strokePaint.strokeWidth = 8f
    strokePaint.color = "#1565C0".toColorInt()
    canvas.drawArc(arcBounds, arcStartAngle, arcSweepAngle, false, strokePaint)

    textPaint.textSize = 24f
    canvas.drawText("Canvas.drawArc() Pie/Gauge", cx, cy + radius + 32f, textPaint)
}
```

### Why it works

**Step 1 — bounding rectangle**

```kotlin
arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
```

Android's `drawArc()` takes a bounding rectangle, not a center/radius pair. The code converts the center `(cx, cy)` and radius to `left, top, right, bottom`.

**Step 2 — split the sweep**

```kotlin
val sliceAngle = arcSweepAngle / sliceColors.size
```

There are five colors. The requested total sweep is split into five equal angular segments. At the default 270° sweep, each segment is (270/5 = 54°).

**Important interpretation:** this is an illustrative *equal-segment gauge/pie-style graphic*. Segment sizes are not proportional to numeric input data, because there is no numeric slice-value array.

**Step 3 — draw a filled wedge**

```kotlin
canvas.drawArc(arcBounds, currentAngle, sliceAngle, true, fillPaint)
```

The fourth argument after the angles is `useCenter = true`. That connects the arc endpoints to the oval's center, creating a pie-slice wedge.

**Step 4 — separate outline styling**

The white 4-pixel stroke draws the boundary accents between segments. Then the dark-blue accent arc is drawn with `useCenter = false`, so it traces only the outer arc and doesn't create radial lines to the center.

### Relevant APIs

- `RectF.set(...)`
- `Canvas.drawArc(...)`
- `Paint.Style.FILL`
- `Paint.Style.STROKE`
- `Color` and `toColorInt()`
- iteration over `sliceColors.indices`

---

## 6. Graph 2 — Rounded bar chart using rectangles

### Demo values and bar layout

```kotlin
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

    fillPaint.color = sliceColors[i % sliceColors.size]
    canvas.drawRoundRect(barBounds, rectCornerRadius, rectCornerRadius, fillPaint)

    strokePaint.strokeWidth = 3f
    strokePaint.color = Color.BLACK
    canvas.drawRoundRect(barBounds, rectCornerRadius, rectCornerRadius, strokePaint)

    textPaint.textSize = 20f
    val label = "${(barValues[i] * 100).toInt()}%"
    canvas.drawText(label, barLeft + barWidth / 2f, barTop - 8f, textPaint)
}
```

### The formulas and coordinate logic

- `count` is the number of bars (five).
- `totalGaps = barGap × (count + 1)` reserves a gap before the first bar, between bars, and after the last bar.
- `barWidth = (availableWidth - totalGaps) / count` evenly distributes the remaining width.
- `scaledHeight = chartHeight × value × rectHeightScale` sets the height.
- `barTop = baselineY - scaledHeight` positions the top above the baseline, because screen Y coordinates increase downward.
- `barBounds` represents the rectangle consumed by Canvas.

The label converts normalized values into percentages: `0.85` becomes `85%`.

**Color reuse:** `sliceColors[i % sliceColors.size]` uses the modulo operator. This cycles through the palette safely if the number of bars ever exceeds the number of colors.

The baseline itself is drawn with:

```kotlin
strokePaint.strokeWidth = 5f
strokePaint.color = Color.GRAY
canvas.drawLine(x, baselineY, x + width, baselineY, strokePaint)
```

### User-controlled scale

In the original Activity, the bar-height SeekBar maps an integer progress to a floating-point multiplier:

```kotlin
val scale = progress / 100f
canvasGraphsView.rectHeightScale = scale
canvasGraphsView.invalidate()
```

The XML sets a maximum progress of 150 and an initial progress of 100, so the user can adjust the multiplier up to 1.5×. Values above 1.0 can make tall bars extend beyond the originally allocated chart height; the implementation does not clamp the resulting bar height to the plot bounds.

---

## 7. Graph 3 — Smooth line spline using `Path.cubicTo()`

### Normalized demo data

```kotlin
val samplePoints = arrayOf(
    Pair(0f, 0.2f),
    Pair(0.2f, 0.7f),
    Pair(0.4f, 0.35f),
    Pair(0.6f, 0.9f),
    Pair(0.8f, 0.5f),
    Pair(1.0f, 0.8f)
)
```

Each `Pair(xFraction, yFraction)` stores normalized coordinates from roughly 0 to 1. They are proportions of the chart area, not screen pixels.

### Convert normalized points into screen coordinates

```kotlin
val baselineY = y + height

val p1X = x + samplePoints[i].first * width
val p1Y = baselineY - (samplePoints[i].second * height * pathCurviness)

val p2X = x + samplePoints[i + 1].first * width
val p2Y = baselineY - (samplePoints[i + 1].second * height * pathCurviness)
```

The X fraction is measured from the left edge. The Y fraction is measured upward from the chart baseline, so it is subtracted from `baselineY`.

The multiplier `pathCurviness` currently scales point heights relative to the baseline. It does **not** compute a mathematical curvature parameter; at values greater than 1.0, points can be pushed above the original plotting range.

### Cubic Bézier segments

```kotlin
val controlX1 = p1X + (p2X - p1X) / 2f
val controlY1 = p1Y
val controlX2 = p1X + (p2X - p1X) / 2f
val controlY2 = p2Y

graphPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2X, p2Y)
```

A cubic Bézier segment is defined by:

- start point (P_0) — the current Path position
- control point (P_1)
- control point (P_2)
- end point (P_3)

The general curve is:

[
B(t)=(1-t)^3P_0+3(1-t)^2tP_1+3(1-t)t^2P_2+t^3P_3,quad 0\leq t\leq1
]

This implementation puts both control points at the midpoint X between the consecutive samples, while their Y positions are taken from the two endpoints. That creates the smooth, rounded transition used by this example. It is a specific curve construction, not a general-purpose spline-fitting algorithm.

### Area under the curve

The code constructs a second Path:

```kotlin
graphPath.moveTo(startX, startY)
areaPath.moveTo(startX, baselineY)
areaPath.lineTo(startX, startY)

// For each consecutive sample, call cubicTo() on both paths.

val lastX = x + samplePoints.last().first * width
areaPath.lineTo(lastX, baselineY)
areaPath.close()
```

The line Path describes only the curve. The area Path starts on the baseline, follows the same curve and then closes along the baseline. Closing the area Path gives Canvas a filled region.

### Gradient fill and cleanup

```kotlin
fillPaint.shader = LinearGradient(
    x, y, x, baselineY,
    Color.argb(160, 30, 136, 229),
    Color.argb(20, 30, 136, 229),
    Shader.TileMode.CLAMP
)
canvas.drawPath(areaPath, fillPaint)
fillPaint.shader = null
```

The gradient transitions from a more opaque blue at the top to a more transparent blue near the baseline. Setting `fillPaint.shader = null` afterward matters because the same Paint is reused later to draw the magenta data-point circles. Without clearing the shader, those circles could inherit the area gradient instead of using their intended solid color.

The line itself is drawn with `canvas.drawPath(graphPath, strokePaint)`; data points are drawn as circles at each normalized sample coordinate.

---

# Part II — 3D Homogeneous Cube

## 8. What this cube renders — and what it does not

The View stores 8 three-dimensional corners and 6 quadrilateral faces. It transforms the corners using a 4×4 matrix, maps X/Y to the 2D canvas, sorts faces by average Z, and draws the faces and their outlines.

It is a **software-drawn 3D-style cube rendered through 2D APIs**. The current code does not use OpenGL, a GPU 3D pipeline, a camera projection matrix, triangle rasterization, or a depth buffer.

## 9. A four-component homogeneous vector

```kotlin
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
```

A point is represented as ((x,y,z,w)), normally with (w=1) for a position. Multiplying a matrix by a vector produces a new vector. In the code, a flat 16-element array represents a row-major 4×4 matrix:

```text
indices  0  1  2  3
         4  5  6  7
         8  9 10 11
        12 13 14 15
```

The transform computes one dot product per output row. Translation values can be stored in positions 3, 7, and 11 because the vector includes homogeneous (w).

---

## 10. The eight cube vertices

```kotlin
private val cubeVertices = arrayOf(
    Vector4(-100f, -100f, -100f), // 0
    Vector4( 100f, -100f, -100f), // 1
    Vector4( 100f,  100f, -100f), // 2
    Vector4(-100f,  100f, -100f), // 3
    Vector4(-100f, -100f,  100f), // 4
    Vector4( 100f, -100f,  100f), // 5
    Vector4( 100f,  100f,  100f), // 6
    Vector4(-100f,  100f,  100f)  // 7
)
```

Each coordinate uses (pm100), so the untransformed cube is 200 units across in each axis. Vertices 0–3 form one square at (z=-100); vertices 4–7 form the opposite square at (z=+100). The array index is the key used by each face to refer to a shared corner.

## 11. Representing the six faces

```kotlin
data class CubeFace(val indices: IntArray, val color: Int)
```

Each face stores four vertex indices and a color. For example:

```kotlin
CubeFace(
    intArrayOf(4, 5, 6, 7),
    Color.argb(180, 229, 57, 53)
) // Front (Red)
```

The six faces use red, blue, green, yellow, purple, and orange. Alpha 180 makes the fill partially transparent. Face indices let the drawing loop reuse the transformed vertex positions instead of storing a separate set of points for every polygon.

**Why this design works:** vertices define geometry once; faces define connectivity and surface styling separately.

---

## 12. Transformation state

```kotlin
var translateX = 0f
var translateY = 0f
var translateZ = 0f

var rotX = 25f
var rotY = 45f
var rotZ = 0f

var cubeScaleX = 1.0f
var cubeScaleY = 1.0f
var cubeScaleZ = 1.0f

var perspectiveW = 1.0f

private val matrix4x4 = FloatArray(16)
var onMatrix4UpdatedListener: ((m: FloatArray) -> Unit)? = null
```

The view keeps transformation parameters separate from the original vertices. That lets the same source cube be redrawn after every touch movement or slider change.

- `rotX`, `rotY`, `rotZ`: rotation angles in degrees.
- `translateX/Y/Z`: translation terms in the 4×4 transform; the original UI primarily exposes rotation and the W-division control.
- `cubeScaleX/Y/Z`: per-output-row multipliers in the matrix builder.
- `perspectiveW`: affects the view's current homogeneous-division expression, described carefully in section 16 below.

---

## 13. Building the combined 4×4 matrix

### Convert degrees to radians

```kotlin
val radX = Math.toRadians(rotX.toDouble())
val radY = Math.toRadians(rotY.toDouble())
val radZ = Math.toRadians(rotZ.toDouble())

val cx = cos(radX).toFloat()
val sx = sin(radX).toFloat()
val cy = cos(radY).toFloat()
val sy = sin(radY).toFloat()
val cz = cos(radZ).toFloat()
val sz = sin(radZ).toFloat()
```

Kotlin's trigonometric functions take radians. The UI exposes degrees, so the code converts degrees into radians before calculating the sine and cosine values. The short variable names pair cosine/sine by axis: `cx/sx`, `cy/sy`, `cz/sz`.

### Fill the matrix array

```kotlin
// Rotation composition identified in the code comments as Rz * Ry * Rx
out[0]  = (cy * cz) * cubeScaleX
out[1]  = (cz * sx * sy - cx * sz) * cubeScaleX
out[2]  = (cx * cz * sy + sx * sz) * cubeScaleX
out[3]  = translateX

out[4]  = (cy * sz) * cubeScaleY
out[5]  = (cx * cz + sx * sy * sz) * cubeScaleY
out[6]  = (-cz * sx + cx * sy * sz) * cubeScaleY
out[7]  = translateY

out[8]  = (-sy) * cubeScaleZ
out[9]  = (cy * sx) * cubeScaleZ
out[10] = (cx * cy) * cubeScaleZ
out[11] = translateZ

out[12] = 0f
out[13] = 0f
out[14] = 0f
out[15] = 1.0f
```

The first three rows contain rotation-derived coefficients, with a scale value multiplying each row; translation occupies the fourth element of those rows. The bottom row remains ([0,0,0,1]), the usual homogeneous affine form.

**Implementation detail worth noticing:** the code multiplies `cubeScaleX` across row 0, `cubeScaleY` across row 1, and `cubeScaleZ` across row 2. That's how scaling is currently implemented in this matrix builder. When learning matrix order or changing scale semantics, do not assume it is a generic textbook matrix multiplication pipeline; inspect the actual array coefficients.

The builder is not allocating a new array each frame. `matrix4x4` is reused and filled by `build4x4Matrix()`.

---

## 14. Transform every vertex

```kotlin
build4x4Matrix(matrix4x4)
onMatrix4UpdatedListener?.invoke(matrix4x4.clone())

val transformedVertices = Array(cubeVertices.size) { i ->
    cubeVertices[i].transform(matrix4x4)
}
```

This code follows a simple pipeline:

1. Update the matrix from the current rotation/scale/translation state.
2. Send a clone of the matrix to the Activity so it can display the coefficients.
3. Transform every one of the eight original vertices.
4. Use the transformed results for face depth sorting and drawing.

The `.clone()` prevents the receiver from holding the same mutable array that the View will overwrite during the next draw pass.

---

## 15. Map transformed 3D vertices onto the 2D screen

```kotlin
val cx = width / 2f
val cy = height / 2f

val screenPoints = Array(transformedVertices.size) { i ->
    val v = transformedVertices[i]
    val wDiv = if (v.w != 0f) v.w / perspectiveW else 1.0f
    val sx = cx + (v.x / wDiv)
    val sy = cy + (v.y / wDiv)
    Pair(sx, sy)
}
```

The center of the custom View is the screen origin for the cube. X is added to `cx`; Y is added to `cy`. The current rendering drops Z from the screen coordinate and uses it separately to order faces.

### Important accuracy note about the current “perspective W” control

The comments describe this expression as homogeneous W division, but the current matrix builder always sets the bottom row to ([0,0,0,1]), and every vertex defaults to (w=1). Therefore, for this matrix, transformed (v.w) normally remains 1. The code computes:

```text
wDiv = 1 / perspectiveW
screenX = cx + x / (1 / perspectiveW)
        = cx + x × perspectiveW

screenY = cy + y × perspectiveW
```

So the slider currently behaves as a **uniform scale factor on the screen-space X/Y offsets**, not a camera-perspective projection in which W varies with depth. This observation describes the uploaded implementation as it is; the code has not been silently rewritten to claim a different projection model.

---

## 16. Painter's Algorithm: sorting faces by average Z

```kotlin
val sortedFaces = cubeFaces.map { face ->
    val avgZ = face.indices
        .map { idx -> transformedVertices[idx].z }
        .average()
        .toFloat()
    Pair(face, avgZ)
}.sortedBy { it.second } // Far to near
```

The code retrieves the transformed Z value of each corner in a face, averages the four values, and sorts faces using that average depth. It then draws the ordered faces.

This is a simple **Painter's Algorithm** approach: render one face, then let a nearer face paint over it.

### Limitations

- It sorts each whole face by one average Z value, not each pixel.
- It does not perform back-face culling.
- For complex orientations, intersecting or partially overlapping surfaces can be ordered incorrectly.
- Because the fill is partially transparent, later faces can blend over earlier faces.

That is appropriate for this educational cube. It is not equivalent to a hardware depth buffer.

---

## 17. Construct each face Path and draw it

```kotlin
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
```

- `reset()` empties the reused face path before constructing a new face.
- `moveTo()` establishes the first corner.
- `lineTo()` connects the remaining corners in the given order.
- `close()` connects the last corner back to the first.
- The filled face is drawn first; the black wireframe is drawn afterward.

The separate last pass adds pink points at every projected corner:

```kotlin
for (pt in screenPoints) {
    canvas.drawCircle(pt.first, pt.second, 8f, vertexPaint)
}
```

Those vertex markers make the eight corner positions easier to see.

---

## 18. Dragging to rotate the cube

```kotlin
MotionEvent.ACTION_DOWN -> {
    performClick()
    lastTouchX = event.x
    lastTouchY = event.y
    stopAutoSpin()
    return true
}

MotionEvent.ACTION_MOVE -> {
    val dx = event.x - lastTouchX
    val dy = event.y - lastTouchY

    rotY = (rotY + dx * 0.5f) % 360f
    if (rotY < 0) rotY += 360f

    rotX = (rotX - dy * 0.5f) % 360f
    if (rotX < 0) rotX += 360f

    lastTouchX = event.x
    lastTouchY = event.y

    invalidate()
    return true
}
```

Horizontal movement changes `rotY`; vertical movement changes `rotX`. Multiplying by 0.5 makes a two-pixel drag change the corresponding angle by about one degree.

The modulo operation wraps large angles to a range of one full turn; the extra negative check makes the stored angle non-negative. Finally, updating the previous touch coordinates prevents the same movement delta from being applied repeatedly.

---

## 19. Automatic clockwise / counter-clockwise spin

```kotlin
private var autoSpinDirection = 0 // 0: off, 1: CW, -1: CCW

private val spinRunnable = object : Runnable {
    override fun run() {
        if (autoSpinDirection != 0) {
            rotY = (rotY + autoSpinDirection * 2f) % 360f
            if (rotY < 0) rotY += 360f
            invalidate()
            postDelayed(this, 16)
        }
    }
}
```

The runnable changes Y rotation by two degrees per scheduled frame, requests a redraw, and schedules itself again after 16 ms. This is a nominal animation loop; the actual frame timing depends on the Android message queue and system load.

The start/stop API is:

```kotlin
fun setAutoSpin(direction: Int) {
    autoSpinDirection = direction
    removeCallbacks(spinRunnable)
    if (autoSpinDirection != 0) {
        post(spinRunnable)
    }
}

fun stopAutoSpin() {
    autoSpinDirection = 0
    removeCallbacks(spinRunnable)
}
```

Removing the previous callback before posting avoids scheduling multiple copies of the same runnable when switching direction. Touch-down stops the auto-spin before manual rotation begins.

**Lifecycle note:** the current code stops auto-spin when the user touches the view or presses Pause/Reset. It does not override a View lifecycle callback to stop the runnable automatically when the view is detached; that would be a worthwhile improvement in a production implementation.

---

## 20. Resetting the cube state

```kotlin
fun resetCube() {
    stopAutoSpin()
    translateX = 0f
    translateY = 0f
    translateZ = 0f
    rotX = 25f
    rotY = 45f
    rotZ = 0f
    cubeScaleX = 1.0f
    cubeScaleY = 1.0f
    cubeScaleZ = 1.0f
    perspectiveW = 1.0f
    invalidate()
}
```

The reset operation returns the adjustable state to the initial values used by the demo, stops the animation loop, and requests redraw. The Activity additionally resets slider progress so the visual controls match the reset properties.

---

## 21. Live 4×4 matrix display

```kotlin
var onMatrix4UpdatedListener: ((m: FloatArray) -> Unit)? = null
```

During drawing:

```kotlin
build4x4Matrix(matrix4x4)
onMatrix4UpdatedListener?.invoke(matrix4x4.clone())
```

The Activity formats the 16 elements as four rows:

```kotlin
private fun updateMatrix4TextDisplay(m: FloatArray) {
    val format =
        "[ %5.2f %5.2f %5.2f %5.2f ]\n" +
        "[ %5.2f %5.2f %5.2f %5.2f ]\n" +
        "[ %5.2f %5.2f %5.2f %5.2f ]\n" +
        "[ %5.2f %5.2f %5.2f %5.2f ]"

    tvMatrix4Display.text = String.format(
        Locale.US, format,
        m[0], m[1], m[2], m[3],
        m[4], m[5], m[6], m[7],
        m[8], m[9], m[10], m[11],
        m[12], m[13], m[14], m[15]
    )
}
```

The matrix view uses a monospace font because aligned columns are easier to compare. Two decimal places are used for readability. The matrix display is a teaching aid: users can change an angle and see how the coefficients respond.

---

# Part III — UI and state flow

## 22. The graph controls

The graph layout contains a `RadioGroup` with these IDs:

- `rbGraphAll`
- `rbGraphArc`
- `rbGraphRect`
- `rbGraphPath`

It also has these SeekBars:

- `sbArcSweep`: progress 0–360, mapped directly to `arcSweepAngle`.
- `sbBarHeight`: progress 0–150, divided by 100 to become a 0.00–1.50 multiplier.
- `sbPathCurviness`: progress 0–150, divided by 100 to become a 0.00–1.50 multiplier.

The radio selection updates `currentGraphType`. The SeekBars update a graph property. Each handler calls `canvasGraphsView.invalidate()` to redraw.

## 23. The cube controls

The cube layout provides:

- **Spin CW** → `setAutoSpin(1)`
- **Spin CCW** → `setAutoSpin(-1)`
- **Pause** → `stopAutoSpin()`
- **Reset** → `resetCube()` and reset slider positions
- **Rotation X** → update `rotX` in degrees
- **Rotation Y** → update `rotY` in degrees
- **Rotation Z** → update `rotZ` in degrees
- **Perspective W Division** → progress is clamped to at least 10 and divided by 100.0 before being stored as `perspectiveW`

The implementation uses a shared helper for slider listeners in `MainActivity`; it invokes the supplied callback only when `fromUser` is true.

## 24. Main navigation integration

The uploaded application uses a navigation drawer and a FrameLayout containing included layouts. The new IDs are:

```xml
<include
    android:id="@+id/layoutCanvasGraphs"
    layout="@layout/fragment_canvas_graphs"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone" />

<include
    android:id="@+id/layoutCube3D"
    layout="@layout/fragment_cube_3d"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone" />
```

The navigation menu gains:

```xml
<item
    android:id="@+id/nav_canvas_graphs"
    android:title="2D Canvas Graphs (Arc, Rect, Path)" />

<item
    android:id="@+id/nav_cube_3d"
    android:title="3D Homogeneous Cube &amp; Matrix" />
```

The exact feature-specific Activity wiring is documented in [MainActivity-integration.md](MainActivity-integration.md).

---

# Part IV — Kotlin, data structures, algorithms, and mathematics

## 25. Coding fundamentals demonstrated

| Fundamental | Where it appears | Why it matters |
|---|---|---|
| Custom View | `CanvasGraphsView`, `Cube3DView` | Encapsulates rendering and interaction |
| State-driven UI | `currentGraphType`, rotation properties | Current values determine the rendered result |
| Enums | `GraphType` | Restricts graph mode to a finite set |
| Data classes | `Vector4`, `CubeFace` | Gives structured meaning to vectors and face definitions |
| Reusable Paint objects | Both Views | Separates colors/strokes/text style from geometry |
| `Path` and `RectF` | Line chart, faces, arc/bar drawing | Store drawing geometry |
| Lambdas/callbacks | Matrix callback, slider listeners | Connect custom drawing Views to the Activity |
| Modulo arithmetic | Bar palette, rotation angles | Cycles through colors / wraps angles |
| Loop traversal | Vertices, faces, graph samples | Builds and renders repeated geometry |
| `Pair` | Screen points / demo samples | Groups two related values |
| `FloatArray` | 4×4 matrix | Compact indexed representation for numeric coefficients |
| `invalidate()` | Control handlers / touch input | Requests the next drawing pass |
| Reused mutable objects | `Path`, `Paint`, `RectF` | Avoids creating all geometry helpers on every frame |

## 26. DSA fundamentals actually present

This example does **not** implement graph algorithms such as BFS/DFS, nor does it implement a general-purpose charting algorithm. The applicable fundamentals are:

- **Arrays:** fixed cube vertices, six cube faces, palette colors, and matrix coefficients.
- **Indexed traversal:** loop over the vertex array and face indices.
- **Sequential transformation:** each vertex is passed through the same matrix.
- **Mapping:** each face is mapped to its average depth with `map { ... }`.
- **Sorting:** `sortedBy { it.second }` arranges faces by average Z depth.
- **Dynamic/derived geometry:** paths are built by iterating through vertices or sample points.
- **Fixed-size numeric representation:** 16 floats represent a 4×4 matrix.

A particularly important line is:

```kotlin
}.sortedBy { it.second }
```

This makes painter-order rendering possible. It is a basic sorting use case, not a hidden-surface algorithm.

## 27. Mathematics recap

### Degrees to radians

[
\text{radians}=\text{degrees}\times\frac{\pi}{180}
]

### Rotation coefficients

For each axis, the code uses sine and cosine. The combined rotation formulas are encoded into the first three rows of the 4×4 array.

### Homogeneous matrix multiplication

[
\begin{bmatrix}
x'\\y'\\z'\\w'
\end{bmatrix}
=
\begin{bmatrix}
m_0&m_1&m_2&m_3\\
m_4&m_5&m_6&m_7\\
m_8&m_9&m_{10}&m_{11}\\
m_{12}&m_{13}&m_{14}&m_{15}
\end{bmatrix}
\begin{bmatrix}
x\\y\\z\\w
\end{bmatrix}
]

### Screen coordinates

[
x_{screen}=c_x+\frac{x'}{w_{div}},\qquad
y_{screen}=c_y+\frac{y'}{w_{div}}
]

That equation describes the code's screen mapping. The implementation-specific meaning of `wDiv` is important: with its present matrix, the slider scales screen offsets rather than creating a depth-dependent camera perspective.

### Chart bar height

[
h_{bar}=h_{chart}\times value\times heightScale
]

### Normalized chart samples

[
x_{screen}=x_{origin}+x_{fraction}\times width
]

[
y_{screen}=y_{baseline}-y_{fraction}\times height\times pathCurviness
]

---

# Part V — Tests and implementation limitations

## 28. What's currently tested?

`Homogeneous3DMatrixTest.kt` checks two small calculations:

1. Dividing an example homogeneous vector ((200,-100,50,2)) by W produces ((100,-50,25)).
2. A manually constructed 4×4 translation matrix puts translation values in indices 3, 7, and 11 and has 1 at index 15.

These tests validate arithmetic examples and matrix-index conventions. They do **not** instantiate the Android View or assert the rendered pixels.

## 29. Important limitations to understand rather than overlook

1. Graph values are hard-coded demo data, not data from a file, API, or live model.
2. The five arc segments are equal slices of the configured sweep, not proportional to separate values.
3. Bar heights and line points can exceed their nominal chart area when scales exceed 1.0; clipping/clamping is not implemented.
4. The smooth line uses hand-chosen cubic Bézier control points, not a general spline-fitting algorithm.
5. The 3D cube uses average-depth face sorting. It has no depth buffer or back-face culling.
6. The current W slider changes the screen-space multiplier because the built matrix keeps W constant; it is not a full perspective camera.
7. The auto-spin runnable should ideally be stopped when the View is detached or the screen is no longer active.
8. The test class does not test touch gestures, animation timing, or Canvas output.

These observations are useful learning opportunities; the feature code is preserved as the uploaded implementation, not silently redesigned.

---

# Part VI — Suggested study plan

1. Open `CanvasGraphsView.kt` and follow `onDraw()` first.
2. Change `arcSweepAngle` manually and see how `sliceAngle` is calculated.
3. Derive the five bar coordinates with paper and pencil.
4. Read the normalized line samples, then trace how they turn into screen coordinates.
5. Inspect the two Paths that represent the curve and filled area.
6. Open `Cube3DView.kt` and write the vector multiplication out by hand for one vertex.
7. Draw the matrix as four rows and label each index.
8. Change one rotation angle at a time and watch the matrix display.
9. Follow the face index arrays to determine which four corners make a surface.
10. Read `Homogeneous3DMatrixTest.kt` and connect each assertion to the array positions.
11. Finally, inspect [MainActivity-integration.md](MainActivity-integration.md) to understand how the controls update state and trigger redraws.

---

**Learning focus:** tie each Canvas API call back to geometry, each matrix coefficient back to an equation, and each UI control back to the state that changes the rendered frame.
