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
    private lateinit var btnClosePolygon: Button
    private lateinit var btnUndo: Button
    private lateinit var btnClear: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        polygonDrawingView = findViewById(R.id.polygonDrawingView)
        tvStatus = findViewById(R.id.tvStatus)
        btnClosePolygon = findViewById(R.id.btnClosePolygon)
        btnUndo = findViewById(R.id.btnUndo)
        btnClear = findViewById(R.id.btnClear)

        val btnColorBlue: Button = findViewById(R.id.btnColorBlue)
        val btnColorRed: Button = findViewById(R.id.btnColorRed)
        val btnColorGreen: Button = findViewById(R.id.btnColorGreen)
        val btnColorYellow: Button = findViewById(R.id.btnColorYellow)
        val btnColorPurple: Button = findViewById(R.id.btnColorPurple)
        val btnColorOrange: Button = findViewById(R.id.btnColorOrange)

        // Status listener
        polygonDrawingView.onPolygonStateChangedListener = { isClosed, pointCount ->
            updateStatusText(isClosed, pointCount)
        }

        // Close polygon button
        btnClosePolygon.setOnClickListener {
            if (polygonDrawingView.isPolygonClosed()) {
                Toast.makeText(this, "Polygon is already closed and colored", Toast.LENGTH_SHORT).show()
            } else if (polygonDrawingView.getPointCount() < 3) {
                Toast.makeText(this, "Need at least 3 points to close a polygon", Toast.LENGTH_SHORT).show()
            } else {
                polygonDrawingView.closePolygon()
                Toast.makeText(this, "Polygon closed and colored!", Toast.LENGTH_SHORT).show()
            }
        }

        // Undo button
        btnUndo.setOnClickListener {
            polygonDrawingView.undoLastPoint()
        }

        // Clear button
        btnClear.setOnClickListener {
            polygonDrawingView.clear()
        }

        // Color selector setup (semi-transparent fill)
        btnColorBlue.setOnClickListener { setSemiTransparentColor("#1E88E5") }
        btnColorRed.setOnClickListener { setSemiTransparentColor("#E53935") }
        btnColorGreen.setOnClickListener { setSemiTransparentColor("#4CAF50") }
        btnColorYellow.setOnClickListener { setSemiTransparentColor("#FBC02D") }
        btnColorPurple.setOnClickListener { setSemiTransparentColor("#8E24AA") }
        btnColorOrange.setOnClickListener { setSemiTransparentColor("#FB8C00") }

        updateStatusText(polygonDrawingView.isPolygonClosed(), polygonDrawingView.getPointCount())
    }

    private fun setSemiTransparentColor(hexColor: String) {
        val baseColor = hexColor.toColorInt()
        val semiTransparentColor = Color.argb(150, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        polygonDrawingView.setFillColor(semiTransparentColor)
        if (polygonDrawingView.isPolygonClosed()) {
            Toast.makeText(this, "Polygon fill color updated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusText(isClosed: Boolean, pointCount: Int) {
        if (isClosed) {
            tvStatus.text = "Polygon CLOSED and COLORED ($pointCount vertices). Pick a color below!"
        } else if (pointCount == 0) {
            tvStatus.text = "Tap on the screen to add vertices."
        } else if (pointCount < 3) {
            tvStatus.text = "Added " + pointCount + " points. Add at least " + (3 - pointCount) + " more point(s) to form a polygon."
        } else {
            tvStatus.text = "Added " + pointCount + " points. Tap 'Close Polygon' or tap near starting green ring to seal shape."
        }
    }
}
