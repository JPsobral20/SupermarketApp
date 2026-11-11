package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

// Class used for the voucher generation page
class GenerateVoucherActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_voucher_gen)

        val usernameInput = findViewById<EditText>(R.id.receiverUsernameInput)
        val voucherNameInput = findViewById<EditText>(R.id.voucherNameInput)
        val voucherDiscountPercentageInput = findViewById<EditText>(R.id.voucherDiscountPercentageInput)

        val generateVoucherButton = findViewById<Button>(R.id.generateVoucherButton)
        generateVoucherButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val voucherName = voucherNameInput.text.toString()
            val discountPercentage = voucherDiscountPercentageInput.text.toString().toIntOrNull()
            storeVoucherServer(username, voucherName, discountPercentage)
        }

        val menuButton = findViewById<Button>(R.id.menuButton)
        menuButton.setOnClickListener {
            val intent = Intent(this, HomePageActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    // Function to store the voucher on the server
    private fun storeVoucherServer(username: String, voucherName: String, discountPercentage: Int?) {
        // Use a separate coroutine scope (thread) for network operations
        CoroutineScope(Dispatchers.IO).launch {
            val url = URL("$SERVER_URL/vouchers")

            val requestBody = JSONObject().apply {
                put("username", username)
                put("voucherName", voucherName)
                put("discountPercentage", discountPercentage)
            }.toString()

            var urlConnection: HttpURLConnection? = null
            try {
                urlConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json")
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
                        // Clear the input fields if the admin wants to generate another voucher
                        findViewById<EditText>(R.id.receiverUsernameInput).text.clear()
                        findViewById<EditText>(R.id.voucherNameInput).text.clear()
                        findViewById<EditText>(R.id.voucherDiscountPercentageInput).text.clear()
                        Toast.makeText(this@GenerateVoucherActivity, "Voucher generated successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@GenerateVoucherActivity, "Voucher generation failed! $response", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Network request failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GenerateVoucherActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
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