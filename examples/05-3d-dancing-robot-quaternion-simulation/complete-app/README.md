# Integrated Polygon Studio — Robot Simulation Snapshot

This is the combined Android source snapshot for the repository's 3D dancing robot module. It contains the earlier polygon, styling, 2D affine, canvas graphs, 3D cube and camera projection sections, and adds the robot/quaternion section as a new navigation destination.

## Open in Android Studio

Open this directory as the project root. The snapshot includes the project/app Gradle configuration and version catalog. The Gradle wrapper scripts and wrapper JAR are not included in this source-only snapshot; use a configured Gradle installation or add the wrapper before invoking `./gradlew`.

## Key robot files

- `app/src/main/java/com/example/test/Robot3DView.kt`: cuboid robot, animation timer, choreography, quaternion transforms, projection and face rendering.
- `app/src/main/java/com/example/test/Quaternion.kt`: `Vector3`, `Quaternion.fromAxisAngle()`, quaternion multiplication and vector rotation.
- `app/src/main/java/com/example/test/MainActivity.kt`: seven-section navigation and robot control wiring.
- `app/src/main/res/layout/fragment_robot_3d.xml`: robot canvas, playback buttons, mode selection and speed control.
- `app/src/main/res/layout/activity_main.xml`: includes the new robot destination.
- `app/src/main/res/menu/nav_menu.xml`: includes `nav_robot_3d`.
- `app/src/test/java/com/example/test/QuaternionMathTest.kt`: four quaternion math tests.

The earlier source files, layouts, theme/resources, Gradle files, and test classes remain included. For a code-by-code explanation of the formulas, rendering stages, Android concepts and limitations, read the parent [`README.md`](../README.md).

## Test information

The uploaded ZIP contains saved Gradle XML test reports timestamped 2026-10-11T02:07:58Z. Across the six test suites, the reports show 15 tests, 0 failures, 0 errors and 0 skipped. This is the result recorded in the supplied archive; it should not be interpreted as a newly run test suite in another environment.

The robot is a Canvas-based graphics simulation. It does not communicate with real motors or hardware and does not implement physics or inverse kinematics.
