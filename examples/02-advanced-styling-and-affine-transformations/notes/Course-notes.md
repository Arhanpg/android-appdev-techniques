# Course Notes — Drawing, Fills, Gradients & Affine Transformations

These notes are transcribed and organized from the uploaded **Mobile development** PDF and are kept as study notes alongside the implementation.

## Page 1 — Drawing lines and accumulating vertices

The notes introduce line drawing by starting at one coordinate and then adding line segments for subsequent coordinates.

Core idea:

```
Path
  ↓
moveTo(x0, y0)
  ↓
lineTo(x1, y1)
  ↓
lineTo(x2, y2)
  ↓
...
```

The coordinates of the polygon are therefore a sequence of vertices:

```
(x0, y0), (x1, y1), (x2, y2), ...
```

This maps directly to the repository's `Path.moveTo()` and repeated `Path.lineTo()` implementation.

## Page 2 — Closing and colouring the polygon

The notes explicitly show the closing operation:

```kotlin
mylines.close()
```

The notes then separate the two Paint styles:

```kotlin
redfillPaint.setStyle(Paint.Style.FILL)
blackPaint.setStyle(Paint.Style.STROKE)
```

and render the same path twice:

```kotlin
canvas.drawPath(mylines, redfillPaint)
canvas.drawPath(mylines, blackPaint)
```

Conceptually:

```
closed Path
   ├── FILL   → interior
   └── STROKE → outline
```

This is reflected in the new Android implementation through `FillMode`.

## Page 3 — Stroke and fill types

The notes classify several rendering styles:

- Stroke
- Fill
- Stroke & Fill
- Gradient fill
- Texture fill

The important learning point is that the geometry can remain the same while the Paint/shader changes.

## Page 4 — Gradient fill and affine transformations

The notes introduce gradient filling and then move to affine transformations.

The transformation families listed are:

- Translation
- Rotation
- Scaling
- Shearing

The implementation in this repository uses Android's `Matrix` API to represent the combined transformation.

## Page 5 — Translation and rotation

### Translation

The notes give the coordinate update:

```
x' = x + a
y' = y + b
```

where `a` and `b` are the horizontal and vertical translations.

### Rotation

The notes give the standard 2D rotation equations:

```
x' = cos(θ)x - sin(θ)y
y' = sin(θ)x + cos(θ)y
```

The Android implementation delegates this calculation to:

```kotlin
affineMatrix.postRotate(...)
```

## Page 6 — Scaling, shear and matrix representation

### Scaling

The notes describe:

```
x' = c x
y' = d y
```

The implementation exposes independent:

```kotlin
affineScaleX
affineScaleY
```

and applies them with:

```kotlin
affineMatrix.postScale(...)
```

### Shear

The notes describe shear using:

```
x' = x + e y
y' = f x + y
```

The implementation exposes:

```kotlin
skewX
skewY
```

and uses:

```kotlin
affineMatrix.postSkew(...)
```

### Matrix form

The notes connect these coordinate equations to a matrix representation.

The repository makes that representation visible by reading:

```kotlin
affineMatrix.getValues(matrixValues)
```

and displaying the nine values as a 3×3 matrix.

## Practical connection between the notes and the code

The learning chain is:

```
course theory
   ↓
coordinate geometry
   ↓
Path / Paint / Shader APIs
   ↓
Android Matrix
   ↓
interactive implementation
   ↓
live visual + matrix feedback
```

The handwritten annotations in the uploaded notes are particularly useful as a reminder of the conceptual progression: first learn how points and paths are constructed, then understand fill/stroke/shader styles, then move into coordinate transformations and matrix operations.

## Source note

The uploaded PDF is the source for these study notes. The page screenshots in the PDF contain diagrams and handwritten annotations; the repository README summarizes the relevant diagrams and ties them to the exact implementation snippets in this folder.
