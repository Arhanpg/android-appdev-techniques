# Integrated Android App Snapshot — OpenGL ES Addition

This folder contains a combined source snapshot of the learning app's existing Canvas/linear-algebra/robot sections plus the new OpenGL ES 2D rendering section.

## Open the project

Open this directory as the project root in Android Studio. It contains root/app Gradle configuration, version catalog, Kotlin source files, layouts, navigation, themes, manifest, backup XML resources and tests.

The Gradle wrapper scripts/JAR and local.properties from the development machine are not included in this source-only repository snapshot. local.properties is machine-specific; caches, compiled class files, APK outputs and generated test reports are intentionally excluded. Use an available configured Gradle installation or add a wrapper before running command-line tasks.

## Important OpenGL files

- app/src/main/java/com/example/test/OpenGL2DShapeRenderer.kt: GLSL source, shader/program setup, shape buffers, transformation matrices, uniforms and draw calls.
- app/src/main/java/com/example/test/OpenGLSurfaceView.kt: OpenGL ES 2.0 GLSurfaceView, touch handling and renderer helper methods.
- app/src/main/java/com/example/test/MainActivity.kt: navigation and UI bindings for all existing features plus OpenGL.
- app/src/main/res/layout/fragment_opengl_shapes.xml: shape selector, solid colors, rainbow mode, rotation and scale.
- app/src/main/res/layout/activity_main.xml: includes the OpenGL screen alongside the earlier modules.
- app/src/main/res/menu/nav_menu.xml: adds nav_opengl.
- app/src/test/java/com/example/test/OpenGLMathTest.kt: two OpenGL-supporting mathematical/data-layout tests.

The manifest declares OpenGL ES 2.0 as a required feature in this snapshot. The application icon uses a system drawable because generated launcher-image assets are excluded; replace that reference when adding your own launcher icons.

For detailed code snippets, mathematics, shader semantics, buffer layout, matrix composition, threading, performance and known limitations, read the parent README.

## Test evidence

The uploaded ZIP includes saved Gradle XML reports timestamped 2026-10-28 05:37 UTC. Across seven suites the reports show 17 tests, zero failures, zero errors and zero skipped. These are saved results from the supplied archive, not a fresh test run performed during this repository update.

This module uses OpenGL ES 2.0 and GLSL ES 1.00-style shaders. It does not use Vulkan, a 3D engine, texture samplers, lighting, or a 3D model loader.
