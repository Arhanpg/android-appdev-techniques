package com.example.test

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var topAppBar: MaterialToolbar

    private lateinit var layoutBasic: View
    private lateinit var layoutAdvanced: View
    private lateinit var layoutAffine: View
    private lateinit var layoutCanvasGraphs: View
    private lateinit var layoutCube3D: View

    // Basic Section Views
    private lateinit var polygonDrawingView: PolygonDrawingView
    private lateinit var tvStatus: TextView
    private lateinit var btnClosePolygon: Button
    private lateinit var btnUndo: Button
    private lateinit var btnClear: Button

    // Advanced Styling Section Views
    private lateinit var advancedStylingView: AdvancedStylingView
    private lateinit var tvAdvancedStatus: TextView
    private lateinit var btnAdvClosePolygon: Button
    private lateinit var btnAdvUndo: Button
    private lateinit var btnAdvClear: Button
    private lateinit var rgFillMode: RadioGroup
    private lateinit var rgShaderType: RadioGroup
    private lateinit var rgTexturePattern: RadioGroup
    private lateinit var layoutTextureOptions: LinearLayout
    private lateinit var sbStrokeWidth: SeekBar
    private lateinit var tvStrokeWidthLabel: TextView

    
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

    // Affine Section Views
    private lateinit var affineTransformView: AffineTransformView
    private lateinit var tvAffineStatus: TextView
    private lateinit var btnAffineClose: Button
    private lateinit var btnResetTransform: Button
    private lateinit var btnAffineClear: Button
    private lateinit var tvMatrixDisplay: TextView
    private lateinit var sbTranslateX: SeekBar
    private lateinit var sbTranslateY: SeekBar
    private lateinit var sbRotation: SeekBar
    private lateinit var sbScaleX: SeekBar
    private lateinit var sbScaleY: SeekBar
    private lateinit var sbSkewX: SeekBar
    private lateinit var sbSkewY: SeekBar
    private lateinit var tvTranslateX: TextView
    private lateinit var tvTranslateY: TextView
    private lateinit var tvRotation: TextView
    private lateinit var tvScaleX: TextView
    private lateinit var tvScaleY: TextView
    private lateinit var tvSkewX: TextView
    private lateinit var tvSkewY: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)

        layoutBasic = findViewById(R.id.layoutBasic)
        layoutAdvanced = findViewById(R.id.layoutAdvanced)
        layoutAffine = findViewById(R.id.layoutAffine)
        layoutCanvasGraphs = findViewById(R.id.layoutCanvasGraphs)
        layoutCube3D = findViewById(R.id.layoutCube3D)

        setupNavigationDrawer()
        setupBasicSection()
        setupAdvancedSection()
        setupAffineSection()
        setupCanvasGraphsSection()
        setupCube3DSection()
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, topAppBar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        topAppBar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_basic -> {
                    showSection(layoutBasic, "Basic Polygon Drawer")
                }
                R.id.nav_advanced_styling -> {
                    showSection(layoutAdvanced, "Advanced Color & Fill Styling")
                }
                R.id.nav_affine_transform -> {
                    showSection(layoutAffine, "Affine Matrix Transformations")
                }
                R.id.nav_canvas_graphs -> {
                    showSection(layoutCanvasGraphs, "2D Canvas Graphs (Arc, Rect, Path)")
                }
                R.id.nav_cube_3d -> {
                    showSection(layoutCube3D, "3D Homogeneous Cube & Matrix")
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showSection(targetLayout: View, title: String) {
        layoutBasic.visibility = View.GONE
        layoutAdvanced.visibility = View.GONE
        layoutAffine.visibility = View.GONE
        layoutCanvasGraphs.visibility = View.GONE
        layoutCube3D.visibility = View.GONE

        targetLayout.visibility = View.VISIBLE
        topAppBar.title = title
    }

    private fun setupBasicSection() {
        polygonDrawingView = layoutBasic.findViewById(R.id.polygonDrawingView)
        tvStatus = layoutBasic.findViewById(R.id.tvStatus)
        btnClosePolygon = layoutBasic.findViewById(R.id.btnClosePolygon)
        btnUndo = layoutBasic.findViewById(R.id.btnUndo)
        btnClear = layoutBasic.findViewById(R.id.btnClear)

        polygonDrawingView.onPolygonStateChangedListener = { isClosed, pointCount ->
            if (isClosed) {
                tvStatus.text = "Polygon CLOSED & COLORED ($pointCount vertices)."
            } else if (pointCount == 0) {
                tvStatus.text = "Tap on screen to add vertices."
            } else {
                tvStatus.text = "Vertices: $pointCount. Min 3 required to close polygon."
            }
        }

        btnClosePolygon.setOnClickListener {
            if (polygonDrawingView.isPolygonClosed()) {
                Toast.makeText(this, "Polygon already closed", Toast.LENGTH_SHORT).show()
            } else if (!polygonDrawingView.closePolygon()) {
                Toast.makeText(this, "Need at least 3 points to close polygon", Toast.LENGTH_SHORT).show()
            }
        }

        btnUndo.setOnClickListener { polygonDrawingView.undoLastPoint() }
        btnClear.setOnClickListener { polygonDrawingView.clear() }

        val btnColorBlue: Button = layoutBasic.findViewById(R.id.btnColorBlue)
        val btnColorRed: Button = layoutBasic.findViewById(R.id.btnColorRed)
        val btnColorGreen: Button = layoutBasic.findViewById(R.id.btnColorGreen)
        val btnColorYellow: Button = layoutBasic.findViewById(R.id.btnColorYellow)
        val btnColorPurple: Button = layoutBasic.findViewById(R.id.btnColorPurple)
        val btnColorOrange: Button = layoutBasic.findViewById(R.id.btnColorOrange)

        btnColorBlue.setOnClickListener { setBasicColor("#1E88E5") }
        btnColorRed.setOnClickListener { setBasicColor("#E53935") }
        btnColorGreen.setOnClickListener { setBasicColor("#4CAF50") }
        btnColorYellow.setOnClickListener { setBasicColor("#FBC02D") }
        btnColorPurple.setOnClickListener { setBasicColor("#8E24AA") }
        btnColorOrange.setOnClickListener { setBasicColor("#FB8C00") }
    }

    private fun setBasicColor(hex: String) {
        val c = hex.toColorInt()
        polygonDrawingView.setFillColor(
            Color.argb(150, Color.red(c), Color.green(c), Color.blue(c))
        )
    }

    private fun setupAdvancedSection() {
        advancedStylingView = layoutAdvanced.findViewById(R.id.advancedStylingView)
        tvAdvancedStatus = layoutAdvanced.findViewById(R.id.tvAdvancedStatus)
        btnAdvClosePolygon = layoutAdvanced.findViewById(R.id.btnAdvClosePolygon)
        btnAdvUndo = layoutAdvanced.findViewById(R.id.btnAdvUndo)
        btnAdvClear = layoutAdvanced.findViewById(R.id.btnAdvClear)
        rgFillMode = layoutAdvanced.findViewById(R.id.rgFillMode)
        rgShaderType = layoutAdvanced.findViewById(R.id.rgShaderType)
        rgTexturePattern = layoutAdvanced.findViewById(R.id.rgTexturePattern)
        layoutTextureOptions = layoutAdvanced.findViewById(R.id.layoutTextureOptions)
        sbStrokeWidth = layoutAdvanced.findViewById(R.id.sbStrokeWidth)
        tvStrokeWidthLabel = layoutAdvanced.findViewById(R.id.tvStrokeWidthLabel)

        advancedStylingView.onPolygonStateChangedListener = { isClosed, count ->
            if (isClosed) {
                tvAdvancedStatus.text =
                    "Advanced Polygon CLOSED ($count vertices). Adjust Fill, Shader & Stroke below!"
            } else {
                tvAdvancedStatus.text =
                    "Vertices: $count. Tap screen to add points, tap green ring to close."
            }
        }

        btnAdvClosePolygon.setOnClickListener {
            if (!advancedStylingView.closePolygon()) {
                Toast.makeText(this, "Need at least 3 points to close", Toast.LENGTH_SHORT).show()
            }
        }

        btnAdvUndo.setOnClickListener { advancedStylingView.undoLastPoint() }
        btnAdvClear.setOnClickListener { advancedStylingView.clear() }

        rgFillMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbFillAndStroke ->
                    advancedStylingView.currentFillMode =
                        AdvancedStylingView.FillMode.FILL_AND_STROKE
                R.id.rbFillOnly ->
                    advancedStylingView.currentFillMode =
                        AdvancedStylingView.FillMode.FILL
                R.id.rbStrokeOnly ->
                    advancedStylingView.currentFillMode =
                        AdvancedStylingView.FillMode.STROKE
            }
            advancedStylingView.invalidate()
        }

        rgShaderType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbLinearGradient -> {
                    advancedStylingView.currentShaderType =
                        AdvancedStylingView.ShaderType.LINEAR_GRADIENT
                    layoutTextureOptions.visibility = View.GONE
                }
                R.id.rbRadialGradient -> {
                    advancedStylingView.currentShaderType =
                        AdvancedStylingView.ShaderType.RADIAL_GRADIENT
                    layoutTextureOptions.visibility = View.GONE
                }
                R.id.rbSweepGradient -> {
                    advancedStylingView.currentShaderType =
                        AdvancedStylingView.ShaderType.SWEEP_GRADIENT
                    layoutTextureOptions.visibility = View.GONE
                }
                R.id.rbTexturePattern -> {
                    advancedStylingView.currentShaderType =
                        AdvancedStylingView.ShaderType.TEXTURE_PATTERN
                    layoutTextureOptions.visibility = View.VISIBLE
                }
                R.id.rbSolid -> {
                    advancedStylingView.currentShaderType =
                        AdvancedStylingView.ShaderType.SOLID
                    layoutTextureOptions.visibility = View.GONE
                }
            }
            advancedStylingView.invalidate()
        }

        rgTexturePattern.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbCheckerboard ->
                    advancedStylingView.currentTexturePattern =
                        AdvancedStylingView.TexturePattern.CHECKERBOARD
                R.id.rbHatch ->
                    advancedStylingView.currentTexturePattern =
                        AdvancedStylingView.TexturePattern.DIAGONAL_HATCH
                R.id.rbDots ->
                    advancedStylingView.currentTexturePattern =
                        AdvancedStylingView.TexturePattern.POLKA_DOTS
                R.id.rbBrick ->
                    advancedStylingView.currentTexturePattern =
                        AdvancedStylingView.TexturePattern.BRICK_WALL
            }
            advancedStylingView.invalidate()
        }

        sbStrokeWidth.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val width = progress.coerceAtLeast(1)
                    tvStrokeWidthLabel.text = "Stroke Width: ${width}px"
                    advancedStylingView.strokeWidthPx = width.toFloat()
                    advancedStylingView.invalidate()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
    }

    private fun setupAffineSection() {
        affineTransformView = layoutAffine.findViewById(R.id.affineTransformView)
        tvAffineStatus = layoutAffine.findViewById(R.id.tvAffineStatus)
        btnAffineClose = layoutAffine.findViewById(R.id.btnAffineClose)
        btnResetTransform = layoutAffine.findViewById(R.id.btnResetTransform)
        btnAffineClear = layoutAffine.findViewById(R.id.btnAffineClear)
        tvMatrixDisplay = layoutAffine.findViewById(R.id.tvMatrixDisplay)

        sbTranslateX = layoutAffine.findViewById(R.id.sbTranslateX)
        sbTranslateY = layoutAffine.findViewById(R.id.sbTranslateY)
        sbRotation = layoutAffine.findViewById(R.id.sbRotation)
        sbScaleX = layoutAffine.findViewById(R.id.sbScaleX)
        sbScaleY = layoutAffine.findViewById(R.id.sbScaleY)
        sbSkewX = layoutAffine.findViewById(R.id.sbSkewX)
        sbSkewY = layoutAffine.findViewById(R.id.sbSkewY)

        tvTranslateX = layoutAffine.findViewById(R.id.tvTranslateX)
        tvTranslateY = layoutAffine.findViewById(R.id.tvTranslateY)
        tvRotation = layoutAffine.findViewById(R.id.tvRotation)
        tvScaleX = layoutAffine.findViewById(R.id.tvScaleX)
        tvScaleY = layoutAffine.findViewById(R.id.tvScaleY)
        tvSkewX = layoutAffine.findViewById(R.id.tvSkewX)
        tvSkewY = layoutAffine.findViewById(R.id.tvSkewY)

        affineTransformView.onPolygonStateChangedListener = { isClosed, count ->
            if (isClosed) {
                tvAffineStatus.text =
                    "Polygon CLOSED ($count vertices). Use sliders below for Matrix transformations!"
            } else {
                tvAffineStatus.text =
                    "Vertices: $count. Draw polygon, then close to enable Matrix transformations."
            }
        }

        affineTransformView.onMatrixUpdatedListener = { matrix ->
            updateMatrixTextDisplay(matrix)
        }

        btnAffineClose.setOnClickListener {
            if (!affineTransformView.closePolygon()) {
                Toast.makeText(this, "Need at least 3 points to close", Toast.LENGTH_SHORT).show()
            }
        }

        btnResetTransform.setOnClickListener {
            affineTransformView.resetTransformations()
            sbTranslateX.progress = 300
            sbTranslateY.progress = 300
            sbRotation.progress = 0
            sbScaleX.progress = 100
            sbScaleY.progress = 100
            sbSkewX.progress = 150
            sbSkewY.progress = 150
            Toast.makeText(this, "Matrix Reset to Identity", Toast.LENGTH_SHORT).show()
        }

        btnAffineClear.setOnClickListener {
            affineTransformView.clear()
            btnResetTransform.performClick()
        }

        sbTranslateX.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
            val tx = (progress - 300).toFloat()
            tvTranslateX.text = "Translate X: ${tx.toInt()} px"
            affineTransformView.translateX = tx
            affineTransformView.invalidate()
        })

        sbTranslateY.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
            val ty = (progress - 300).toFloat()
            tvTranslateY.text = "Translate Y: ${ty.toInt()} px"
            affineTransformView.translateY = ty
            affineTransformView.invalidate()
        })

        sbRotation.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
            tvRotation.text = "Rotate Angle: ${progress}°"
            affineTransformView.rotationDegrees = progress.toFloat()
            affineTransformView.invalidate()
        })

        sbScaleX.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
            val sx = progress.coerceAtLeast(10) / 100f
            tvScaleX.text = String.format(Locale.US, "Scale X: %.2fx", sx)
            affineTransformView.affineScaleX = sx
            affineTransformView.invalidate()
        })

        sbScaleY.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
            val sy = progress.coerceAtLeast(10) / 100f
            tvScaleY.text = String.format(Locale.US, "Scale Y: %.2fx", sy)
            affineTransformView.affineScaleY = sy
            affineTransformView.invalidate()
        })

        sbSkewX.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
            val kx = (progress - 150) / 100f
            tvSkewX.text = String.format(Locale.US, "Shear X: %.2f", kx)
            affineTransformView.skewX = kx
            affineTransformView.invalidate()
        })

        sbSkewY.setOnSeekBarChangeListener(createSeekBarChangeListener { progress ->
            val ky = (progress - 150) / 100f
            tvSkewY.text = String.format(Locale.US, "Shear Y: %.2f", ky)
            affineTransformView.skewY = ky
            affineTransformView.invalidate()
        })
    }


    // --- CANVAS GRAPHS SECTION SETUP ---
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
                R.id.rbGraphAll -> canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.ALL
                R.id.rbGraphArc -> canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.ARC_PIE
                R.id.rbGraphRect -> canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.RECT_BAR
                R.id.rbGraphPath -> canvasGraphsView.currentGraphType = CanvasGraphsView.GraphType.PATH_LINE
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

    // --- 3D CUBE SECTION SETUP ---
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

    private fun createSeekBarChangeListener(
        onProgressChanged: (progress: Int) -> Unit
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) onProgressChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun updateMatrixTextDisplay(m: FloatArray) {
        val format =
            "[ %6.2f  %6.2f  %6.2f ]\n[ %6.2f  %6.2f  %6.2f ]\n[ %6.2f  %6.2f  %6.2f ]"

        tvMatrixDisplay.text = String.format(
            Locale.US,
            format,
            m[0], m[1], m[2],
            m[3], m[4], m[5],
            m[6], m[7], m[8]
        )
    }

    private fun updateMatrix4TextDisplay(m: FloatArray) {
        val format = "[ %5.2f %5.2f %5.2f %5.2f ]\n[ %5.2f %5.2f %5.2f %5.2f ]\n[ %5.2f %5.2f %5.2f %5.2f ]\n[ %5.2f %5.2f %5.2f %5.2f ]"
        tvMatrix4Display.text = String.format(
            Locale.US, format,
            m[0], m[1], m[2], m[3],
            m[4], m[5], m[6], m[7],
            m[8], m[9], m[10], m[11],
            m[12], m[13], m[14], m[15]
        )
    }

}
