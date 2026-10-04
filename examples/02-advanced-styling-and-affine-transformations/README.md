# 02. Advanced Fill Styling & Affine Matrix Transformations

This folder contains the **next layer of the polygon experiment**: advanced paint/shader styling and 2D affine transformations.

The important rule for this repository is:

> The root README is only a tracker. The detailed explanation, exact snippets, mathematics, notes, and implementation observations live here.

## Features in this example

### Advanced styling

- Fill only
- Stroke only
- Fill + Stroke
- Solid-color fill
- Linear gradient
- Radial gradient
- Sweep / angular gradient
- Repeating texture fills
- Checkerboard texture
- Diagonal hatch texture
- Polka-dot texture
- Brick-wall texture
- Adjustable stroke width
- Shader selection through RadioGroups
- Texture selection that appears only when texture mode is active

### Affine transformations

- Translation
- Rotation
- Scaling
- Shearing / skewing
- Transformation pivot based on polygon bounds
- Reference path showing the original polygon
- Grid for visual orientation
- Live 3×3 matrix display
- Reset-to-identity behavior
- SeekBar-driven interactive transformations

---

# A. Advanced Styling

## 1. Fill mode as an enum

The code avoids scattered booleans by representing the three rendering modes explicitly:

```kotlin
enum class FillMode { FILL, STROKE, FILL_AND_STROKE }
```

Current mode is stored as state:

```kotlin
var currentFillMode = FillMode.FILL_AND_STROKE
```

### Why this is important

This is a small but useful state-modeling technique:

```
state = one of { FILL, STROKE, FILL_AND_STROKE }
```

It is clearer than keeping multiple flags such as `isFill`, `isStroke`, and manually preventing invalid combinations.

---

## 2. Rendering according to the selected fill mode

The core branching logic is:

```kotlin
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
```

### Important concept

The same geometry is reused.

Only the **rendering operation** changes:

```
same Path
   ├── fill
   ├── stroke
   └── fill + stroke
```

That is an important graphics-programming idea: separate geometry from presentation.

---

# B. Shader Architecture

## 3. Representing shader modes

The code defines:

```kotlin
enum class ShaderType {
    SOLID,
    LINEAR_GRADIENT,
    RADIAL_GRADIENT,
    SWEEP_GRADIENT,
    TEXTURE_PATTERN
}
```

Current selection:

```kotlin
var currentShaderType = ShaderType.LINEAR_GRADIENT
```

This creates a second independent piece of state:

```
FillMode
ShaderType
```

That allows combinations such as:

```
Fill + Linear Gradient
Fill + Texture
Stroke only + any selected shader
```

The shader primarily affects `fillPaint`; stroke appearance is controlled separately.

---

## 4. Computing polygon bounds before creating a shader

The code first calculates the polygon's bounding rectangle:

```kotlin
private val polygonBounds = RectF()

polygonPath.computeBounds(polygonBounds, true)
applyShaderToFillPaint()
```

This is important because the gradients need coordinates relative to the polygon's actual geometry.

The implementation derives:

```kotlin
val width = max(10f, polygonBounds.width())
val height = max(10f, polygonBounds.height())
val radius = max(width, height) / 2f
val cx = polygonBounds.centerX()
val cy = polygonBounds.centerY()
```

So the bounding box becomes the coordinate frame for the visual effect.

---

# C. Solid Color

## 5. Resetting a previous shader

The solid-color branch is:

```kotlin
ShaderType.SOLID -> {
    fillPaint.shader = null
    fillPaint.color = primaryColor
}
```

The important detail is:

```kotlin
fillPaint.shader = null
```

A shader can remain attached to a `Paint`. Clearing it is therefore part of switching back to a normal solid fill.

---

# D. Linear Gradient

## 6. Exact linear-gradient implementation

```kotlin
fillPaint.shader = LinearGradient(
    polygonBounds.left,
    polygonBounds.top,
    polygonBounds.right,
    polygonBounds.bottom,
    intArrayOf(primaryColor, secondaryColor, Color.YELLOW),
    null,
    Shader.TileMode.CLAMP
)
```

### Geometry

The gradient travels from:

```
(left, top)
     ↓
(right, bottom)
```

So the shader is tied to the polygon's bounding rectangle.

### Important API

```kotlin
LinearGradient(
    x0, y0,
    x1, y1,
    colors,
    positions,
    tileMode
)
```

The implementation uses three colors:

```
primaryColor → secondaryColor → YELLOW
```

and `CLAMP` so the edge colors continue beyond the gradient endpoints rather than repeating.

---

# E. Radial Gradient

## 7. Exact radial-gradient implementation

```kotlin
fillPaint.shader = RadialGradient(
    cx,
    cy,
    radius,
    intArrayOf(primaryColor, secondaryColor, Color.WHITE),
    null,
    Shader.TileMode.CLAMP
)
```

The center is:

```kotlin
cx = polygonBounds.centerX()
cy = polygonBounds.centerY()
```

and the radius is:

```kotlin
radius = max(width, height) / 2
```

### Mental model

```
              outer color
            ↗           ↖
         color           color
            ↘           ↙
             center
```

The effect radiates outward from the calculated center.

---

# F. Sweep Gradient

## 8. Exact sweep-gradient implementation

```kotlin
fillPaint.shader = SweepGradient(
    cx,
    cy,
    intArrayOf(primaryColor, secondaryColor, Color.GREEN, primaryColor),
    null
)
```

Unlike the linear and radial approaches, the sweep gradient changes color around the center angle.

The code intentionally repeats `primaryColor` at the end so the color sequence cycles back toward its starting color.

---

# G. Texture Patterns

## 9. Texture mode

The shader enum includes:

```kotlin
ShaderType.TEXTURE_PATTERN
```

When selected:

```kotlin
val patternBmp = generatePatternBitmap(currentTexturePattern)

fillPaint.shader = BitmapShader(
    patternBmp,
    Shader.TileMode.REPEAT,
    Shader.TileMode.REPEAT
)
```

This is one of the most important snippets in the new code.

### What happens

```
Pattern enum
    ↓
generatePatternBitmap()
    ↓
64 × 64 Bitmap
    ↓
BitmapShader
    ↓
REPEAT / REPEAT
    ↓
Fill polygon with repeating texture
```

---

## 10. Creating a procedural texture bitmap

The method begins with:

```kotlin
val size = 64

val bitmap = Bitmap.createBitmap(
    size,
    size,
    Bitmap.Config.ARGB_8888
)

val canvas = Canvas(bitmap)
val p = Paint(Paint.ANTI_ALIAS_FLAG)
```

This is an important graphics technique:

> Create a tiny image procedurally, then use it as a repeating texture.

The generated bitmap becomes the texture tile.

---

# H. Checkerboard Texture

## 11. Checkerboard implementation

```kotlin
TexturePattern.CHECKERBOARD -> {
    canvas.drawColor(primaryColor)
    p.color = secondaryColor

    canvas.drawRect(0f, 0f, 32f, 32f, p)
    canvas.drawRect(32f, 32f, 64f, 64f, p)
}
```

The base tile is 64×64.

The second color is placed in two diagonal 32×32 squares.

When repeated by `BitmapShader`, those two squares create a checker pattern.

---

# I. Diagonal Hatch

## 12. Hatch implementation

```kotlin
TexturePattern.DIAGONAL_HATCH -> {
    canvas.drawColor(primaryColor)
    p.color = secondaryColor
    p.strokeWidth = 8f
    p.style = Paint.Style.STROKE

    for (i in -64..128 step 16) {
        canvas.drawLine(
            i.toFloat(),
            0f,
            (i + 64).toFloat(),
            64f,
            p
        )
    }
}
```

### Coding fundamentals used

This small loop combines several fundamental ideas:

- integer range
- configurable step size
- repeated primitive drawing
- coordinate generation from the loop variable

The loop generates many parallel diagonal line segments.

---

# J. Polka Dots

## 13. Polka-dot implementation

```kotlin
TexturePattern.POLKA_DOTS -> {
    canvas.drawColor(primaryColor)
    p.color = secondaryColor

    canvas.drawCircle(16f, 16f, 10f, p)
    canvas.drawCircle(48f, 48f, 10f, p)
}
```

The small number of circles becomes a repeating pattern because the resulting bitmap is tiled.

---

# K. Brick Wall

## 14. Brick pattern implementation

```kotlin
TexturePattern.BRICK_WALL -> {
    canvas.drawColor(primaryColor)
    p.color = secondaryColor
    p.strokeWidth = 4f
    p.style = Paint.Style.STROKE

    canvas.drawLine(0f, 16f, 64f, 16f, p)
    canvas.drawLine(0f, 32f, 64f, 32f, p)
    canvas.drawLine(0f, 48f, 64f, 48f, p)

    canvas.drawLine(16f, 0f, 16f, 16f, p)
    canvas.drawLine(48f, 16f, 48f, 32f, p)
    canvas.drawLine(16f, 32f, 16f, 48f, p)
    canvas.drawLine(48f, 48f, 48f, 64f, p)
}
```

The vertical lines alternate their positions between rows. That creates the staggered-brick effect.

---

# L. Adjustable Stroke Width

## 15. SeekBar drives graphics state

The Activity listens to the stroke-width slider:

```kotlin
sbStrokeWidth.setOnSeekBarChangeListener(
    object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: SeekBar?,
            progress: Int,
            fromUser: Boolean
        ) {
            val width = progress.coerceAtLeast(1)
            advancedStylingView.strokeWidthPx = width.toFloat()
            advancedStylingView.invalidate()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
)
```

Then `onDraw()` copies that state into the actual Paint:

```kotlin
strokePaint.strokeWidth = strokeWidthPx
```

### Pattern to remember

```
UI control
   ↓
update state
   ↓
invalidate()
   ↓
onDraw()
   ↓
Paint uses new state
```

This is a very common Android custom-View pattern.

---

# M. Conditional UI for Texture Options

The shader selection listener makes texture controls visible only when needed:

```kotlin
R.id.rbTexturePattern -> {
    advancedStylingView.currentShaderType =
        AdvancedStylingView.ShaderType.TEXTURE_PATTERN

    layoutTextureOptions.visibility = View.VISIBLE
}
```

For other shader modes:

```kotlin
layoutTextureOptions.visibility = View.GONE
```

This is a simple but useful dependent-UI state pattern:

```
selected mode = TEXTURE
        ↓
show texture controls

selected mode ≠ TEXTURE
        ↓
hide texture controls
```

---

# N. Affine Transformations

The second major feature is a direct implementation of 2D affine matrix transformations.

The code stores:

```kotlin
var translateX = 0f
var translateY = 0f
var rotationDegrees = 0f
var affineScaleX = 1.0f
var affineScaleY = 1.0f
var skewX = 0f
var skewY = 0f
```

So the transformation state is explicitly separated into:

- translation
- rotation
- scale
- shear

---

# O. Android Matrix

## 16. Matrix object

The implementation creates:

```kotlin
private val affineMatrix = Matrix()
private val matrixValues = FloatArray(9)
```

The `FloatArray(9)` corresponds to the 3×3 homogeneous-coordinate matrix representation used by the Android `Matrix` API.

Conceptually:

```
[ a  b  tx ]
[ c  d  ty ]
[ p  q  r  ]
```

For standard 2D affine transforms, the last row is normally associated with the homogeneous-coordinate representation.

---

# P. Computing the transformation pivot

The polygon's bounds are calculated:

```kotlin
polygonPath.computeBounds(polygonBounds, true)

val cx = polygonBounds.centerX()
val cy = polygonBounds.centerY()
```

The code therefore uses the polygon bounding-box center as the transformation reference point.

This matters because rotation and scaling around the default origin would produce a very different visual result.

---

# Q. Translation

## 17. Translation code

```kotlin
affineMatrix.reset()

affineMatrix.postTranslate(
    translateX,
    translateY
)
```

The conceptual equations from the course notes are:

```
x' = x + a
y' = y + b
```

So every vertex receives the same displacement.

---

# R. Rotation

## 18. Rotation code

```kotlin
affineMatrix.postRotate(
    rotationDegrees,
    cx + translateX,
    cy + translateY
)
```

The pivot explicitly includes the translation already applied by the first operation.

The notes represent rotation using:

```
x' = cos(θ)x - sin(θ)y
y' = sin(θ)x + cos(θ)y
```

The implementation delegates the actual matrix construction to Android's `Matrix.postRotate()`.

---

# S. Scaling

## 19. Scale code

```kotlin
affineMatrix.postScale(
    affineScaleX,
    affineScaleY,
    cx + translateX,
    cy + translateY
)
```

The notes describe scaling as:

```
x' = c x
y' = d y
```

The implementation permits independent X and Y scale values.

For example:

```
affineScaleX = 2.0
affineScaleY = 0.5
```

would stretch the polygon horizontally while compressing it vertically.

---

# T. Shearing / Skew

## 20. Skew code

```kotlin
affineMatrix.postSkew(
    skewX,
    skewY,
    cx + translateX,
    cy + translateY
)
```

The course notes express the shear idea as:

```
x' = x + e y
y' = f x + y
```

The current code exposes both `skewX` and `skewY`.

---

# U. Transformation Order

The implementation deliberately applies transformations in this order:

```kotlin
affineMatrix.reset()
affineMatrix.postTranslate(...)
affineMatrix.postRotate(...)
affineMatrix.postScale(...)
affineMatrix.postSkew(...)
```

This is an important mathematical and programming concept:

> Matrix transformations are compositional, and the sequence of operations matters.

The code is therefore not merely storing four independent values. It is constructing one combined transformation.

---

# V. Applying the Matrix to the Polygon

The transformation is applied using a separate path:

```kotlin
transformedPath.set(polygonPath)
transformedPath.transform(affineMatrix)
```

Then the transformed path is rendered:

```kotlin
canvas.drawPath(transformedPath, fillPaint)
canvas.drawPath(transformedPath, strokePaint)
```

### Why use a second Path?

Because the original polygon geometry is preserved.

```
polygonPath
   │
   ├── original geometry
   │
   └── transformed copy
             ↓
      transformedPath
```

That makes it possible to draw the original as a reference and render the transformed result separately.

---

# W. Showing the Original Polygon

Before transforming, the app draws a faint reference path:

```kotlin
canvas.drawPath(
    polygonPath,
    referencePathPaint
)
```

The paint uses a dashed line:

```kotlin
private val referencePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 4f
    color = Color.LTGRAY
    pathEffect = DashPathEffect(
        floatArrayOf(10f, 10f),
        0f
    )
}
```

This provides immediate visual feedback about:

```
original position
        vs
transformed position
```

---

# X. Drawing a Coordinate Grid

The transformation view also draws an orientation grid.

```kotlin
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
```

### Coding fundamentals here

This demonstrates:

- while-loop traversal
- incremental coordinates
- drawing repeated primitives
- deriving rendering from the View dimensions

It is deliberately simple and makes the geometric transformation easier to see.

---

# Y. Live Matrix Inspection

The implementation exposes the actual matrix coefficients:

```kotlin
affineMatrix.getValues(matrixValues)
onMatrixUpdatedListener?.invoke(matrixValues)
```

The Activity then formats the nine values:

```kotlin
val format =
    "[ %6.2f  %6.2f  %6.2f ]\n" +
    "[ %6.2f  %6.2f  %6.2f ]\n" +
    "[ %6.2f  %6.2f  %6.2f ]"
```

and displays them as a 3×3 matrix.

This is excellent for learning because the visual transformation and the numerical matrix can be observed at the same time.

---

# Z. Mapping SeekBars to Transformation Values

## Translation

The slider range is 0..600, centered at 300:

```kotlin
val tx = (progress - 300).toFloat()
```

Therefore:

```
progress = 300 → 0 px
progress = 600 → +300 px
progress = 0   → -300 px
```

## Rotation

```kotlin
affineTransformView.rotationDegrees = progress.toFloat()
```

with a 0..360 range.

## Scale

```kotlin
val sx = progress.coerceAtLeast(10) / 100f
```

So the slider maps approximately to:

```
0.10x ... 3.00x
```

## Shear

```kotlin
val kx = (progress - 150) / 100f
```

which maps the middle point:

```
progress = 150 → 0.0
```

and gives an approximately symmetric range around zero.

This is a good example of converting a UI-friendly integer control into a useful floating-point mathematical parameter.

---

# AA. Resetting to the Identity Transformation

The reset method:

```kotlin
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
```

The identity state is:

```
Translation = (0, 0)
Rotation    = 0°
Scale       = (1, 1)
Shear       = (0, 0)
```

and the matrix is reset to its identity representation.

---

# AB. Important Programming Fundamentals

## State

The custom Views maintain explicit mutable state:

```
points
isClosed
currentFillMode
currentShaderType
currentTexturePattern
strokeWidthPx

translateX
translateY
rotationDegrees
affineScaleX
affineScaleY
skewX
skewY
```

The screen is effectively a function of that state:

```
state → onDraw() → pixels
```

## Encapsulation

The actual rendering state lives inside the custom Views instead of being scattered throughout the Activity.

## Callbacks

The Views notify the Activity through listener properties:

```kotlin
var onPolygonStateChangedListener: ((isClosed: Boolean, pointCount: Int) -> Unit)? = null

var onMatrixUpdatedListener: ((matrixValues: FloatArray) -> Unit)? = null
```

This is a lightweight observer/callback pattern.

## Enums

Enums constrain the UI state to a finite set of valid options.

## Lists

Polygon vertices are stored in:

```kotlin
mutableListOf<PointF>()
```

## Traversal

The polygon is rebuilt with a sequential loop:

```kotlin
for (i in 1 until points.size) {
    polygonPath.lineTo(points[i].x, points[i].y)
}
```

## Hit testing

The same Euclidean-distance idea from the first example is reused to detect taps near the first vertex.

---

# AC. DSA Fundamentals Present in This Code

There are no advanced graph/tree algorithms in this example. The useful DSA fundamentals are small but real:

### Dynamic array / list

```kotlin
mutableListOf<PointF>()
```

Used for a variable number of polygon vertices.

### Indexed traversal

```for (i in 1 until points.size)
```

### Accessing endpoints

```kotlin
points.first()
points.last()
```

### Removal from a dynamic list

```kotlin
points.removeAt(points.size - 1)
```

### Simple numeric aggregation

```max(width, height)
```

### Repeated traversal for rendering

Vertices are visited one by one to rebuild and draw the geometry.

The main learning point is that graphics programs still rely on ordinary data-structure operations underneath the rendering APIs.

---

# AD. Mathematical Fundamentals

This example is especially useful because the coding directly maps onto geometry.

## Euclidean distance

Used for closing-point detection:

```
d = √((x-x₀)² + (y-y₀)²)
```

## Bounding rectangle

The polygon is reduced to:

```
left
top
right
bottom
width
height
centerX
centerY
```

These values drive shader geometry and the transformation pivot.

## Affine transformation matrix

The course notes represent the transformation family using a 3×3 matrix.

The implementation uses Android's `Matrix` class rather than manually multiplying every vertex.

## Composition

Translation, rotation, scaling, and shear are combined into one matrix through ordered `post...` operations.

---

# AE. What the Course Notes Add

The uploaded notes connect the implementation to the underlying graphics concepts:

### Page 1

The notes introduce drawing lines by moving to a starting coordinate and repeatedly adding line segments. They also connect the drawing operation to stored vertex coordinates. fileciteturn39file0L2-L6

### Page 2

The notes explicitly highlight closing the polygon with `Path.close()` and then using separate fill and stroke Paint styles to color and outline the polygon. fileciteturn39file0L8-L10

### Page 3

The notes classify the major paint modes as **Stroke**, **Fill**, **Stroke & Fill**, **Gradient fill**, and **Texture fill**. fileciteturn39file0L12-L14

### Pages 4–6

The notes move from gradient-fill geometry into affine transformations: translation, rotation, scaling, shear, and representation through matrix operations. fileciteturn39file0L16-L26

The notes therefore provide the theory behind the two major coding additions in this folder:

```
Paint / Shader styling
        +
Affine geometry transformations
```

The original uploaded PDF is retained as the source study material for this section where the repository can store it; the handwritten annotations should be treated as your own learning notes.

---

# AF. Feature Map

```
AdvancedStylingView
│
├── FillMode
│   ├── FILL
│   ├── STROKE
│   └── FILL_AND_STROKE
│
├── ShaderType
│   ├── SOLID
│   ├── LINEAR_GRADIENT
│   ├── RADIAL_GRADIENT
│   ├── SWEEP_GRADIENT
│   └── TEXTURE_PATTERN
│
└── TexturePattern
    ├── CHECKERBOARD
    ├── DIAGONAL_HATCH
    ├── POLKA_DOTS
    └── BRICK_WALL

AffineTransformView
│
├── Translation
├── Rotation
├── Scaling
├── Shearing
├── Matrix coefficients
├── Reference path
└── Orientation grid
```

---

# AG. Files in This Learning Folder

```
examples/02-advanced-styling-and-affine-transformations/
│
├── README.md
│
├── notes/
│   └── Mobile-development-notes.pdf
│
└── android-source/
    ├── AdvancedStylingView.kt
    ├── AffineTransformView.kt
    ├── AffineMatrixTest.kt
    ├── MainActivity.kt
    ├── activity_main.xml
    ├── fragment_advanced_styling.xml
    ├── fragment_affine_transform.xml
    └── nav_menu.xml
```

## Suggested study order

1. Read the notes on stroke/fill and affine transformations.
2. Read `AdvancedStylingView.kt`.
3. Reproduce one shader yourself.
4. Study `BitmapShader` and procedural texture generation.
5. Read `AffineTransformView.kt`.
6. Reproduce translation manually.
7. Compare manual formulas with Android `Matrix`.
8. Move to rotation, scaling, and shear.
9. Watch the 3×3 matrix values while moving the sliders.
10. Read `AffineMatrixTest.kt` to connect formulas to testable values.

---

**Learning focus:** understand the geometry first, then the Android API, then the implementation, then reproduce the feature yourself.
