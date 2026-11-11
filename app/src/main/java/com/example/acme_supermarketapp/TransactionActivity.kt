package com.example.acme_supermarketapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Class responsible for the past transactions page
class TransactionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_past_transactions)

        val recyclerView: RecyclerView = findViewById(R.id.transactionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }

        val userUUID = getStoredUserUUID(this)
        if (userUUID != null) {
            retrieveTransactions(this, userUUID) {
                recyclerView.adapter = TransactionAdapter(transactions) { selectedTransaction ->
                    showTransactionDetailsDialog(selectedTransaction)
                }
            }
        } else {
            Toast.makeText(this, "Error retrieving user UUID in order to extract transactions", Toast.LENGTH_LONG).show()
        }
    }

    // Function to show transaction details in a dialog
    private fun showTransactionDetailsDialog(transaction: Transaction) {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Transaction Details")
        val detailsView = layoutInflater.inflate(R.layout.dialog_transaction_details, null)

        val productsRecyclerView: RecyclerView = detailsView.findViewById(R.id.productsRecyclerView)
        val productAdapter = ProductAdapter(transaction.products)
        productsRecyclerView.layoutManager = LinearLayoutManager(this)
        productsRecyclerView.adapter = productAdapter

        builder.setView(detailsView)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private val transactions = mutableListOf<Transaction>()

    // Function to retrieve transactions from the server
    private fun retrieveTransactions(context: Context, userUUID: String, onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val nonce = requestNonce(userUUID)

            if (nonce == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to get nonce", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val signedNonce = signChallengeWithRSA(nonce)

            val requestBody = JSONObject().apply {
                put("signedNonce", signedNonce)
            }.toString()

            val encodedUUID = URLEncoder.encode(userUUID, StandardCharsets.UTF_8.toString())

            val url = URL("$SERVER_URL/pastTransactions/$encodedUUID")

            var urlConnection: HttpURLConnection? = null
            try {
                urlConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json")
                    useCaches = false
                }

                urlConnection.outputStream.use { it.write(requestBody.toByteArray()) }

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = readStream(urlConnection.inputStream)
                    val jsonArray = JSONArray(response)

                    transactions.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        Log.d("Transaction", "Transaction object: $obj")
                        val productsJson = obj.getJSONArray("products")
                        val productList = mutableListOf<ShoppingCartItem>()

                        for (j in 0 until productsJson.length()) {
                            val p = productsJson.getJSONObject(j)
                            val product = ShoppingCartItem(
                                name = p.getString("name"),
                                price = p.getDouble("price"),
                                quantity = p.getInt("quantity")
                            )
                            productList.add(product)
                        }

                        val transaction = Transaction(
                            date = obj.getString("date"),
                            total = obj.getDouble("total"),
                            voucherUsed = obj.optInt("usedVoucher", 0) == 1,
                            accumulatedDiscount = obj.optDouble("accumulatedDiscount", 0.0),
                            products = productList
                        )

                        Log.d("Transaction", "Voucher Used: ${obj.optInt("usedVoucher", 0)}")

                        transactions.add(transaction)
                    }

                    withContext(Dispatchers.Main) {
                        onComplete()
                    }

                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to retrieve transactions", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("NetworkError", "Error fetching transactions", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                urlConnection?.disconnect()
            }
        }
    }
}

// Function to request a nonce from the server
private suspend fun requestNonce(userUUID: String?): String? {
    val url = URL("$SERVER_URL/get_transaction_nonce")
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

// Class to display transactions in a recycler view
class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val onItemClick: ((Transaction) -> Unit)
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
        holder.itemView.setOnClickListener { onItemClick(transactions[position]) }
    }

    override fun getItemCount() = transactions.size

    inner class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val transactionDate: TextView = itemView.findViewById(R.id.transactionDate)
        private val transactionTotal: TextView = itemView.findViewById(R.id.transactionTotal)
        private val voucherUsed: TextView = itemView.findViewById(R.id.voucherUsed)
        private val accumulatedDiscount: TextView = itemView.findViewById(R.id.accumulatedDiscount)
        private val detailsButton: Button = itemView.findViewById(R.id.detailsButton)

        fun bind(transaction: Transaction) {
            transactionDate.text = "Date: ${transaction.date}"
            transactionTotal.text = "Total: €${"%.2f".format(transaction.total)}"
            voucherUsed.text = "Voucher Used: ${if (transaction.voucherUsed) "Yes" else "No"}"
            accumulatedDiscount.text = "Accumulated Discount: €${"%.2f".format(transaction.accumulatedDiscount)}"

            // Use the lambda passed to the adapter
            detailsButton.setOnClickListener {
                onItemClick(transaction)
            }
        }
    }
}

// Adapter for products in the transaction details dialog
class ProductAdapter(private val products: List<ShoppingCartItem>) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product_details, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount() = products.size

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val productName: TextView = itemView.findViewById(R.id.productName)
        private val productPrice: TextView = itemView.findViewById(R.id.productPrice)
        private val productQuantity: TextView = itemView.findViewById(R.id.productQuantity)

        fun bind(product: ShoppingCartItem) {
            productName.text = product.name
            productPrice.text = "€${"%.2f".format(product.price)}"
            productQuantity.text = "x${product.quantity}"
        }
    }
}






// Function to parse product data from the response
private fun parseProducts(productsJson: JSONArray): List<ShoppingCartItem> {
    val products = mutableListOf<ShoppingCartItem>()
    for (i in 0 until productsJson.length()) {
        val productObj = productsJson.getJSONObject(i)
        val product = ShoppingCartItem(
            name = productObj.getString("name"),
            price = productObj.getDouble("price"),
            quantity = productObj.getInt("quantity")
        )
        products.add(product)
    }
    return products
}


// Function to read the response stream
private fun readStream(inputStream: InputStream?): String {
    return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
}
