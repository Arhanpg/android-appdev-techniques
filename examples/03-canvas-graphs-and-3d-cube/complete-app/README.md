# Complete Polygon Studio App Snapshot

This folder contains the integrated Android project source from the cube-and-graphs update, including the earlier polygon, advanced-styling, and affine-transform sections.

## Open in Android Studio

Open this folder as the project root:

`examples/03-canvas-graphs-and-3d-cube/complete-app/`

The project uses Kotlin, Android Views/Canvas, Material Components, and Android Gradle Plugin settings matching the uploaded project. Its original wrapper script and wrapper JAR are not included in this source snapshot, so use Android Studio's configured Gradle installation or add a Gradle wrapper before running command-line Gradle tasks.

## Main files

- `app/src/main/java/com/example/test/MainActivity.kt` — navigation and user-control wiring for all five sections.
- `PolygonDrawingView.kt` — original polygon drawing basics.
- `AdvancedStylingView.kt` — solid, gradient, texture and stroke modes.
- `AffineTransformView.kt` — 2D matrix transformations.
- `CanvasGraphsView.kt` — arc/pie-style gauge, rounded bars, and curved line plot.
- `Cube3DView.kt` — homogeneous cube transforms, manual 2D projection, face sorting, and rotation controls.
- `app/src/main/res/layout/` — all screens/layouts.
- `app/src/main/res/menu/nav_menu.xml` — five navigation destinations.
- `app/src/test/` — polygon, affine-matrix, and homogeneous-coordinate example tests.

For the focused, line-by-line explanation of the two new features, see the parent folder's `README.md`.
