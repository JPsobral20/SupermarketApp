package com.example.acme_supermarketapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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
import android.util.Base64
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

private val usedVouchers = mutableListOf<Voucher>()
private val unusedVouchers = mutableListOf<Voucher>()
private lateinit var availableRecyclerView: RecyclerView
private lateinit var usedRecyclerView: RecyclerView

// Class responsible for the used and unused vouchers page
class VoucherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_voucher)

        val availableRecyclerView: RecyclerView = findViewById(R.id.availableVouchersRecyclerView)
        availableRecyclerView.layoutManager = LinearLayoutManager(this)

        val usedRecyclerView: RecyclerView = findViewById(R.id.usedVouchersRecyclerView)
        usedRecyclerView.layoutManager = LinearLayoutManager(this)

        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish() // Close the current activity and return to the home page
        }

        val userUUID = getStoredUserUUID(this)
        if (userUUID != null) {
            retrieveVouchers(this, userUUID) {
                availableRecyclerView.adapter = VoucherAdapter(unusedVouchers)
                usedRecyclerView.adapter = VoucherAdapter(usedVouchers)
            }
        } else {
            Toast.makeText(this, "Error retrieving user UUID in order to extract vouchers", Toast.LENGTH_LONG).show()
        }
    }
}

// Class responsible for the selection of a voucher during checkout
class SelectVoucherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_select_voucher)

        val recyclerView: RecyclerView = findViewById(R.id.voucherRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val userUUID = getStoredUserUUID(this)
        if (userUUID != null) {
            retrieveVouchers(this, userUUID) {
                // Set the adapter only after vouchers are retrieved
                recyclerView.adapter = VoucherAdapter(unusedVouchers) { selectedVoucher ->
                    val resultIntent = Intent().apply {
                        putExtra("selectedVoucherUuid", selectedVoucher.uuid)
                        putExtra("selectedVoucherName", selectedVoucher.name)
                        putExtra("selectedVoucherDiscount", selectedVoucher.discountPercentage)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        } else {
            Toast.makeText(this, "Error retrieving user UUID in order to extract vouchers", Toast.LENGTH_LONG).show()
        }
    }
}

// Class used to display the vouchers in a recycler view
class VoucherAdapter(
    private val vouchers: List<Voucher>,
    private val onItemClick: ((Voucher) -> Unit)? = null
) : RecyclerView.Adapter<VoucherAdapter.VoucherViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoucherViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.voucher_item, parent, false)
        return VoucherViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoucherViewHolder, position: Int) {
        holder.bind(vouchers[position])
    }

    override fun getItemCount(): Int = vouchers.size

    // ViewHolder class for binding voucher data to views
    inner class VoucherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val voucherTextView: TextView = itemView.findViewById(R.id.voucherTextView)

        fun bind(voucher: Voucher) {
            voucherTextView.text = "${voucher.name} - ${voucher.discountPercentage}% Off"
            itemView.setOnClickListener {
                onItemClick?.invoke(voucher)
            }
        }
    }
}

// Function to request a nonce from the server
private suspend fun requestNonce(userUUID: String?): String? {
    val url = URL("$SERVER_URL/get_voucher_nonce")
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

// Function to retrieve vouchers from the server
private fun retrieveVouchers(act: Activity, userUUID: String, onComplete: () -> Unit) {
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
        val url = URL("$SERVER_URL/vouchers/$encodedUUID")

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
                    val responseJson = JSONObject(response)
                    val usedVoucherJson = responseJson.getJSONArray("usedVouchers")
                    val unusedVoucherJson = responseJson.getJSONArray("unusedVouchers")

                    usedVouchers.clear()
                    unusedVouchers.clear()

                    for (i in 0 until usedVoucherJson.length()) {
                        val voucherObj = usedVoucherJson.getJSONObject(i)
                        val voucher = Voucher(
                            uuid = voucherObj.getString("UUID"),
                            name = voucherObj.getString("Name"),
                            discountPercentage = voucherObj.getInt("Percentage")
                        )
                        usedVouchers.add(voucher)
                    }

                    for (i in 0 until unusedVoucherJson.length()) {
                        val voucherObj = unusedVoucherJson.getJSONObject(i)
                        val voucher = Voucher(
                            uuid = voucherObj.getString("UUID"),
                            name = voucherObj.getString("Name"),
                            discountPercentage = voucherObj.getInt("Percentage")
                        )
                        unusedVouchers.add(voucher)
                    }

                    onComplete()
                } else {
                    Toast.makeText(act, "You don't have any vouchers", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: IOException) {
            Log.e("NetworkError", "Network request failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(act, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
