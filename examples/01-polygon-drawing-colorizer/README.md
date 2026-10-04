# 01. Polygon Drawing & Colorizer

This example is the foundation for the later graphics experiments.

## What this example teaches

- Custom Android `View`
- `MotionEvent.ACTION_DOWN`
- `event.x` / `event.y`
- `PointF`
- `Canvas`
- `Paint`
- `Path`
- `Path.close()`
- `DashPathEffect`
- distance-based hit testing with `hypot(...)`
- fill vs stroke drawing
- semi-transparent ARGB colors
- `invalidate()`
- simple polygon state management

## 1. Detecting a tap

**File:** `PolygonDrawingView.kt`

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_DOWN) {
        performClick()
        val x = event.x
        val y = event.y

        // ...
    }

    return super.onTouchEvent(event)
}
```

The two important values are:

```kotlin
val x = event.x
val y = event.y
```

They represent the touch location inside the custom View.

The point is stored using:

```kotlin
points.add(PointF(x, y))
```

## 2. Storing polygon vertices

```kotlin
private val points = mutableListOf<PointF>()
```

The list is the source of truth for the polygon geometry.

## 3. Joining vertices

```kotlin
polygonPath.reset()
polygonPath.moveTo(points[0].x, points[0].y)

for (i in 1 until points.size) {
    polygonPath.lineTo(points[i].x, points[i].y)
}
```

Conceptually:

```
tap
 ↓
PointF
 ↓
points list
 ↓
Path.moveTo()
 ↓
Path.lineTo()
 ↓
Canvas
```

## 4. Previewing the closing edge

Before the polygon is closed, the code draws a dashed line from the latest vertex to the first:

```kotlin
if (points.size >= 3) {
    val last = points.last()
    val first = points.first()
    canvas.drawLine(last.x, last.y, first.x, first.y, closingPreviewPaint)
}
```

The dashed paint uses:

```kotlin
private val closingPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 5f
    color = Color.GRAY
    pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
}
```

## 5. Detecting a tap near the starting point

The app does not require pixel-perfect clicking on the first point.

```kotlin
if (points.size >= 3) {
    val startPt = points.first()
    val distance = hypot(
        (x - startPt.x).toDouble(),
        (y - startPt.y).toDouble()
    ).toFloat()

    if (distance <= closeTouchThresholdPx) {
        closePolygon()
        return true
    }
}
```

The threshold is:

```kotlin
private val closeTouchThresholdPx = 60f
```

This is a simple Euclidean-distance hit test:

```
distance = √((x-x₀)² + (y-y₀)²)
```

## 6. Closing the polygon

```kotlin
fun closePolygon(): Boolean {
    if (points.size < 3 || isClosed) {
        return false
    }

    isClosed = true
    onPolygonStateChangedListener?.invoke(isClosed, points.size)
    invalidate()
    return true
}
```

The actual path closure occurs through:

```kotlin
polygonPath.close()
```

That closes the current path back to its starting point.

## 7. Fill and stroke

Fill:

```kotlin
private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = polygonFillColor
}
```

Stroke:

```kotlin
private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 8f
    color = polygonStrokeColor
    strokeJoin = Paint.Join.ROUND
    strokeCap = Paint.Cap.ROUND
}
```

Rendering:

```kotlin
canvas.drawPath(polygonPath, fillPaint)
canvas.drawPath(polygonPath, strokePaint)
```

## 8. Dynamic color changes

```kotlin
fun setFillColor(color: Int) {
    polygonFillColor = color
    fillPaint.color = polygonFillColor
    invalidate()
}
```

The Activity converts a hex color into a semi-transparent ARGB value:

```kotlin
private fun setSemiTransparentColor(hexColor: String) {
    val baseColor = hexColor.toColorInt()

    val semiTransparentColor = Color.argb(
        150,
        Color.red(baseColor),
        Color.green(baseColor),
        Color.blue(baseColor)
    )

    polygonDrawingView.setFillColor(semiTransparentColor)
}
```

## 9. Undo and clear

Undo:

```kotlin
fun undoLastPoint() {
    if (points.isNotEmpty()) {
        points.removeAt(points.size - 1)
        isClosed = false
        onPolygonStateChangedListener?.invoke(isClosed, points.size)
        invalidate()
    }
}
```

Clear:

```kotlin
fun clear() {
    points.clear()
    isClosed = false
    onPolygonStateChangedListener?.invoke(isClosed, points.size)
    invalidate()
}
```

## 10. Redraw model

A central Android custom-View pattern used here is:

```
Change state
   ↓
invalidate()
   ↓
onDraw()
   ↓
Canvas renders current state
```

## Files

```
examples/01-polygon-drawing-colorizer/
├── PolygonDrawingView.kt
├── MainActivity.kt
├── activity_main.xml
└── PolygonLogicTest.kt
```

Study `PolygonDrawingView.kt` first. It contains the core touch, geometry, and drawing logic.

---

**Learning focus:** understand the code, identify the important API calls, then reproduce the same behavior yourself before moving to the advanced examples.
