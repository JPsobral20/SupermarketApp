package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.nio.charset.StandardCharsets
import java.util.*

// Class for the QR code cart tag page
class QRTagCartActivity : AppCompatActivity() {
    private val qrCodeImageview by lazy { findViewById<ImageView>(R.id.img_qr_code) }
    private val tvError by lazy { findViewById<TextView>(R.id.tv_error) }
    private val tvPrice by lazy { findViewById<TextView>(R.id.tv_price_value) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        //setStatusBarIconColor(window, Lightness.LIGHT)
        setContentView(R.layout.activity_qrcode_tag_checkout)
        //setInsetsPadding(tvError, bottom=0)

        val menuButton = findViewById<Button>(R.id.menuButton)
        menuButton.setOnClickListener {
            val intent = Intent(this, HomePageActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        val content = intent.getByteArrayExtra("data")!!
        val qrContent = String(content, StandardCharsets.ISO_8859_1)

        val price = intent.getStringExtra("price") ?: "—"
        Thread {                         // do the creation in a new thread to avoid ANR Exception
            try {
                val bitmap = encodeAsBitmap(qrContent)
                runOnUiThread {   // runOnUiThread method used to do UI task in main thread
                    tvPrice.text = price
                    qrCodeImageview.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                tvError.text = e.message
            }
        }.start()
    }

    // Function to encode the QR Code
    private fun encodeAsBitmap(str: String): Bitmap? {
        val DIMENSION = 1000

        val hints = Hashtable<EncodeHintType, String>().also {
            it[EncodeHintType.CHARACTER_SET] = "ISO-8859-1"
        }
        val result = try {
            MultiFormatWriter().encode(str, BarcodeFormat.QR_CODE, DIMENSION, DIMENSION, hints)
        } catch (iae: IllegalArgumentException) {
            tvError.text = iae.message
            return null
        }
        val w = result.getWidth()
        val h = result.getHeight()
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (result.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }
}