# Robot Simulation — MainActivity Integration Reference

This file supplements the full source in [`complete-app/app/src/main/java/com/example/test/MainActivity.kt`](complete-app/app/src/main/java/com/example/test/MainActivity.kt). It pinpoints the new navigation and UI wiring without duplicating the quaternion mathematics covered in the parent README.

## 1. Bind the included robot layout

The Activity finds the included root layout in `onCreate()`:

~~~kotlin
layoutRobot3D = findViewById(R.id.layoutRobot3D)
~~~

The robot setup method is invoked after the other section setup calls:

~~~kotlin
setupRobot3DSection()
~~~

The shared navigation drawer maps `R.id.nav_robot_3d` to `layoutRobot3D`. The shared `showSection()` hides all other included layouts, shows the chosen one and updates the toolbar title.

## 2. Resolve the XML controls

~~~kotlin
robot3DView = layoutRobot3D.findViewById(R.id.robot3DView)
btnRobotPlay = layoutRobot3D.findViewById(R.id.btnRobotPlay)
btnRobotPause = layoutRobot3D.findViewById(R.id.btnRobotPause)
btnToggleWireframe = layoutRobot3D.findViewById(R.id.btnToggleWireframe)
rgDanceStyle = layoutRobot3D.findViewById(R.id.rgDanceStyle)
sbDanceSpeed = layoutRobot3D.findViewById(R.id.sbDanceSpeed)
tvDanceSpeedLabel = layoutRobot3D.findViewById(R.id.tvDanceSpeedLabel)
~~~

The `R.id.*` values come from `fragment_robot_3d.xml`. Keeping these names consistent between XML and Activity code is required for correct view binding.

## 3. Playback controls

~~~kotlin
btnRobotPlay.setOnClickListener {
    robot3DView.startDanceTimer()
    Toast.makeText(this, "Dance Animation Started", Toast.LENGTH_SHORT).show()
}

btnRobotPause.setOnClickListener {
    robot3DView.stopDanceTimer()
    Toast.makeText(this, "Dance Animation Paused", Toast.LENGTH_SHORT).show()
}
~~~

The View cancels any timer before starting another, so repeated Play taps do not intentionally create multiple active timers.

## 4. Wireframe toggle

~~~kotlin
btnToggleWireframe.setOnClickListener {
    robot3DView.isWireframeMode = !robot3DView.isWireframeMode
    robot3DView.invalidate()
}
~~~

The boolean flips between wireframe and filled rendering. `invalidate()` schedules the next drawing pass, where the View checks the current flag.

## 5. Choreography modes

~~~kotlin
rgDanceStyle.setOnCheckedChangeListener { _, checkedId ->
    when (checkedId) {
        R.id.rbFullDisco -> robot3DView.currentDanceStyle = Robot3DView.DanceStyle.FULL_DISCO
        R.id.rbMarching -> robot3DView.currentDanceStyle = Robot3DView.DanceStyle.MARCHING
        R.id.rbSideSwing -> robot3DView.currentDanceStyle = Robot3DView.DanceStyle.ARM_SIDE_SWING
        R.id.rbDiagonal -> robot3DView.currentDanceStyle = Robot3DView.DanceStyle.DIAGONAL_WAVE
    }
    robot3DView.invalidate()
}
~~~

The radio button IDs are mapped to a typed enum. Each draw pass reads that enum to choose the quaternion expressions for the pose.

## 6. Dance speed mapping

~~~kotlin
sbDanceSpeed.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    val scale = progress.coerceAtLeast(10) / 100f
    tvDanceSpeedLabel.text = String.format(Locale.US, "Dance Tempo / Speed: %.2fx", scale)
    robot3DView.danceSpeedScale = scale
})
~~~

XML maximum is 250 and initial progress is 100. The effective scale is approximately 0.1×–2.5×. The timer callback interval stays nominally at 30 ms; the slider adjusts the amount added to `danceTime` on each callback.

## 7. Timer lifecycle caveat

`Robot3DView.init` starts the timer automatically. `onDetachedFromWindow()` cancels it, but the hidden layout is included/inflated and then hidden with `GONE`; it is not necessarily detached while hidden. A production-quality version should explicitly start/pause animation when the robot page becomes visible/invisible and coordinate Activity pause/resume.

## 8. Source files

- `android-source/Robot3DView.kt`: animation, geometry and drawing.
- `android-source/Quaternion.kt`: vector and quaternion math.
- `android-source/QuaternionMathTest.kt`: four math tests.
- `android-source/fragment_robot_3d.xml`: controls/layout.
- `android-source/activity_main.xml`: full integrated host layout with robot included.
- `android-source/nav_menu.xml`: navigation menu with the new robot item.
- `complete-app/`: integrated six-prior-sections-plus-robot source snapshot.
