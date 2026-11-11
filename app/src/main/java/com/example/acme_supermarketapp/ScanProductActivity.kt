package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher

// Class used for the scan product page
class ScanProductActivity : AppCompatActivity() {
    private val shoppingList = mutableListOf<Product>()
    private val tvTitle by lazy { findViewById<TextView>(R.id.tv_title) }
    private val tvText by lazy { findViewById<TextView>(R.id.tv_text) }
    private var decodedId: UUID? = null

    //SuperMarket Public Key
    private val publicKey = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAMqxIvVOUAmWyjfB2AtEPiECuwo+5U75cPwwVv75i2qaW3E26Q4CGpdsRvCOgCgQmaBo/OAh6kaJYibvnMr2sksCAwEAAQ=="
    private fun getPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.DEFAULT) // Para Android
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    private val pub = getPublicKey(publicKey)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_scan_product)

        val scanButton: Button = findViewById(R.id.scanButton)
        scanButton.setOnClickListener {
            scanQRCode()
        }

        val backButton = findViewById<Button>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    // Function to initiate the scan of the QR code
    private fun scanQRCode() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setBeepEnabled(false)
            setOrientationLocked(true)
        }
        scanCodeLauncher.launch(options)
    }

    // Function to handle the result of the QR code scan
    private val scanCodeLauncher = registerForActivityResult(ScanContract()) {
        if (it != null && it.contents != null) {
            val result = it.contents
            decodeAndShow(result.toByteArray(StandardCharsets.ISO_8859_1))
        }
    }

    // Function to decode and show the contents of the QR code
    private fun decodeAndShow(encTag: ByteArray) {
        val clearTextTag: ByteArray

        try {
            clearTextTag = Cipher.getInstance(Constants.ENC_ALGO).run {
                init(Cipher.DECRYPT_MODE, pub)
                doFinal(encTag)
            }
        } catch (e: Exception) {
            tvTitle.text = e.message
            return
        }
        val tag = ByteBuffer.wrap(clearTextTag)
        val tId = tag.int
        val id = UUID(tag.long, tag.long)
        decodedId = id
        val euros = tag.short.toInt()
        val cents = tag.get().toInt()
        val bName = ByteArray(tag.get().toInt())
        tag[bName]
        val name = String(bName, StandardCharsets.ISO_8859_1)
        val strCents = String.format("%02d", cents)
        tvTitle.setText(R.string.msg_taginfo)
        val textTag = """
      Read Tag (${clearTextTag.size}):
      ${byteArrayToHex(clearTextTag)}
           
      ${if (tId == Constants.tagId) "correct" else "wrong"}
      ID: $id
      Name: $name
      Price: €$euros.$strCents
    """.trimIndent()
        tvText.text = textTag

        val userUUID = getStoredUserUUID(this);
        if (userUUID != null) {
            addToShoppingList(decodedId.toString(), userUUID) {
                Toast.makeText(this@ScanProductActivity, "Product $name added to the Shopping List successfully!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Function to add a product to the shopping list in the server
    private fun addToShoppingList(uuidProduct: String, uuidUser: String, onComplete: () -> Unit){
        CoroutineScope(Dispatchers.IO).launch {
            val url = URL("$SERVER_URL/addShopList")

            val requestBody = JSONObject().apply {
                put("userId", uuidUser)
                put("productId", uuidProduct)
            }.toString()

            var urlConnection: HttpURLConnection? = null
            try {
                urlConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 5000
                    useCaches = false
                }

                urlConnection.outputStream.use { it.write(requestBody.toByteArray()) }

                val response = if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    readStream(urlConnection.inputStream)
                } else {
                    "Error: Code ${urlConnection.responseCode}"
                }

                withContext(Dispatchers.Main) {
                    if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = JSONObject(response)
                        onComplete()
                    } else {
                        Toast.makeText(this@ScanProductActivity, "You don't have any vouchers", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Network request failed", e)
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    // Function to read the response stream
    private fun readStream(inputStream: InputStream?): String {
        return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
    }
}
