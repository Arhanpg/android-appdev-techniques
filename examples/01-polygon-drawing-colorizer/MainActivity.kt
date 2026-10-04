package com.example.test

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {

    private lateinit var polygonDrawingView: PolygonDrawingView
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        polygonDrawingView = findViewById(R.id.polygonDrawingView)
        tvStatus = findViewById(R.id.tvStatus)

        val btnClosePolygon: Button = findViewById(R.id.btnClosePolygon)
        val btnUndo: Button = findViewById(R.id.btnUndo)
        val btnClear: Button = findViewById(R.id.btnClear)

        val btnColorBlue: Button = findViewById(R.id.btnColorBlue)
        val btnColorRed: Button = findViewById(R.id.btnColorRed)
        val btnColorGreen: Button = findViewById(R.id.btnColorGreen)
        val btnColorYellow: Button = findViewById(R.id.btnColorYellow)
        val btnColorPurple: Button = findViewById(R.id.btnColorPurple)
        val btnColorOrange: Button = findViewById(R.id.btnColorOrange)

        polygonDrawingView.onPolygonStateChangedListener = { isClosed, pointCount ->
            updateStatusText(isClosed, pointCount)
        }

        btnClosePolygon.setOnClickListener {
            when {
                polygonDrawingView.isPolygonClosed() -> Toast.makeText(
                    this,
                    "Polygon is already closed and colored",
                    Toast.LENGTH_SHORT
                ).show()

                polygonDrawingView.getPointCount() < 3 -> Toast.makeText(
                    this,
                    "Need at least 3 points to close a polygon",
                    Toast.LENGTH_SHORT
                ).show()

                else -> {
                    polygonDrawingView.closePolygon()
                    Toast.makeText(
                        this,
                        "Polygon closed and colored!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnUndo.setOnClickListener { polygonDrawingView.undoLastPoint() }
        btnClear.setOnClickListener { polygonDrawingView.clear() }

        btnColorBlue.setOnClickListener { setSemiTransparentColor("#1E88E5") }
        btnColorRed.setOnClickListener { setSemiTransparentColor("#E53935") }
        btnColorGreen.setOnClickListener { setSemiTransparentColor("#4CAF50") }
        btnColorYellow.setOnClickListener { setSemiTransparentColor("#FBC02D") }
        btnColorPurple.setOnClickListener { setSemiTransparentColor("#8E24AA") }
        btnColorOrange.setOnClickListener { setSemiTransparentColor("#FB8C00") }

        updateStatusText(
            polygonDrawingView.isPolygonClosed(),
            polygonDrawingView.getPointCount()
        )
    }

    private fun setSemiTransparentColor(hexColor: String) {
        val baseColor = hexColor.toColorInt()

        val semiTransparentColor = Color.argb(
            150,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )

        polygonDrawingView.setFillColor(semiTransparentColor)

        if (polygonDrawingView.isPolygonClosed()) {
            Toast.makeText(
                this,
                "Polygon fill color updated!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateStatusText(isClosed: Boolean, pointCount: Int) {
        tvStatus.text = when {
            isClosed ->
                "Polygon CLOSED and COLORED (" + pointCount + " vertices). Pick a color below!"

            pointCount == 0 ->
                "Tap on the screen to add vertices."

            pointCount < 3 ->
                "Added " + pointCount + " points. Add at least " +
                    (3 - pointCount) + " more point(s) to form a polygon."

            else ->
                "Added " + pointCount +
                    " points. Tap 'Close Polygon' or tap near starting green ring to seal shape."
        }
    }
}
