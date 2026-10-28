# OpenGL ES 2D Shapes — Activity and Control Integration

This file maps the Android UI to the OpenGL renderer. Mathematical and pipeline explanations are in the parent README; the complete combined app is in complete-app/README.md.

## 1. Host layout and navigation

The host activity_main.xml includes the OpenGL panel as a new destination:

~~~xml
<include
    android:id="@+id/layoutOpenGL"
    layout="@layout/fragment_opengl_shapes"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone" />
~~~

The NavigationView menu adds nav_opengl. The Activity's menu handler maps that item ID to layoutOpenGL, shows it using the shared showSection() helper and updates the toolbar title.

## 2. Binding views

~~~kotlin
openGLSurfaceView = layoutOpenGL.findViewById(R.id.openGLSurfaceView)
rgGLShape = layoutOpenGL.findViewById(R.id.rgGLShape)
btnGLBlue = layoutOpenGL.findViewById(R.id.btnGLBlue)
btnGLRainbow = layoutOpenGL.findViewById(R.id.btnGLRainbow)
btnGLAutoRotate = layoutOpenGL.findViewById(R.id.btnGLAutoRotate)
btnGLReset = layoutOpenGL.findViewById(R.id.btnGLReset)
sbGLScale = layoutOpenGL.findViewById(R.id.sbGLScale)
tvGLScaleLabel = layoutOpenGL.findViewById(R.id.tvGLScaleLabel)
~~~

The Activity also binds the remaining color buttons. The ID strings have to match fragment_opengl_shapes.xml exactly.

## 3. Shape selector

~~~kotlin
rgGLShape.setOnCheckedChangeListener { _, checkedId ->
    when (checkedId) {
        R.id.rbGLTriangle -> openGLSurfaceView.setShape(OpenGL2DShapeRenderer.ShapeType.TRIANGLE)
        R.id.rbGLSquare -> openGLSurfaceView.setShape(OpenGL2DShapeRenderer.ShapeType.SQUARE)
        R.id.rbGLPentagon -> openGLSurfaceView.setShape(OpenGL2DShapeRenderer.ShapeType.PENTAGON)
        R.id.rbGLHexagon -> openGLSurfaceView.setShape(OpenGL2DShapeRenderer.ShapeType.HEXAGON)
        R.id.rbGLOctagon -> openGLSurfaceView.setShape(OpenGL2DShapeRenderer.ShapeType.OCTAGON)
        R.id.rbGLCircle -> openGLSurfaceView.setShape(OpenGL2DShapeRenderer.ShapeType.CIRCLE)
    }
}
~~~

The selected resource ID becomes a typed shape enum. The renderer reads that enum during its next frame and chooses a draw method.

## 4. Color mode

The solid-color buttons call setSolidColorRGBA(r,g,b), which writes normalized float channels and sets mode SOLID. The rainbow button selects RAINBOW_GRADIENT. These choices change the uniform/attribute path used in the fragment shader.

## 5. Rotation, reset and scale

Auto-rotate toggles renderer.isAutoRotating; the frame loop increments rotation while the flag is true. Reset sets scale to 1, translation to 0, rotation to 0, and slider progress to 80.

The scale mapping is:

$$
scale=\frac{\max(progress,10)}{80}
$$

The SeekBar maximum is 280 and initial progress 80, so the scale range is 0.125×–3.5× and starts at 1.0×.

## 6. Touch gestures

OpenGLSurfaceView.onTouchEvent() records touch coordinates on down and calculates incremental dx/dy values on move. It adds dx * 0.5f to rotation, adds dx * 0.002f to X translation, and subtracts dy * 0.002f from Y translation.

The source changes renderer state from the UI thread while the GL renderer reads it on the GL thread. For production use, enqueue those state mutations with GLSurfaceView.queueEvent { ... } or adopt another explicit thread-safe hand-off.

## 7. Integration files

- android-source/MainActivity.kt: full Activity with new screen and control listeners.
- android-source/activity_main.xml: shared root layout plus OpenGL include.
- android-source/nav_menu.xml: drawer menu including nav_opengl.
- android-source/fragment_opengl_shapes.xml: renderer surface and controls.
- android-source/OpenGLSurfaceView.kt: surface setup, state helpers and touch events.
- android-source/OpenGL2DShapeRenderer.kt: shaders, buffers, matrices and draw commands.
- android-source/OpenGLMathTest.kt: polygon coordinate and buffer-size tests.
