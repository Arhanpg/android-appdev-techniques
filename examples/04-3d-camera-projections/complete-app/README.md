# Integrated Polygon Studio — Projection Module Snapshot

This directory contains the integrated Android source snapshot corresponding to the 3D camera projection update. It keeps the five earlier sections and adds the sixth **3D Projections (Perspective vs. Ortho)** destination.

## Open the project

Open this directory as the project root in Android Studio, then allow Android Studio to sync Gradle with your installed/configured Gradle environment.

The source snapshot contains the project/app Gradle files and version catalog. The Gradle wrapper scripts and wrapper JAR are not included in this repository snapshot, so command-line `./gradlew` execution is not available until a wrapper is added.

## Main source

- `app/src/main/java/com/example/test/ProjectionsView.kt`: 3D house, camera-space transform, perspective/orthographic matrices, homogeneous divide, depth status and Canvas rendering.
- `app/src/main/java/com/example/test/MainActivity.kt`: Activity/navigation/control wiring for all six screens.
- `app/src/main/res/layout/fragment_projections.xml`: radio buttons, matrix status, clipping status and sliders.
- `app/src/main/res/layout/activity_main.xml`: includes the projection screen as another hidden destination.
- `app/src/main/res/menu/nav_menu.xml`: adds `nav_projections`.
- `app/src/test/java/com/example/test/CameraProjectionMatrixTest.kt`: the three projection formula tests.

The original polygon, advanced-styling, affine-transformation, graph and 3D-cube source/resources/tests are also included. For a code-by-code explanation and notes on projection mathematics and implementation limitations, read the parent [`README.md`](../README.md).

## Source archive and tests

Generated caches, build intermediates and the built APK from the uploaded ZIP were not copied into this source-only folder. The uploaded archive contained its previous test reports: 11 tests, zero failures, and zero errors. A fresh test run in the editing environment could not complete because Gradle 9.4.1 could not be downloaded from `services.gradle.org`.

**Important:** the detailed README describes the current code as uploaded, including its depth-only clipping diagnostic and orthographic scaling behavior. It does not represent those limitations as features of a complete 3D renderer.
