package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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

// Class for the shopping cart page
class ShoppingCartActivity : AppCompatActivity() {
    private var shoppingCart: MutableList<Product> = mutableListOf()
    private lateinit var cartContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_shopping_cart)

        cartContainer = findViewById(R.id.cartContainer)
        val scanProductButton: Button = findViewById(R.id.scanProductButton)

        scanProductButton.setOnClickListener {
            val intent = Intent(this, ScanProductActivity::class.java)
            startActivity(intent)
        }

        // Get the shopping list from the server using the user ID
        val userUUID = getStoredUserUUID(this).toString()
        getShoppingList(userUUID){
            Toast.makeText(this@ShoppingCartActivity, "Retrieve the Shopping List successfully!", Toast.LENGTH_LONG).show()
            displayCartItems()
        }

        //displayCartItems()
        val checkoutButton: Button = findViewById(R.id.checkoutButton)
        checkoutButton.setOnClickListener {
            if (shoppingCart.isNotEmpty()) {
                val intent = Intent(this, CheckoutActivity::class.java)
                val cartItems = shoppingCart.map {
                    ShoppingCartItem(it.name, it.quantity, it.price)
                }
                intent.putParcelableArrayListExtra("shopping_cart", ArrayList(cartItems))
                startActivity(intent)
            }
            else {
                Toast.makeText(this@ShoppingCartActivity, "Your cart is empty", Toast.LENGTH_LONG).show()
            }
        }

        val backButton = findViewById<Button>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    // Function to display the items in the shopping cart
    private fun displayCartItems() {
        cartContainer.removeAllViews() // Clear previous views before updating

        for (i in shoppingCart.indices) {
            val item = shoppingCart[i]
            val itemRow = LayoutInflater.from(this).inflate(R.layout.item_shopping_cart, null) as TableRow

            val nameTextView = itemRow.findViewById<TextView>(R.id.itemNameTextView)
            val quantityTextView = itemRow.findViewById<TextView>(R.id.itemQuantityTextView)
            val priceTextView = itemRow.findViewById<TextView>(R.id.itemPriceTextView)
            val incrementButton = itemRow.findViewById<Button>(R.id.incrementButton)
            val decrementButton = itemRow.findViewById<Button>(R.id.decrementButton)
            val removeButton = itemRow.findViewById<Button>(R.id.removeButton)

            nameTextView.text = item.name
            quantityTextView.text = item.quantity.toString()
            //Toast.makeText(this@ShoppingCartActivity, item.price.toString(), Toast.LENGTH_LONG).show()
            priceTextView.text = String.format("%.2f", item.price * item.quantity)

            incrementButton.setOnClickListener {
                item.quantity++
                displayCartItems()
            }

            decrementButton.setOnClickListener {
                if (item.quantity > 1) {
                    item.quantity--
                } else {
                    shoppingCart.removeAt(i)
                }
                displayCartItems()
            }

            removeButton.setOnClickListener {
                val product = shoppingCart.removeAt(i)
                val productID = product.id
                removeProductFromCart(getStoredUserUUID(this@ShoppingCartActivity), productID) {
                    displayCartItems()
                }
            }

            cartContainer.addView(itemRow)
        }

    }

    // Function to retrieve the shopping list from the server
    private fun getShoppingList(uuidUser: String, onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            // Build the URL with the user ID as a query parameter
            val encodedUUID = URLEncoder.encode(uuidUser, StandardCharsets.UTF_8.toString())
            val url = URL("$SERVER_URL/getShopList/$encodedUUID")

            var urlConnection: HttpURLConnection? = null
            try {
                urlConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    useCaches = false
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }

                val response = if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    readStream(urlConnection.inputStream)
                } else {
                    "Error: Code ${urlConnection.responseCode}"
                }

                withContext(Dispatchers.Main) {
                    if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = JSONObject(response)
                        val listofProducts = responseJson.getJSONArray("products")

                        Log.d("list", listofProducts.toString())

                        for (i in 0 until listofProducts.length()) {
                            val productObj = listofProducts.getJSONObject(i)
                            val product = Product(
                                id = productObj.getString("UUID"),
                                name = productObj.getString("Name"),
                                price = productObj.getDouble("Price"),
                                quantity = 1
                            )
                            shoppingCart.add(product)
                        }

                        onComplete()
                    } else {
                        Toast.makeText(this@ShoppingCartActivity, "You don't have any products", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Network request failed", e)
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    // Function to remove a product from the shopping cart in the server
    private fun removeProductFromCart(uuidUser: String?, productID: String, onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            // Build the URL with the user ID as a query parameter
            val encodedUUID = URLEncoder.encode(uuidUser, StandardCharsets.UTF_8.toString())
            val url = URL("$SERVER_URL/userProduct/$encodedUUID")

            val requestBody = JSONObject().apply {
                put("productId", productID)
            }.toString()

            var urlConnection: HttpURLConnection? = null
            try {
                urlConnection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    useCaches = false
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }

                urlConnection.outputStream.use { it.write(requestBody.toByteArray()) }

                val response = if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    readStream(urlConnection.inputStream)
                } else {
                    "Error: Code ${urlConnection.responseCode}"
                }

                withContext(Dispatchers.Main) {
                    if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        onComplete()
                    } else {
                        Toast.makeText(this@ShoppingCartActivity, "Failed to remove product", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Network request failed", e)
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    // Function to read the response from the server
    private fun readStream(inputStream: InputStream?): String {
        return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
    }
}