# Android App Development Techniques

A learning-focused Android repository for understanding how Android application-development techniques work, not just memorizing APIs.

This repository is being built progressively from practical experiments, course material, personal notes, and small implementations. Each topic is intended to contain:

- the concept and intuition
- the important Android APIs/classes
- a working implementation
- the exact code patterns worth remembering
- common mistakes and observations
- small experiments that make the concept easier to understand

## Learning Roadmap

| Topic | Status |
|---|---|
| Polygon drawing with a custom Android `View` | ✅ |
| Touch / gesture coordinate handling | ✅ |
| `Canvas`, `Paint`, and `Path` drawing | ✅ |
| Closing and filling arbitrary polygons | ✅ |
| Dynamic polygon colors | ✅ |
| Undo / clear state handling | ✅ |
| Kotlin / Android fundamentals | 🚧 |
| Jetpack Compose | 🚧 |
| MVVM / application architecture | 🚧 |
| Local persistence | 🚧 |
| Networking / APIs | 🚧 |
| Firebase | 🚧 |
| Coroutines / background work | 🚧 |
| Advanced Android techniques | 🚧 |

---

# 01. Polygon Drawing & Colorizer

**Location:** `examples/01-polygon-drawing-colorizer`

This first example is a small Android application that lets the user create a polygon by tapping points on the screen.

The implementation demonstrates:

`View` → `MotionEvent` → `PointF` → `Canvas` → `Paint` → `Path` → polygon state → dynamic color

## What the app does

1. Tap anywhere on the drawing area to create a vertex.
2. Every tap is converted into an `(x, y)` coordinate.
3. The vertices are stored as `PointF` objects.
4. A `Path` connects the vertices.
5. Once at least three points exist, the first point is highlighted.
6. Tapping near the first point closes the polygon.
7. Closing the polygon uses `Path.close()`, which joins the final vertex back to the first vertex.
8. The closed polygon is filled and stroked.
9. The fill color can be changed dynamically.
10. The last vertex can be undone, or the entire drawing can be cleared.

---

# Important Code Techniques

These are the snippets in this example that are worth learning and remembering.

## 1. Detecting where the user tapped

**File:** `PolygonDrawingView.kt`

The custom `View` receives touch events through `onTouchEvent`. The actual touch location is obtained from `event.x` and `event.y`.

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_DOWN) {
        performClick()
        val x = event.x
        val y = event.y

        if (isClosed) {
            return true
        }

        // ...
    }

    return super.onTouchEvent(event)
}
```

### Important idea

```kotlin
val x = event.x
val y = event.y
```

These are the coordinates of the user's tap inside the custom View.

The point is then stored as:

```kotlin
points.add(PointF(x, y))
```

So the basic flow is:

```
User tap
   ↓
MotionEvent.ACTION_DOWN
   ↓
event.x / event.y
   ↓
PointF(x, y)
   ↓
points list
   ↓
Canvas redraw
```

---

## 2. Storing all polygon vertices

The polygon vertices are maintained as a mutable list:

```kotlin
private val points = mutableListOf<PointF>()
```

Whenever the user taps somewhere that is not interpreted as a closing action:

```kotlin
points.add(PointF(x, y))
```

This is the application's source of truth for the polygon geometry.

---

## 3. Automatically connecting the points

The `Path` is rebuilt during `onDraw()`.

First, the path starts at the first vertex:

```kotlin
polygonPath.reset()
polygonPath.moveTo(points[0].x, points[0].y)
```

Then every remaining vertex is connected:

```kotlin
for (i in 1 until points.size) {
    polygonPath.lineTo(points[i].x, points[i].y)
}
```

The important technique here is:

```kotlin
polygonPath.moveTo(...)
polygonPath.lineTo(...)
```

`moveTo()` establishes the starting position, while every `lineTo()` adds another connected segment.

---

## 4. Showing a preview of the closing edge

Before the polygon is actually closed, the app draws a dashed preview from the most recent point back to the first point.

```kotlin
if (points.size >= 3) {
    val last = points.last()
    val first = points.first()
    canvas.drawLine(last.x, last.y, first.x, first.y, closingPreviewPaint)
}
```

This is useful because the user can visually see which edge will be created when the polygon is closed.

The dashed style is configured with:

```kotlin
private val closingPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 5f
    color = Color.GRAY
    pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
}
```

---

## 5. Detecting when the user taps near the first point

This is the important interaction technique used to close the polygon without requiring the user to hit the exact pixel.

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

So the user only needs to tap within 60 pixels of the starting point.

### Why this is useful

Exact-coordinate matching would be frustrating:

```
tap exactly on starting point → difficult
```

Distance-based detection is much more forgiving:

```
tap near starting point → close polygon
```

The distance is calculated using:

```kotlin
hypot(dx, dy)
```

which is equivalent to the Euclidean distance:

```
distance = √(dx² + dy²)
```

---

## 6. Closing the polygon

The actual closing state is handled by:

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

There are two important checks:

```kotlin
points.size < 3
```

A polygon cannot be formed with fewer than three vertices.

And:

```kotlin
isClosed
```

prevents an already-closed polygon from being closed again.

---

## 7. Automatically joining the last point to the first point

This is the exact piece that makes the open shape become a closed polygon:

```kotlin
if (isClosed) {
    polygonPath.close()

    canvas.drawPath(polygonPath, fillPaint)
    canvas.drawPath(polygonPath, strokePaint)
}
```

The important line is:

```kotlin
polygonPath.close()
```

`Path.close()` adds the closing segment from the current path position back to the path's starting position.

So instead of manually doing:

```kotlin
polygonPath.lineTo(points[0].x, points[0].y)
```

the implementation lets Android close the path:

```kotlin
polygonPath.close()
```

---

## 8. Filling the inside of the polygon

The polygon uses a separate `Paint` configured as `FILL`:

```kotlin
private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = polygonFillColor
}
```

After the path is closed:

```kotlin
canvas.drawPath(polygonPath, fillPaint)
```

This paints the interior of the polygon.

---

## 9. Drawing the polygon outline

The outline uses a different `Paint` configured as `STROKE`:

```kotlin
private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 8f
    color = polygonStrokeColor
    strokeJoin = Paint.Join.ROUND
    strokeCap = Paint.Cap.ROUND
}
```

The actual drawing is:

```kotlin
canvas.drawPath(polygonPath, strokePaint)
```

So the application deliberately separates:

```
fillPaint   → inside
strokePaint → boundary
```

---

## 10. Drawing the vertex dots

Every stored point is visualized using `drawCircle()`:

```kotlin
for (i in points.indices) {
    val pt = points[i]

    if (i == 0 && !isClosed && points.size >= 3) {
        canvas.drawCircle(pt.x, pt.y, 22f, startPointRingPaint)
        canvas.drawCircle(pt.x, pt.y, 12f, pointPaint)
    } else {
        canvas.drawCircle(pt.x, pt.y, 10f, pointPaint)
    }
}
```

This means the first point receives special treatment when the polygon has enough vertices to be closed.

---

# Dynamic Color Changing

## 11. Changing the polygon fill color

The custom `View` exposes a method:

```kotlin
fun setFillColor(color: Int) {
    polygonFillColor = color
    fillPaint.color = polygonFillColor
    invalidate()
}
```

There are two important operations here:

```kotlin
fillPaint.color = polygonFillColor
```

updates the actual `Paint`.

And:

```kotlin
invalidate()
```

tells Android that the `View` needs to be redrawn.

---

## 12. Converting a color into a semi-transparent fill

The activity converts a hexadecimal RGB color into an ARGB color with transparency:

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

    if (polygonDrawingView.isPolygonClosed()) {
        Toast.makeText(
            this,
            "Polygon fill color updated!",
            Toast.LENGTH_SHORT
        ).show()
    }
}
```

The key concept is:

```kotlin
Color.argb(alpha, red, green, blue)
```

Here the alpha value is 150, so the polygon remains partially transparent.

---

## 13. Connecting color buttons to the drawing view

Each button calls the same color-changing function:

```kotlin
btnColorBlue.setOnClickListener {
    setSemiTransparentColor("#1E88E5")
}

btnColorRed.setOnClickListener {
    setSemiTransparentColor("#E53935")
}

btnColorGreen.setOnClickListener {
    setSemiTransparentColor("#4CAF50")
}
```

The same pattern is used for yellow, purple, and orange.

---

# State Management Techniques

## 14. Undoing the latest vertex

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

---

## 15. Clearing the entire polygon

```kotlin
fun clear() {
    points.clear()
    isClosed = false
    onPolygonStateChangedListener?.invoke(isClosed, points.size)
    invalidate()
}
```

This resets both the stored vertices and the polygon's closed/open state.

---

## 16. Redrawing after state changes

A recurring Android custom-View pattern in this project is:

```kotlin
invalidate()
```

Whenever state changes, the view requests a new drawing pass.

So an important mental model is:

```
Change state
    ↓
invalidate()
    ↓
Android calls onDraw()
    ↓
Canvas renders the new state
```

---

# Architecture of This Example

```
MainActivity
    │
    ├── UI button handling
    ├── color selection
    └── status text
            │
            ▼
PolygonDrawingView
    │
    ├── touch handling
    ├── point storage
    ├── polygon state
    ├── Path construction
    ├── Canvas drawing
    └── fill/stroke colors
```

The important separation is:

- `MainActivity` handles screen-level interaction.
- `PolygonDrawingView` owns the drawing logic and polygon state.

---

# Core Files

```
examples/01-polygon-drawing-colorizer/
├── PolygonDrawingView.kt
├── MainActivity.kt
├── activity_main.xml
└── PolygonLogicTest.kt
```

Study `PolygonDrawingView.kt` first. It contains the actual custom-`View` drawing and touch logic.

---

# Learning Philosophy

Future additions should follow the same philosophy:

> **Understand the concept → inspect the important API → implement it → isolate the key code snippet → experiment with it → document what was learned.**

The goal is not to collect random Android code.

The goal is to build a practical **Android App Development Techniques** reference that can be read, studied, modified, and reused.

---

**Learning in public. Building in public.**
