# 05. 3D Dancing Robot Simulation with Quaternions

This module adds an articulated, animated 3D-style robot to the Android Canvas learning app. It is the next step after the custom polygon View, gradient and texture fills, 2D affine transformations, custom graphs, the 3D cube, and the projection demo.

The central topic is **how quaternion rotations can animate the separate cuboids that form an articulated model**, and how those transformed 3D faces can be rendered as 2D Android paths.

The robot does **not** control physical hardware. It is a local graphics simulation: Kotlin calculates pose and geometry; Android `Canvas`, `Paint`, and `Path` draw the projected surfaces.

## Quick links to the exact source

- [`Robot3DView.kt`](android-source/Robot3DView.kt) — robot geometry, choreography, hierarchical transforms, projection, face sorting and drawing.
- [`Quaternion.kt`](android-source/Quaternion.kt) — `Vector3`, quaternion construction, quaternion multiplication and vector rotation.
- [`fragment_robot_3d.xml`](android-source/fragment_robot_3d.xml) — the robot display and all controls.
- [`MainActivity.kt`](android-source/MainActivity.kt) — play/pause, wireframe, dance-style and speed-slider wiring.
- [`activity_main.xml`](android-source/activity_main.xml) — the new robot screen included in the app.
- [`nav_menu.xml`](android-source/nav_menu.xml) — adds the Robot destination to the drawer.
- [`QuaternionMathTest.kt`](android-source/QuaternionMathTest.kt) — four tests of quaternion rotation behavior.
- [Complete integrated Android project](complete-app/README.md) — source for the earlier sections plus the robot.

---

# 1. What the robot simulation does

The screen builds a robot using cuboids for its main body parts, calculates time-varying rotations, transforms their vertices, projects those vertices onto the screen, sorts faces by average depth, then draws each face.

The robot is made from **12 cuboid parts**:

| Body component | Cuboid | Base color in the source |
|---|---|---|
| Head | `headCuboid` | Bright green |
| Neck | `neckCuboid` | Yellow |
| Torso | `torsoCuboid` | Blue |
| Pelvis | `pelvisCuboid` | Purple |
| Left/right upper arms | `upperArmCuboid` (reused geometry) | Orange |
| Left/right forearms | `forearmCuboid` (reused geometry) | Cyan |
| Left/right upper legs | `upperLegCuboid` (reused geometry) | Red |
| Left/right lower legs | `lowerLegCuboid` (reused geometry) | Light green |

Each cuboid has eight local-space vertices and six four-vertex faces. The same geometry definition is reused for left/right limbs; each placement supplies different pivot/offset/rotation values.

## The complete drawing pipeline

~~~text
danceTime + chosen DanceStyle
          |
          v
sin()/cos() phase equations for body, legs and arms
          |
          v
Axis-angle quaternion for each desired rotation
          |
          v
Quaternion composition for selected choreography
          |
          v
Rotate local cuboid vertices and place each body part
          |
          v
Simple perspective-like X/Y projection
          |
          v
Create one TransformedFace per cuboid face
          |
          v
Sort all faces by avgZ (Painter's Algorithm)
          |
          v
Build a 2D Path for each face
          |
          v
Filled/stroked mode OR wireframe mode
          |
          v
Android Canvas displays the current frame
~~~

The main flow is implemented inside `Robot3DView.onDraw()`. This matters because Android custom Views don't continuously draw themselves by default: this example has a timer that advances animation state and requests redraws.

---

# 2. Android concepts and project structure

## 2.1 The custom View

**Exact code — `Robot3DView.kt`:**

~~~kotlin
class Robot3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    // Robot state, geometry, animation and drawing.
}
~~~

- `View` is the Android UI component whose pixels are drawn by `onDraw()`.
- `Context` provides access to the Android environment.
- `AttributeSet?` allows Android to inflate the custom View from XML.
- `defStyleAttr` participates in style resolution.
- `@JvmOverloads` generates constructor overloads useful for Java interoperability and Android's XML inflation requirements.

The layout hosts the custom view directly:

~~~xml
<com.example.test.Robot3DView
    android:id="@+id/robot3DView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
~~~

The Activity does not calculate every cube vertex itself. Instead, it updates a small number of properties on the View and asks it to redraw. This separation is a useful custom-View design pattern: **Activity = controls and navigation; View = graphics state, geometry calculations and rendering**.

## 2.2 The root app layout and navigation drawer

The new robot layout is included inside the same `FrameLayout` as the earlier six screens:

~~~xml
<include
    android:id="@+id/layoutRobot3D"
    layout="@layout/fragment_robot_3d"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone" />
~~~

It starts with `GONE`, so another section is shown first. Selecting the robot item in the navigation drawer calls:

~~~kotlin
R.id.nav_robot_3d -> {
    showSection(layoutRobot3D, "3D Dancing Robot (Quaternions)")
}
~~~

The shared `showSection()` hides the other layouts, shows the selected target, and updates the toolbar title. The menu entry is declared in `nav_menu.xml` as `nav_robot_3d`.

**Android learning point:** `View.GONE` removes that View from layout participation and hides it; it does not necessarily detach an already-inflated View from its parent. That distinction is particularly important for the timer in this feature and is discussed in the lifecycle section below.

---

# 3. The mathematical building blocks: Vector3

**Exact code — `Quaternion.kt`:**

~~~kotlin
data class Vector3(var x: Float, var y: Float, var z: Float) {
    fun add(other: Vector3): Vector3 =
        Vector3(x + other.x, y + other.y, z + other.z)

    fun subtract(other: Vector3): Vector3 =
        Vector3(x - other.x, y - other.y, z - other.z)
}
~~~

A `Vector3` represents a 3D position or direction:

$$
\mathbf v=(x,y,z)
$$

The vector operations used by this implementation are componentwise addition and subtraction.

For example:

$$
(2,3,4)+(10,-2,1)=(12,1,5)
$$

**Where it is used:** local cuboid vertices, body-part center locations, shoulder/hip/knee pivots, offsets from joints, and transformed world-space points.

The current implementation uses mutable `Float` fields and returns new `Vector3` objects from `add()` and `subtract()`. The transformation methods in the View create arrays of vertices during drawing. This is straightforward to learn from, though a high-throughput renderer may reuse temporary storage to reduce allocations.

---

# 4. What is a quaternion?

A quaternion is a four-component number, represented here as:

$$
q=(w,x,y,z)
$$

The scalar component is $w$; the vector part is $(x,y,z)$. For a **unit quaternion**, a 3D rotation can be encoded without storing a full 3×3 or 4×4 rotation matrix.

**Exact data class:**

~~~kotlin
data class Quaternion(
    var w: Float = 1.0f,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
)
~~~

The default values ((1,0,0,0)) are the identity rotation.

## 4.1 Why use quaternions for this robot?

Each body part can be assigned a rotation around a selected axis. Quaternion rotations are composable: the code can combine the body rotation with a joint rotation and can combine several arm motions for the Full Disco style.

This example uses quaternions as a practical 3D-rotation representation. It does not implement inverse kinematics, a physics engine, collision detection, joint constraints, or a skeletal animation framework.

---

# 5. Identity quaternion

**Exact code:**

~~~kotlin
fun identity(): Quaternion = Quaternion(1.0f, 0f, 0f, 0f)
~~~

For a unit quaternion $q=(1,0,0,0)$, rotating any vector should leave it unchanged:

$$
q=(1,0,0,0)\quad\Longrightarrow\quad \mathbf v'=\mathbf v
$$

The first unit test, `testQuaternionIdentity()`, verifies all three output vector components against the original values within a tolerance of 0.001.

The identity quaternion is also the safe fallback returned when `fromAxisAngle()` receives an axis whose length is effectively zero.

---

# 6. Building a rotation quaternion from an axis and angle

## 6.1 Exact code

~~~kotlin
fun fromAxisAngle(
    axisX: Float,
    axisY: Float,
    axisZ: Float,
    angleRad: Float
): Quaternion {
    val len = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
    if (len < 1e-6f) return identity()

    val normX = axisX / len
    val normY = axisY / len
    val normZ = axisZ / len

    val halfAngle = angleRad / 2.0f
    val s = sin(halfAngle)

    return Quaternion(
        cos(halfAngle),
        normX * s,
        normY * s,
        normZ * s
    )
}
~~~

The method has four steps:

1. Calculate the length of the supplied axis.
2. Normalize the axis so its direction has unit length.
3. Calculate half of the rotation angle.
4. Build the scalar and vector parts of the quaternion.

## 6.2 Normalize the axis

For an axis $\mathbf a=(a_x,a_y,a_z)$, its length is:

$$
\|\mathbf a\|=\sqrt{a_x^2+a_y^2+a_z^2}
$$

Then the normalized axis is:

$$
\hat{\mathbf a}=\frac{\mathbf a}{\|\mathbf a\|}
$$

For the diagonal axis $(1,1,0)$:

$$
\|\mathbf a\|=\sqrt{1^2+1^2+0^2}=\sqrt 2
$$

so the normalized axis is:

$$
\hat{\mathbf a}=\left(\frac1{\sqrt2},\frac1{\sqrt2},0\right)
$$

Normalization is important because the axis-angle quaternion formula expects a unit direction.

## 6.3 Half-angle formula

For a unit axis $\hat{\mathbf a}=(a_x,a_y,a_z)$ and rotation angle $\theta$, the quaternion is:

$$
q=\left(\cos\frac\theta2,\;
a_x\sin\frac\theta2,\;
a_y\sin\frac\theta2,\;
a_z\sin\frac\theta2\right)
$$

Notice that the quaternion uses **half the rotation angle**. For a 90° rotation, $\theta/2=45^\circ$. The code parameter is named \`angleRad\`, so it expects radians, not degrees.

A rotation magnitude in degrees is converted at the call site, for example:

~~~kotlin
val marchAngle =
    sin((danceTime * 2.5f).toDouble()).toFloat() *
    Math.toRadians(35.0).toFloat()
~~~

`Math.toRadians(35.0)` converts 35 degrees into radians before the value is passed to the quaternion constructor. This keeps the trigonometric functions and quaternion math consistent.

## 6.4 The zero-axis guard

~~~kotlin
if (len < 1e-6f) return identity()
~~~

A zero-length axis cannot be normalized because that would require division by zero. The implementation chooses identity rotation when the axis length is below (10^{-6}). This is a defensive numeric guard.

---

# 7. Quaternion multiplication — combining rotations

## 7.1 Exact implementation

~~~kotlin
operator fun times(other: Quaternion): Quaternion {
    val nw = w * other.w - x * other.x - y * other.y - z * other.z
    val nx = w * other.x + x * other.w + y * other.z - z * other.y
    val ny = w * other.y - x * other.z + y * other.w + z * other.x
    val nz = w * other.z + x * other.y - y * other.x + z * other.w
    return Quaternion(nw, nx, ny, nz)
}
~~~

Overloading Kotlin's `times` operator makes a multiplication expression read naturally:

~~~kotlin
val totalQuat = bodyQuat * jointQuat
~~~

Mathematically, for $q_1=(w_1,x_1,y_1,z_1)$ and $q_2=(w_2,x_2,y_2,z_2)$:

$$
w=w_1w_2-x_1x_2-y_1y_2-z_1z_2
$$

$$
x=w_1x_2+x_1w_2+y_1z_2-z_1y_2
$$

$$
y=w_1y_2-x_1z_2+y_1w_2+z_1x_2
$$

$$
z=w_1z_2+x_1y_2-y_1x_2+z_1w_2
$$

Quaternion multiplication is **not commutative**:

$$
q_1q_2\ne q_2q_1\quad\text{in general}
$$

Therefore, changing the multiplication order can change the resulting pose. Using a composite quaternion for a joint lets the implementation combine the part's rotation with the overall body rotation.

In this code, `totalQuat = bodyQuat * jointQuat`. Under the standard active-rotation convention $q(0,\mathbf v)q^{-1}$, this product means the right-hand rotation is applied first, followed by the left-hand rotation.

---

# 8. Rotating a Vector3 with a Quaternion

## 8.1 The exact rotation method

~~~kotlin
fun rotateVector(v: Vector3): Vector3 {
    // q_vec × v
    val cx1 = y * v.z - z * v.y
    val cy1 = z * v.x - x * v.z
    val cz1 = x * v.y - y * v.x

    // q_vec × (q_vec × v)
    val cx2 = y * cz1 - z * cy1
    val cy2 = z * cx1 - x * cz1
    val cz2 = x * cy1 - y * cx1

    val rx = v.x + 2f * (w * cx1 + cx2)
    val ry = v.y + 2f * (w * cy1 + cy2)
    val rz = v.z + 2f * (w * cz1 + cz2)

    return Vector3(rx, ry, rz)
}
~~~

A quaternion rotation can be written as:

$$
\mathbf v'=q(0,\mathbf v)q^{-1}
$$

For a unit quaternion $q=(w,\mathbf u)$, where $\mathbf u=(x,y,z)$, the code uses the equivalent vector formula:

$$
\mathbf v'=\mathbf v+2w(\mathbf u\times\mathbf v)
+2\left(\mathbf u\times(\mathbf u\times\mathbf v)\right)
$$

This is why the implementation calculates two cross products. The first is $\mathbf u\times\mathbf v$; the second is $\mathbf u\times(\mathbf u\times\mathbf v)$.

### First cross product

$$
\mathbf u\times\mathbf v=
\left(
yv_z-zv_y,\;
zv_x-xv_z,\;
xv_y-yv_x
\right)
$$

The three variables `cx1`, `cy1`, and `cz1` implement these components.

### Second cross product

The next three values compute the cross product between the quaternion vector part and the first cross-product result. The final component formulas combine the original vector with the two cross-product terms.

**Important assumption:** this optimized formula is for a unit quaternion. The axis-angle constructor normalizes the axis and uses sine/cosine of a half-angle, producing a unit quaternion up to floating-point rounding. The multiplication of unit quaternions also preserves unit length mathematically, apart from floating-point error.

## 8.2 Worked example: 90° around Y

The test constructs:

~~~kotlin
val angleRad = Math.toRadians(90.0).toFloat()
val qY = Quaternion.fromAxisAngle(0f, 1f, 0f, angleRad)
val v = Vector3(100f, 0f, 0f)
val rotated = qY.rotateVector(v)
~~~

A positive 90° rotation around the Y-axis maps:

$$
(100,0,0)\rightarrow(0,0,-100)
$$

The test checks X≈0, Y≈0 and Z≈−100 within a tolerance of 0.01. This validates the sign convention used by this implementation.

---

# 9. Building the robot out of cuboids

## 9.1 One reusable cuboid definition

In `Robot3DView.kt`, each `Cuboid3D` stores half-extents and a palette:

~~~kotlin
private class Cuboid3D(
    val halfDx: Float,
    val halfDy: Float,
    val halfDz: Float,
    val faceColors: IntArray
) {
    val localVertices = arrayOf(
        Vector3(-halfDx, -halfDy, -halfDz),
        Vector3( halfDx, -halfDy, -halfDz),
        Vector3( halfDx,  halfDy, -halfDz),
        Vector3(-halfDx,  halfDy, -halfDz),
        Vector3(-halfDx, -halfDy,  halfDz),
        Vector3( halfDx, -halfDy,  halfDz),
        Vector3( halfDx,  halfDy,  halfDz),
        Vector3(-halfDx,  halfDy,  halfDz)
    )
}
~~~

If the half-extents are (d_x,d_y,d_z), the total cuboid dimensions are (2d_x), (2d_y), and (2d_z). Each point is a combination of positive and negative half-extents.

For example, the torso is created as:

~~~kotlin
private val torsoCuboid =
    Cuboid3D(65f, 90f, 40f, createColorPalette("#1E88E5"))
~~~

So its nominal local dimensions are 130 units wide, 180 units tall, and 80 units deep.

## 9.2 Faces refer to vertex indices

~~~kotlin
val faceIndices = arrayOf(
    intArrayOf(4, 5, 6, 7), // Front
    intArrayOf(1, 0, 3, 2), // Back
    intArrayOf(4, 0, 1, 5), // Top
    intArrayOf(7, 6, 2, 3), // Bottom
    intArrayOf(0, 4, 7, 3), // Left
    intArrayOf(5, 1, 2, 6)  // Right
)
~~~

Each entry tells the renderer which four vertices form a face. This is an indexed-mesh pattern: the vertex positions are stored once, and a face references them by index.

A cuboid has 8 vertices and 6 faces; each face here is a quadrilateral with four indices. Each body part reuses the same topology but uses its own half-extents.

## 9.3 Color palette generation

~~~kotlin
private fun createColorPalette(baseHex: String): IntArray {
    val base = baseHex.toColorInt()
    val r = Color.red(base)
    val g = Color.green(base)
    val b = Color.blue(base)

    return intArrayOf(
        Color.rgb(r, g, b),
        Color.rgb((r * 0.85f).toInt(), (g * 0.85f).toInt(), (b * 0.85f).toInt()),
        Color.rgb((r * 0.70f).toInt(), (g * 0.70f).toInt(), (b * 0.70f).toInt()),
        Color.rgb((r * 0.90f).toInt(), (g * 0.90f).toInt(), (b * 0.90f).toInt()),
        Color.rgb((r * 0.75f).toInt(), (g * 0.75f).toInt(), (b * 0.75f).toInt()),
        Color.rgb((r * 0.95f).toInt(), (g * 0.95f).toInt(), (b * 0.95f).toInt())
    )
}
~~~

The input hex color becomes a base RGB value. Other entries multiply each channel by a fraction and convert it back to an integer. As these multipliers are below 1, the generated variants are darker or slightly lighter than the base, producing simple face-to-face shading.

The source uses integer conversion, which truncates the fractional channel values rather than rounding them. These colors are stylistic palette variations; there is no lighting model or normal-based shading in the implementation.

---

# 10. The animation clock and the Android redraw cycle

## 10.1 Timer startup

~~~kotlin
init {
    startDanceTimer()
}

fun startDanceTimer() {
    stopDanceTimer()
    timer = Timer()
    val task = object : TimerTask() {
        override fun run() {
            danceTime += 0.05f * danceSpeedScale
            postInvalidate()
        }
    }
    timer?.schedule(task, 100, 30) // ~33 FPS
}
~~~

The timer waits 100 milliseconds before the first task and then schedules repeated work with a 30-millisecond period. A 30 ms period corresponds to about (1000/30\approx33.3) ticks per second if the system can keep up; it is a nominal target, not a guarantee of exact frame rate.

At each tick:

1. Increase the animation phase `danceTime`.
2. Call `postInvalidate()`.

`postInvalidate()` requests that Android invalidate the View from a non-UI thread safely. The actual drawing occurs later through `onDraw()` on the UI thread.

## 10.2 Why call stop before start?

~~~kotlin
fun stopDanceTimer() {
    timer?.cancel()
    timer = null
}
~~~

`startDanceTimer()` first cancels any previous timer. This avoids creating multiple active timer tasks when the Play button is pressed repeatedly. Pause cancels the timer. It does not reset `danceTime`, so playing again resumes the animation phase from its current value rather than starting choreography at time zero.

## 10.3 Animation speed

The property is:

~~~kotlin
var danceSpeedScale = 1.0f
~~~

The time increment is:

$$
\Delta t_{phase}=0.05\times danceSpeedScale
$$

per timer tick. The slider changes the phase step; it does not change the timer period itself. A scale of 2.0 advances the choreography phase twice as much per callback as a scale of 1.0.

The `onDetachedFromWindow()` override cancels the timer when the View is detached. However, the View starts its timer in `init`, and the robot layout is inflated even while marked `GONE`. A hidden view is not necessarily detached, so the robot can continue consuming timer/redraw work while another section is visible. The current source does not pause/resume the timer based on Activity lifecycle or navigation visibility. This is an important optimization opportunity for a production app.

The timer updates `danceTime` on a background thread while `onDraw()` reads it on the UI thread; the current code does not use explicit synchronization for that float state. This compact demo often works adequately, but a more robust animation loop would use lifecycle-aware frame scheduling and a single-threaded state update strategy.

---

# 11. Periodic choreography mathematics

All the animated angles derive from `danceTime` using sine and cosine functions. These functions oscillate between −1 and +1, producing smooth back-and-forth movement.

## 11.1 Body yaw

~~~kotlin
val bodyAngleY =
    sin(danceTime.toDouble()).toFloat() *
    Math.toRadians(45.0).toFloat()

val qBodyY = Quaternion.fromAxisAngle(0f, 1f, 0f, bodyAngleY)
~~~

The angle is:

$$
\theta_{body}(t)=\sin(t)\times45^\circ
$$

so it oscillates between approximately −45° and +45° around the Y-axis. The angle is converted into radians before being passed to `fromAxisAngle()`.

## 11.2 Leg marching

~~~kotlin
val marchAngle =
    sin((danceTime * 2.5f).toDouble()).toFloat() *
    Math.toRadians(35.0).toFloat()

val qLegLeftX = Quaternion.fromAxisAngle(
    1f, 0f, 0f,
    if (currentDanceStyle == DanceStyle.ARM_SIDE_SWING) 0f else marchAngle
)
val qLegRightX = Quaternion.fromAxisAngle(
    1f, 0f, 0f,
    if (currentDanceStyle == DanceStyle.ARM_SIDE_SWING) 0f else -marchAngle
)
~~~

The marching phase is:

$$
\theta_{march}(t)=\sin(2.5t)\times35^\circ
$$

The left and right leg angles have opposite signs. This creates alternating leg motion. In `ARM_SIDE_SWING` mode, both leg angles are set to zero so that the legs remain static during that choreography.

## 11.3 Arm side swings

~~~kotlin
val armSwingZ =
    cos((danceTime * 2.5f).toDouble()).toFloat() *
    Math.toRadians(40.0).toFloat()

val qArmLeftZ = Quaternion.fromAxisAngle(0f, 0f, 1f, armSwingZ)
val qArmRightZ = Quaternion.fromAxisAngle(0f, 0f, 1f, -armSwingZ)
~~~

This produces opposite rotations around Z:

$$
\theta_{arm}(t)=\cos(2.5t)\times40^\circ
$$

Cosine starts at its maximum value when its input phase is zero, whereas sine starts at zero. Using cosine for arms and sine for legs shifts the phase relation between those movements.

## 11.4 Diagonal arm waves

~~~kotlin
val diagAngle =
    sin((danceTime * 3.0f).toDouble()).toFloat() *
    Math.toRadians(45.0).toFloat()

val qDiagLeft = Quaternion.fromAxisAngle(1f, 1f, 0f, diagAngle)
val qDiagRight = Quaternion.fromAxisAngle(1f, -1f, 0f, -diagAngle)
~~~

The left arm rotates around the axis ((1,1,0)); the right arm uses ((1,-1,0)) with the opposite sign. These are diagonal axes in the XY plane, not simply rotations around a coordinate axis. The `fromAxisAngle()` function normalizes these axis vectors automatically.

The wave angle is:

$$
\theta_{diag}(t)=\sin(3t)\times45^\circ
$$

The multiplier $3.0$ makes this oscillator cycle faster than the body phase $\sin(t)$.

---

# 12. Four choreography styles

The enum is:

~~~kotlin
enum class DanceStyle { MARCHING, ARM_SIDE_SWING, DIAGONAL_WAVE, FULL_DISCO }
~~~

The Activity maps the selected RadioButton ID to one of these enum values.

| Style | Source behavior |
|---|---|
| `MARCHING` | Arms borrow the opposite leg rotation quaternion; legs continue marching |
| `ARM_SIDE_SWING` | Arms use opposite Z-axis swings; leg rotations are forced to zero |
| `DIAGONAL_WAVE` | Arms use the diagonal axes ((1,1,0)) and ((1,-1,0)) |
| `FULL_DISCO` | Each arm composes its Z swing, diagonal wave and the opposite-side leg quaternion |

## 12.1 Quaternion composition for Full Disco

~~~kotlin
val qArmLeftFinal = when (currentDanceStyle) {
    DanceStyle.MARCHING -> qLegRightX
    DanceStyle.ARM_SIDE_SWING -> qArmLeftZ
    DanceStyle.DIAGONAL_WAVE -> qDiagLeft
    DanceStyle.FULL_DISCO -> qArmLeftZ * qDiagLeft * qLegRightX
}
~~~

The right arm uses the corresponding right-side terms:

~~~kotlin
val qArmRightFinal = when (currentDanceStyle) {
    DanceStyle.MARCHING -> qLegLeftX
    DanceStyle.ARM_SIDE_SWING -> qArmRightZ
    DanceStyle.DIAGONAL_WAVE -> qDiagRight
    DanceStyle.FULL_DISCO -> qArmRightZ * qDiagRight * qLegLeftX
}
~~~

This demonstrates the benefit of quaternion multiplication: several rotations can be represented by one combined quaternion. Since multiplication is order-sensitive, the three terms are not interchangeable.

A point rotated by the combined quaternion experiences the composite orientation in a single call. This source uses a stylized choreography rather than biomechanically realistic human or robotic motion.

---

# 13. Hierarchical body transformations

The robot is drawn from components with their own positions and rotations. The source uses separate methods for central cuboids and articulated limbs.

## 13.1 Main body parts

~~~kotlin
val torsoPos = Vector3(0f, -20f, 0f)
transformAndCollectCuboid(torsoCuboid, torsoPos, qBodyY, cx, cy, facesToRender)

val neckPos = Vector3(0f, -125f, 0f)
val headPos = Vector3(0f, -175f, 0f)
transformAndCollectCuboid(neckCuboid, neckPos, qBodyY, cx, cy, facesToRender)

transformAndCollectCuboid(headCuboid, headPos, qBodyY, cx, cy, facesToRender)

val pelvisPos = Vector3(0f, 80f, 0f)
transformAndCollectCuboid(pelvisCuboid, pelvisPos, qBodyY, cx, cy, facesToRender)
~~~

The coordinates are offsets around the View's center, in the app's chosen model coordinate system. Negative Y values place head/neck above the torso in Android screen-space convention, because the later screen mapping adds the model Y value to the screen center.

## 13.2 Transforming a non-articulated cuboid

~~~kotlin
private fun transformAndCollectCuboid(
    cuboid: Cuboid3D,
    centerPos: Vector3,
    bodyQuat: Quaternion,
    cx: Float,
    cy: Float,
    outList: MutableList<TransformedFace>
) {
    val transformedWorldVerts = Array(cuboid.localVertices.size) { i ->
        val rotatedLocal = bodyQuat.rotateVector(cuboid.localVertices[i])
        val worldPos = bodyQuat.rotateVector(centerPos)
        rotatedLocal.add(worldPos)
    }

    projectAndCollectFaces(cuboid, transformedWorldVerts, cx, cy, outList)
}
~~~

For each local vertex:

1. Rotate its local coordinate by the body quaternion.
2. Rotate the body's specified part-center offset by the body quaternion.
3. Add those values to obtain the placed vertex.
4. Project the completed cuboid into screen coordinates and add its faces to the shared render list.

The `Vector3.add()` operation combines the rotated local geometry with its transformed location.

## 13.3 Transforming a limb around a pivot

~~~kotlin
private fun transformAndCollectLimb(
    cuboid: Cuboid3D,
    pivotPos: Vector3,
    offsetFromPivot: Vector3,
    jointQuat: Quaternion,
    bodyQuat: Quaternion,
    cx: Float,
    cy: Float,
    outList: MutableList<TransformedFace>
) {
    val totalQuat = bodyQuat * jointQuat
    val worldPivot = bodyQuat.rotateVector(pivotPos)
    val rotatedOffset = totalQuat.rotateVector(offsetFromPivot)
    val centerPos = worldPivot.add(rotatedOffset)

    val transformedWorldVerts = Array(cuboid.localVertices.size) { i ->
        val rotatedLocal = totalQuat.rotateVector(cuboid.localVertices[i])
        rotatedLocal.add(centerPos)
    }

    projectAndCollectFaces(cuboid, transformedWorldVerts, cx, cy, outList)
}
~~~

This function brings together the main ideas:

- A **pivot** is the point the limb is positioned relative to.
- `bodyQuat` rotates that pivot with the body.
- `totalQuat` composes body and joint orientation.
- `offsetFromPivot` locates the cuboid center relative to the pivot.
- Each local vertex rotates by the composite quaternion and is then translated to the calculated center.

The shoulder, elbow, hip and knee positions are set up in `onDraw()`. Forearms depend on elbow-pivot calculations; lower legs depend on knee-pivot calculations. This gives the robot an approximate articulated structure, but no formal skeleton object, parent/child transform graph, joint limits or inverse-kinematics solver is implemented.

---

# 14. Perspective-like projection from 3D to 2D

## 14.1 Exact implementation

~~~kotlin
private fun projectAndCollectFaces(
    cuboid: Cuboid3D,
    worldVerts: Array<Vector3>,
    cx: Float,
    cy: Float,
    outList: MutableList<TransformedFace>
) {
    val distance = 600f

    val screenVerts = Array(worldVerts.size) { i ->
        val v = worldVerts[i]
        val zPerspective = (distance + v.z) / distance
        val sx = cx + (v.x / zPerspective)
        val sy = cy + (v.y / zPerspective)
        Pair(sx, sy)
    }

    // Collect faces using the cuboid's vertex indices ...
}
~~~

The source uses a simple perspective-like scale factor:

$$
p_z=\frac{d+z}{d}
$$

with $d=600$. Then:

$$
x_{screen}=c_x+\frac{x}{p_z}
$$

$$
y_{screen}=c_y+\frac{y}{p_z}
$$

where $c_x$ and $c_y$ are the center of the custom View.

Because the perspective factor changes with Z, equal X/Y offsets can have different projected distances from the screen center. This is an educational projection formula, **not** a complete camera projection matrix.

### Important limitations of the formula

- There is no explicit camera-space transform or near/far plane clipping in this view.
- The denominator can approach zero when $z$ approaches $-600$, leading to very large projected coordinates. The code does not guard this case.
- Screen Y uses addition, `cy + v.y / zPerspective`, so positive model Y moves down on Android's screen.
- There is no actual 3D depth buffer or back-face culling.

These are the exact characteristics of the submitted implementation and are worth knowing when extending it.

---

# 15. Painter's Algorithm: render far faces first

The View collects a `TransformedFace` with projected vertices, average Z, fill color and stroke color. After the components have been transformed:

~~~kotlin
facesToRender.sortBy { it.avgZ }
~~~

Each face's average depth is computed while collecting it:

~~~kotlin
val avgZ = idxs.map { k -> worldVerts[k].z }.average().toFloat()
val fillColor = cuboid.faceColors[fIdx % cuboid.faceColors.size]
val strokeColor = cuboid.faceColors[0]

outList.add(TransformedFace(face2D, avgZ, fillColor, strokeColor))
~~~

This is an example of the **Painter's Algorithm**:

1. Estimate a depth for each polygon.
2. Sort polygons by that depth.
3. Draw in sorted order so later faces can paint over earlier ones.

The code sorts ascending by average Z, following its comment that it draws from farther Z to nearer Z. This is a coarse approximation: one average value cannot correctly order every possible overlap. Intersecting or complex shapes can still be drawn in the wrong order. A real depth buffer tests depth per pixel.

The sorting does not change any vertices; it only changes drawing order.

---

# 16. Filled and wireframe rendering

## 16.1 Draw a face as a Path

~~~kotlin
for (tf in facesToRender) {
    facePath.reset()
    facePath.moveTo(tf.vertices2D[0].first, tf.vertices2D[0].second)

    for (k in 1 until tf.vertices2D.size) {
        facePath.lineTo(tf.vertices2D[k].first, tf.vertices2D[k].second)
    }
    facePath.close()

    // Filled/wireframe choice follows ...
}
~~~

- `reset()` clears the reusable path from the previous face.
- `moveTo()` establishes the first projected corner.
- `lineTo()` connects each subsequent corner.
- `close()` connects the final corner to the starting corner.

The geometry is already projected into 2D here. The drawing method does not rotate points; it simply uses the projected screen coordinates.

## 16.2 Two display modes

~~~kotlin
if (isWireframeMode) {
    strokePaint.color = tf.strokeColor
    strokePaint.strokeWidth = 4f
    canvas.drawPath(facePath, strokePaint)
} else {
    facePaint.style = Paint.Style.FILL_AND_STROKE
    facePaint.color = tf.fillColor
    canvas.drawPath(facePath, facePaint)

    strokePaint.color = Color.BLACK
    strokePaint.strokeWidth = 2f
    canvas.drawPath(facePath, strokePaint)
}
~~~

- **Filled mode:** draw the face in its palette color, then draw a thinner black outline.
- **Wireframe mode:** draw only the face boundary using the cuboid's base color.

One implementation detail: `strokeColor` is set to `cuboid.faceColors[0]` for every face of that cuboid, so all wireframe edges on a given body part use that cuboid's first palette color rather than each individual face's fill color.

The Paint objects are reusable fields. The code updates their style/color/stroke width as the rendering mode changes rather than creating a new Paint per face.

---

# 17. UI controls — the Activity's exact wiring

## 17.1 Play and pause buttons

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

Play starts a fresh timer after cancelling any prior one. Pause cancels the timer. The current phase value is kept between these actions, so Play continues from that phase rather than resetting.

## 17.2 Wireframe toggle

~~~kotlin
btnToggleWireframe.setOnClickListener {
    robot3DView.isWireframeMode = !robot3DView.isWireframeMode
    robot3DView.invalidate()

    val modeStr =
        if (robot3DView.isWireframeMode) "Wireframe Mode"
        else "Filled & Stroked Colors Mode"

    Toast.makeText(this, "Robot Style: $modeStr", Toast.LENGTH_SHORT).show()
}
~~~

The `!` boolean negation flips the state. `invalidate()` requests a redraw so the next `onDraw()` uses the new path-rendering mode. The Toast is only a temporary Android message to communicate the selected mode.

## 17.3 RadioGroup choreography choice

~~~kotlin
rgDanceStyle.setOnCheckedChangeListener { _, checkedId ->
    when (checkedId) {
        R.id.rbFullDisco ->
            robot3DView.currentDanceStyle = Robot3DView.DanceStyle.FULL_DISCO
        R.id.rbMarching ->
            robot3DView.currentDanceStyle = Robot3DView.DanceStyle.MARCHING
        R.id.rbSideSwing ->
            robot3DView.currentDanceStyle = Robot3DView.DanceStyle.ARM_SIDE_SWING
        R.id.rbDiagonal ->
            robot3DView.currentDanceStyle = Robot3DView.DanceStyle.DIAGONAL_WAVE
    }
    robot3DView.invalidate()
}
~~~

The layout IDs map to the enum. This avoids relying on the human-readable button labels as application state. The render method uses `currentDanceStyle` to decide how to construct the arm and leg quaternions.

## 17.4 Speed SeekBar

~~~kotlin
sbDanceSpeed.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
    val scale = progress.coerceAtLeast(10) / 100f
    tvDanceSpeedLabel.text =
        String.format(Locale.US, "Dance Tempo / Speed: %.2fx", scale)
    robot3DView.danceSpeedScale = scale
})
~~~

The slider's XML `max` is 250 and its initial `progress` is 100. The transformation is:

$$
speedScale=\frac{\max(progress,10)}{100}
$$

Thus the initial setting is 1.0×, and the effective range is approximately 0.1× through 2.5×. Division by `100f` ensures floating-point arithmetic; dividing by the integer `100` would change the result type/precision behavior.

The common listener helper calls the supplied lambda only when `fromUser` is true. That prevents programmatic SeekBar changes from being interpreted as user-driven changes.

---

# 18. The robot XML layout

**File:** `fragment_robot_3d.xml`

The screen has three principal regions:

1. A colored status header (`tvRobotStatus`).
2. A flexible `FrameLayout` holding `Robot3DView`.
3. A fixed-height `ScrollView` holding playback buttons, the choreography RadioGroup and speed SeekBar.

The canvas section uses:

~~~xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1"
    android:background="#FFFFFF">

    <com.example.test.Robot3DView
        android:id="@+id/robot3DView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
~~~

A height of `0dp` together with `layout_weight="1"` assigns the remaining vertical space to the drawing surface after the header and control panel are measured. The ScrollView lets the controls remain reachable on smaller displays.

The layout defines these main IDs:

- `btnRobotPlay`
- `btnRobotPause`
- `btnToggleWireframe`
- `rgDanceStyle`
- `rbFullDisco`, `rbMarching`, `rbSideSwing`, `rbDiagonal`
- `sbDanceSpeed`
- `tvDanceSpeedLabel`

The Activity's `findViewById()` calls must match these XML IDs exactly.

---

# 19. Kotlin features and data structures used

| Concept | Location | Why it is useful |
|---|---|---|
| Data classes | `Vector3`, `Quaternion`, `TransformedFace` | Gives related values a named structure |
| Enum | `DanceStyle` | Explicit finite list of choreography modes |
| Operator overloading | `operator fun times` | Lets `q1 * q2` represent quaternion multiplication |
| Arrays | `localVertices`, `faceIndices`, `faceColors` | Compact storage for fixed-size geometry and topology |
| Lists | `facesToRender` | Collects faces from every robot component |
| Mapping | `idxs.map { ... }` | Extracts depths for all vertex indices in a face |
| Sorting | `sortBy { it.avgZ }` | Establishes the drawing order |
| Lambda callbacks | Click/RadioGroup/SeekBar listeners | Connects controls to rendering state |
| Timer/TimerTask | `startDanceTimer()` | Advances time and requests redraws |
| Trigonometry | `sin()`, `cos()` | Produces smooth cyclic joint trajectories |
| Reusable graphics objects | `Paint`, `Path` | Holds drawing configuration and current face geometry |
| Defensive numeric guard | `len < 1e-6f` | Prevents normalizing an almost-zero axis |

## DSA fundamentals (without overstating them)

This implementation does not use BFS, DFS, pathfinding, search trees, or an advanced robotics planner. The DSA-related pieces that are really present are:

- **Arrays and indexed loops:** vertices and faces are stored in arrays and traversed by index.
- **List accumulation:** every cuboid contributes its face records to one mutable list.
- **Mapping:** face index arrays are mapped to projected points or Z coordinates.
- **Sorting:** faces are sorted by a scalar average depth.
- **Fixed-size numerical computation:** each vector/quaternion rotation performs a constant number of arithmetic operations.

If there are (C) cuboids and six faces per cuboid, the renderer collects approximately (6C) faces. If (F) is the total number of faces, sorting them has typical (O(F\log F)) complexity. Each vertex rotation uses a fixed number of arithmetic operations, so transforming all vertices is linear in the number of vertices. Here the model is small, with twelve cuboids.

---

# 20. Quaternion unit tests

**File:** `QuaternionMathTest.kt`

The submitted test class contains four test cases:

### Test 1 — identity

Confirms `Quaternion.identity()` leaves the vector $(10,-20,30)$ unchanged, checking each component within 0.001.

### Test 2 — 90° around Y

Rotates $(100,0,0)$ around Y and expects approximately $(0,0,-100)$. This checks both the rotation magnitude and the source's sign convention.

### Test 3 — 90° around X

Rotates $(0,100,0)$ around X and expects approximately $(0,0,100)$.

### Test 4 — diagonal axis rotation

Creates a 45° quaternion around $(1,1,0)$ and confirms:

$$
x'^2+y'^2+z'^2\approx x^2+y^2+z^2
$$

For the test input $(0,100,0)$, squared vector length should remain approximately $10000$. This validates magnitude preservation within a tolerance of 1.0; it does not assert every expected rotated component for the diagonal case.

These tests check the math helper. They do not instrument an Android View, inspect rendered pixels, verify animation timing, or prove that the overall articulated pose is anatomically/physically accurate.


## Test evidence from the uploaded ZIP

The supplied archive includes Gradle XML reports timestamped **2026-10-11 02:07:58 UTC**. The six suites report:

| Test suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| `AffineMatrixTest` | 3 | 0 | 0 |
| `CameraProjectionMatrixTest` | 3 | 0 | 0 |
| `ExampleUnitTest` | 1 | 0 | 0 |
| `Homogeneous3DMatrixTest` | 2 | 0 | 0 |
| `PolygonLogicTest` | 2 | 0 | 0 |
| `QuaternionMathTest` | 4 | 0 | 0 |
| **Total** | **15** | **0** | **0** |

These are results recorded inside the uploaded ZIP. I also attempted a fresh run with `./gradlew testDebugUnitTest --offline --no-daemon`; the wrapper still tried to download Gradle 9.4.1 and failed because `services.gradle.org` could not be resolved in this environment. So the table reports the archive's saved test results, not a newly successful test execution during this repository update.


---

# 21. Rendering and lifecycle limitations worth studying

These are the important constraints visible in the source as uploaded.

1. **Painter's Algorithm is approximate.** One average Z value per face can order intersecting/overlapping surfaces incorrectly. The renderer has no per-pixel depth buffer.
2. **Projection can approach a singularity.** `zPerspective=(600+z)/600` is used as a divisor with no guard or geometric clipping.
3. **No true perspective camera matrix.** The View uses a compact projection-like equation, not the same explicit 4×4 projection pipeline used in the projection module.
4. **No lighting/normal computation.** Colors are variants of a base color rather than the result of surface normals and light sources.
5. **No physical robot or physics simulation.** Motion is trigonometric choreography rendered on Canvas, with no servo commands, sensors, collision system or inverse kinematics.
6. **Articulated transforms are approximations.** The part positions/pivots are manually specified. There is no reusable skeleton or joint-limit system.
7. **Hidden layout may still animate.** The robot's timer starts in the View's constructor. Setting its parent layout to `GONE` does not necessarily call `onDetachedFromWindow()`, so navigation can leave the timer running when the robot screen is hidden.
8. **Timer lifetime follows View detachment, not Activity pause/resume.** The override cancels the timer on detach, but the code does not coordinate app backgrounding or navigation visibility.
9. **Animation state is not synchronized explicitly.** The timer updates `danceTime` and can access `danceSpeedScale` from a Timer thread while rendering reads those fields on the UI thread.
10. **Play resumes the phase.** Stop cancels the timer but never resets `danceTime`. The module has no explicit reset choreography button.
11. **Wireframe edge color is per cuboid.** It uses `faceColors[0]` as every face's stroke color for that body component.
12. **Not a full scene graph.** The hierarchy is calculated manually in `onDraw()`, instead of represented as persistent parent-child nodes.

These constraints are documented deliberately so future extensions can build on the sample without confusing it with a complete 3D engine.

---

# 22. Practical exercises

1. Change body yaw amplitude from 45° to 25°. Observe the smaller body oscillation.
2. Change the marching amplitude from 35° to 50°. Observe how far the legs rotate.
3. Change `2.5f` to `1.0f` in the leg phase expression and compare the motion speed.
4. Remove the opposite sign from `qLegRightX) (in a separate experiment) and observe the difference between alternating and same-direction leg motion.
5. Replace a diagonal axis ((1,1,0)) with ((0,0,1)) and compare its motion direction.
6. Change the order of `qArmLeftZ * qDiagLeft * qLegRightX` and observe that the composite rotation changes.
7. Add a small face-color adjustment, then inspect the wireframe color behavior.
8. Pause the animation, wait, and Play again. Notice that `danceTime` resumes rather than restarting.
9. Improve lifecycle handling so the timer runs only when the robot section is visible and the Activity is resumed.
10. Add a projection denominator guard or a near-plane clipping strategy before experimenting with more extreme depth values.
11. Extend quaternion tests with a zero-axis test and a vector-length-preservation test for random/representative axes.
12. Separate the pose update from Canvas drawing so that animation state is easier to unit-test without an Android graphics environment.

---

# 23. Recommended study order

1. Read `Vector3` and understand what each component represents.
2. Read `Quaternion.fromAxisAngle()` and derive the half-angle formula.
3. Manually calculate the quaternion for a 90° Y rotation.
4. Read `rotateVector()` and connect its two cross products to the vector-rotation formula.
5. Study quaternion multiplication and why the order matters.
6. Read `Cuboid3D` and sketch its eight local vertices/six faces.
7. Follow one call to `transformAndCollectLimb()` from pivot to final projected vertices.
8. Derive the sinusoidal choreography functions and their angle amplitudes.
9. Follow the timer → `danceTime` → `postInvalidate()` → `onDraw()` path.
10. Study face-depth sorting and then inspect the render loop.
11. Follow each XML ID to the corresponding Activity callback.
12. Run the four quaternion math tests, then extend them before making large geometry changes.

**Learning goal:** understand how a quaternion encodes rotation, how composite rotations place the robot's articulated parts, how the view projects faces to Android Canvas, and how the UI drives animation state.
