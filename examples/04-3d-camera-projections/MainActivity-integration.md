# Projection Feature — MainActivity Integration Reference

This guide pinpoints the Activity/UI integration for the new projection module. The full source is included in [`complete-app/app/src/main/java/com/example/test/MainActivity.kt`](complete-app/app/src/main/java/com/example/test/MainActivity.kt).

## 1. Bind the projection screen and controls

```kotlin
private lateinit var layoutProjections: View
private lateinit var projectionsView: ProjectionsView
private lateinit var rgProjectionType: RadioGroup
private lateinit var tvProjectionMatrixDisplay: TextView
private lateinit var tvClippingStatus: TextView
private lateinit var sbFov: SeekBar
private lateinit var sbObjectDistance: SeekBar
private lateinit var sbObjectRotation: SeekBar
private lateinit var sbScaleSx: SeekBar
private lateinit var sbScaleSy: SeekBar
private lateinit var sbNearPlane: SeekBar
private lateinit var sbFarPlane: SeekBar
```

In `onCreate()`, after `setContentView()`, find the root included layout and initialize the feature once:

```kotlin
layoutProjections = findViewById(R.id.layoutProjections)
setupProjectionsSection()
```

## 2. Navigation integration

The nav menu ID is `nav_projections`. The click listener calls `showSection(layoutProjections, "3D Projections (Perspective vs. Ortho)")`. The shared `showSection()` method hides the other five included screen layouts before showing the selected one.

The host XML includes the screen hidden at startup:

```xml
<include
    android:id="@+id/layoutProjections"
    layout="@layout/fragment_projections"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone" />
```

## 3. Matrix and depth status callback

```kotlin
projectionsView.onProjectionMatrixUpdatedListener = { m, clippedCount ->
    updateMatrix4Display(tvProjectionMatrixDisplay, m)
    if (clippedCount == 0) {
        tvClippingStatus.text = "Frustum Clipping: All 10 Vertices Inside View Frustum"
        tvClippingStatus.setTextColor("#388E3C".toColorInt())
    } else {
        tvClippingStatus.text =
            "Frustum Clipping: $clippedCount / 10 Vertices Clipped by Near/Far Planes!"
        tvClippingStatus.setTextColor("#D32F2F".toColorInt())
    }
}
```

The label uses the term "Frustum Clipping", but in this implementation the count represents only Z-depth NDC outside ([-1,1]); it does not perform actual polygon clipping or X/Y side-plane tests. See the parent README for the full explanation.

## 4. Projection type selector

```kotlin
rgProjectionType.setOnCheckedChangeListener { _, checkedId ->
    when (checkedId) {
        R.id.rbPerspective ->
            projectionsView.currentProjectionType = ProjectionsView.ProjectionType.PERSPECTIVE
        R.id.rbOrthographic ->
            projectionsView.currentProjectionType = ProjectionsView.ProjectionType.ORTHOGRAPHIC
    }
    projectionsView.invalidate()
}
```

The RadioGroup maps resource IDs to a typed enum. `invalidate()` requests a new draw pass using the chosen matrix builder.

## 5. Slider-to-state mappings

| Control | Mapping | State changed |
|---|---|---|
| FOV | (20 + progress) degrees | `fovDegrees` |
| Object distance | (200 + progress) | `objectDistanceZ` |
| Y rotation | (progress) degrees | `objectRotationY` |
| X scale | (max(progress,10)/80) | `scaleSx` |
| Y scale | (max(progress,10)/80) | `scaleSy` |
| Near plane | (10 + progress) | `nearPlane` |
| Far plane | (500 + progress) | `farPlane` |

Each control updates a property and calls `invalidate()`. The shared SeekBar helper invokes the update lambda only for a user-driven progress change.

## 6. Matrix display

The shared helper formats the 16 entries in four rows with `String.format(Locale.US, ...)`. It lets the matrix be inspected while changing FOV, distance, scale or clipping planes.

## 7. Integration files

- `android-source/MainActivity.kt`: complete combined Activity for six app sections.
- `android-source/fragment_projections.xml`: feature controls and custom View declaration.
- `android-source/ProjectionsView.kt`: new rendering and math implementation.
- `android-source/CameraProjectionMatrixTest.kt`: matrix/coordinate unit-test examples.
- `complete-app/`: combined source snapshot with the prior modules and this new section.
