# MainActivity Integration — Canvas Graphs & 3D Cube

This document records the exact wiring blocks used to connect the two new custom Views to the existing multi-section Polygon Studio app. It is separate from the custom View code so the feature-specific logic stays easy to find.

> Package used by the submitted source: `com.example.test`. The classes referenced below are `CanvasGraphsView` and `Cube3DView`.

## 1. View references

Add these fields alongside the other section fields in `MainActivity`:

```kotlin
// Canvas Graphs Section Views
private lateinit var canvasGraphsView: CanvasGraphsView
private lateinit var rgGraphType: RadioGroup
private lateinit var sbArcSweep: SeekBar
private lateinit var sbBarHeight: SeekBar
private lateinit var sbPathCurviness: SeekBar
private lateinit var tvArcSweepLabel: TextView
private lateinit var tvBarHeightLabel: TextView
private lateinit var tvPathCurvinessLabel: TextView

// 3D Homogeneous Cube Section Views
private lateinit var cube3DView: Cube3DView
private lateinit var btnSpinCW: Button
private lateinit var btnSpinCCW: Button
private lateinit var btnSpinStop: Button
private lateinit var btnResetCube: Button
private lateinit var tvMatrix4Display: TextView
private lateinit var sbRotX: SeekBar
private lateinit var sbRotY: SeekBar
private lateinit var sbRotZ: SeekBar
private lateinit var sbPerspectiveW: SeekBar
private lateinit var tvRotX: TextView
private lateinit var tvRotY: TextView
private lateinit var tvRotZ: TextView
private lateinit var tvPerspectiveW: TextView
```

The host layout must also expose `layoutCanvasGraphs` and `layoutCube3D` references as shown in `activity_main.xml`.

## 2. Initialize the new sections

Inside `onCreate()`, after calling `setContentView(R.layout.activity_main)` and finding the included layouts, call:

```kotlin
setupCanvasGraphsSection()
setupCube3DSection()
```

The submitted Activity runs each setup method once so listeners are installed before the user opens the relevant section.

## 3. Navigation wiring

Add the new destination cases to the existing `NavigationView` listener:

```kotlin
R.id.nav_canvas_graphs ->
    showSection(layoutCanvasGraphs, "2D Canvas Graphs (Arc, Rect, Path)")

R.id.nav_cube_3d ->
    showSection(layoutCube3D, "3D Homogeneous Cube & Matrix")
```

The shared `showSection()` method must set all other included layouts to `View.GONE`, then set the selected one to `View.VISIBLE` and update the toolbar title. If an older version of `showSection()` only hides three sections, extend it to hide both new sections as well:

```kotlin
layoutCanvasGraphs.visibility = View.GONE
layoutCube3D.visibility = View.GONE
```

The matching IDs are declared in `nav_menu.xml` and in the two layout files.

---

## 4. Graph setup: all important callbacks

The graph setup binds the custom View and its controls:

```kotlin
private fun setupCanvasGraphsSection() {
    canvasGraphsView = layoutCanvasGraphs.findViewById(R.id.canvasGraphsView)
    rgGraphType = layoutCanvasGraphs.findViewById(R.id.rgGraphType)
    sbArcSweep = layoutCanvasGraphs.findViewById(R.id.sbArcSweep)
    sbBarHeight = layoutCanvasGraphs.findViewById(R.id.sbBarHeight)
    sbPathCurviness = layoutCanvasGraphs.findViewById(R.id.sbPathCurviness)
    tvArcSweepLabel = layoutCanvasGraphs.findViewById(R.id.tvArcSweepLabel)
    tvBarHeightLabel = layoutCanvasGraphs.findViewById(R.id.tvBarHeightLabel)
    tvPathCurvinessLabel = layoutCanvasGraphs.findViewById(R.id.tvPathCurvinessLabel)

    rgGraphType.setOnCheckedChangeListener { _, checkedId ->
        when (checkedId) {
            R.id.rbGraphAll ->
                canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.ALL
            R.id.rbGraphArc ->
                canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.ARC_PIE
            R.id.rbGraphRect ->
                canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.RECT_BAR
            R.id.rbGraphPath ->
                canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.PATH_LINE
        }
        canvasGraphsView.invalidate()
    }

    sbArcSweep.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
        tvArcSweepLabel.text = "Arc Sweep Angle: ${progress}°"
        canvasGraphsView.arcSweepAngle = progress.toFloat()
        canvasGraphsView.invalidate()
    })

    sbBarHeight.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
        val scale = progress / 100f
        tvBarHeightLabel.text = String.format(Locale.US, "Bar Height Scale: %.2fx", scale)
        canvasGraphsView.rectHeightScale = scale
        canvasGraphsView.invalidate()
    })

    sbPathCurviness.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
        val scale = progress / 100f
        tvPathCurvinessLabel.text = String.format(Locale.US, "Path Curve Scale: %.2fx", scale)
        canvasGraphsView.pathCurviness = scale
        canvasGraphsView.invalidate()
    })
}
```

### Why these callbacks matter

- The `RadioGroup` maps a UI ID to a typed `GraphType` enum instead of storing arbitrary strings.
- The sweep angle uses the progress value directly.
- Both scale sliders divide by `100f`, which makes the result floating point (1.0 at progress 100).
- `invalidate()` is the bridge from a state change to a redraw. The Activity changes the property; the custom View uses the new value during `onDraw()`.

---

## 5. Cube setup: spin, matrix callbacks, and rotation controls

```kotlin
private fun setupCube3DSection() {
    cube3DView = layoutCube3D.findViewById(R.id.cube3DView)
    btnSpinCW = layoutCube3D.findViewById(R.id.btnSpinCW)
    btnSpinCCW = layoutCube3D.findViewById(R.id.btnSpinCCW)
    btnSpinStop = layoutCube3D.findViewById(R.id.btnSpinStop)
    btnResetCube = layoutCube3D.findViewById(R.id.btnResetCube)
    tvMatrix4Display = layoutCube3D.findViewById(R.id.tvMatrix4Display)

    sbRotX = layoutCube3D.findViewById(R.id.sbRotX)
    sbRotY = layoutCube3D.findViewById(R.id.sbRotY)
    sbRotZ = layoutCube3D.findViewById(R.id.sbRotZ)
    sbPerspectiveW = layoutCube3D.findViewById(R.id.sbPerspectiveW)

    tvRotX = layoutCube3D.findViewById(R.id.tvRotX)
    tvRotY = layoutCube3D.findViewById(R.id.tvRotY)
    tvRotZ = layoutCube3D.findViewById(R.id.tvRotZ)
    tvPerspectiveW = layoutCube3D.findViewById(R.id.tvPerspectiveW)

    cube3DView.onMatrix4UpdatedListener = { m ->
        updateMatrix4TextDisplay(m)
    }

    btnSpinCW.setOnClickListener { cube3DView.setAutoSpin(1) }
    btnSpinCCW.setOnClickListener { cube3DView.setAutoSpin(-1) }
    btnSpinStop.setOnClickListener { cube3DView.stopAutoSpin() }

    btnResetCube.setOnClickListener {
        cube3DView.resetCube()
        sbRotX.progress = 25
        sbRotY.progress = 45
        sbRotZ.progress = 0
        sbPerspectiveW.progress = 100
        Toast.makeText(this, "3D Cube Reset", Toast.LENGTH_SHORT).show()
    }

    sbRotX.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
        tvRotX.text = "Rotation X (Vertical): ${progress}°"
        cube3DView.rotX = progress.toFloat()
        cube3DView.invalidate()
    })

    sbRotY.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
        tvRotY.text = "Rotation Y (Horizontal 360°): ${progress}°"
        cube3DView.rotY = progress.toFloat()
        cube3DView.invalidate()
    })

    sbRotZ.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
        tvRotZ.text = "Rotation Z (Roll): ${progress}°"
        cube3DView.rotZ = progress.toFloat()
        cube3DView.invalidate()
    })

    sbPerspectiveW.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
        val wDiv = progress.coerceAtLeast(10) / 100f
        tvPerspectiveW.text = String.format(
            Locale.US,
            "Perspective Homogeneous W Division: %.2f",
            wDiv
        )
        cube3DView.perspectiveW = wDiv
        cube3DView.invalidate()
    })
}
```

The matrix callback receives a clone produced by the custom View. The Activity's `updateMatrix4TextDisplay()` formats all 16 array elements in row order. The shared slider listener invokes callbacks only when `fromUser` is true, preventing programmatic progress resets from being treated as user adjustments.

## 6. Matrix output helper

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

`Locale.US` makes decimal formatting stable regardless of the device's locale, and `%.2f` keeps the matrix compact enough to read in the small display.

## 7. Layout files

The full feature-specific XML files are included:

- `android-source/fragment_canvas_graphs.xml`
- `android-source/fragment_cube_3d.xml`
- `android-source/nav_menu.xml`
- `android-source/activity_main.xml` (full combined host layout from the uploaded project)

The two fragment layouts contain the matching custom View declarations and controls. Keep all IDs synchronized with the Activity's `findViewById()` calls; Android generates `R.id.*` from these XML IDs.

## 8. Compatibility with the prior learning folders

This module intentionally does not edit the earlier numbered folders. The uploaded app has five sections and depends on the previous classes `PolygonDrawingView`, `AdvancedStylingView`, and `AffineTransformView` as well as their layouts. When incorporating these snippets into the integrated app, keep those existing classes available under the same package or adapt the imports/package names deliberately.

This integration guide records the new feature-specific wiring. It is not a substitute for the complete existing Activity when merging the module into your Android Studio app.
