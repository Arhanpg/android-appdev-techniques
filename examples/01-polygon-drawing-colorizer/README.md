# Polygon Drawing & Colorizer

This is the first practical example in the repository.

It focuses on learning how a traditional Android custom View can turn touch input into a drawable polygon.

## What to study

1. PolygonDrawingView.kt — the core drawing and touch logic.
2. MainActivity.kt — UI actions and color selection.
3. activity_main.xml — the custom View and controls.
4. PolygonLogicTest.kt — basic logic tests.

## Core techniques

- MotionEvent.ACTION_DOWN
- event.x / event.y
- PointF
- Canvas
- Paint
- Path
- Path.close()
- DashPathEffect
- hit-distance detection with hypot(...)
- Color.argb(...)
- View.invalidate()

## Learning flow

Tap → coordinates → PointF → points list → Path → Canvas → redraw

The repository root README contains the exact snippets worth remembering and explains what each piece is doing.
