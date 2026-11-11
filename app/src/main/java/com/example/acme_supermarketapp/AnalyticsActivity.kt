package com.example.acme_supermarketapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Class responsible for the analytics page
class AnalyticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        setContentView(R.layout.activity_analytics)

        val userUUID = getStoredUserUUID(this)
        if (userUUID != null) {
            retrieveClientAnalytics(this@AnalyticsActivity, userUUID) { analytics ->
                if (analytics != null) {
                    findViewById<TextView>(R.id.tvTotalSpent).text =
                        "Total Spent: $${"%.2f".format(analytics.totalSpent)}"
                    findViewById<TextView>(R.id.tvPurchaseCount).text =
                        "Number of Purchases: ${analytics.purchaseCount}"
                    findViewById<TextView>(R.id.tvAvgSpend).text =
                        "Average Spend: $${"%.2f".format(analytics.avgSpend)}"
                    findViewById<TextView>(R.id.tvTotalDiscount).text =
                        "Total Discount: $${"%.2f".format(analytics.totalDiscount)}"
                    findViewById<TextView>(R.id.tvVouchersUsed).text =
                        "Vouchers Used: ${analytics.vouchersUsed}"

                    val recyclerView = findViewById<RecyclerView>(R.id.recyclerTopProducts)
                    recyclerView.adapter = TopProductsAdapter(analytics.topProducts)
                    recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
                }
            }
        }

        val backButton = findViewById<Button>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    // Function to request a nonce from the server
    private suspend fun requestNonce(userUUID: String?): String? {
        val url = URL("$SERVER_URL/get_analytics_nonce")
        val requestBody = JSONObject().apply {
            put("userUUID", userUUID)
        }.toString()

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 5000
                    useCaches = false
                }

                connection.outputStream.use { it.write(requestBody.toByteArray()) }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = JSONObject(readStream(connection.inputStream))
                    response.getString("nonce")
                } else {
                    null
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Failed to request nonce", e)
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    // Function to retrieve client analytics from the server
    private fun retrieveClientAnalytics(
        act: Activity,
        userUUID: String,
        onResult: (ClientAnalytics?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val nonce = requestNonce(userUUID)
            if (nonce == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(act, "Failed to get nonce", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val signedNonce = signChallengeWithRSA(nonce)
            val requestBody = JSONObject().apply {
                put("signedNonce", signedNonce)
            }.toString()

            val encodedUUID = URLEncoder.encode(userUUID, StandardCharsets.UTF_8.toString())

            val url = URL("$SERVER_URL/analytics/$encodedUUID")
            var conn: HttpURLConnection? = null

            try {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doInput = true
                    doOutput = true
                    connectTimeout = 5000
                }

                conn.outputStream.use { it.write(requestBody.toByteArray()) }

                val response = readStream(conn.inputStream)
                withContext(Dispatchers.Main) {
                    val json = JSONObject(response)
                    val analytics = ClientAnalytics.fromJson(json)
                    onResult(analytics)
                }
            } catch (e: Exception) {
                Log.e("AnalyticsError", "Error fetching analytics", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(act, "Failed to retrieve analytics", Toast.LENGTH_LONG).show()
                    onResult(null)
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // Function to read the response stream
    private fun readStream(inputStream: InputStream?): String {
        return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
    }
}

// Class to help populate the analytics page
class TopProductsAdapter(private val products: List<Pair<String, Int>>) :
    RecyclerView.Adapter<TopProductsAdapter.ProductViewHolder>() {

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val productName: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            android.R.layout.simple_list_item_1,
            parent,
            false
        )

        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.setTextColor(ContextCompat.getColor(parent.context, R.color.blue))
        textView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return ProductViewHolder(view)
    }


    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val (name, quantity) = products[position]
        holder.productName.text = "${position + 1}. $name — Qty: $quantity"
    }

    override fun getItemCount() = products.size
}