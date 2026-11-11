package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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

// Class for managing the registration of a user in the app
class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_register)

        val passwordStored = isPasswordStored(this)

        // If password is already stored locally, redirect to login
        if (passwordStored) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        val nameInput: EditText = findViewById(R.id.nameInput)
        val usernameInput: EditText = findViewById(R.id.usernameInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val repeatPasswordInput: EditText = findViewById(R.id.repeatPasswordInput)

        val cardTypeSpinner: Spinner = findViewById(R.id.cardTypeSpinner)
        val cardTypes = arrayOf("Visa", "Mastercard")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cardTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        cardTypeSpinner.adapter = adapter
        cardTypeSpinner.setSelection(0) // Index 0 corresponds to "Visa"

        val cardExpirationMonthInput: EditText = findViewById(R.id.cardExpirationMonthInput)
        val cardExpirationYearInput: EditText = findViewById(R.id.cardExpirationYearInput)
        val cardNumberInput: EditText = findViewById(R.id.cardNumberInput)
        val loginLink: TextView = findViewById(R.id.loginLink)
        val registerSubmitButton: Button = findViewById(R.id.registerSubmitButton)

        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Handle registration when clicking the register button
        registerSubmitButton.setOnClickListener {
            val name = nameInput.text.toString()
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            val repeatPassword = repeatPasswordInput.text.toString()
            val cardType = cardTypeSpinner.selectedItem as String
            val cardExpirationMonth = cardExpirationMonthInput.text.toString()
            val cardExpirationYear = cardExpirationYearInput.text.toString()
            val cardNumber = cardNumberInput.text.toString()

            // Verifies that all fields have the correct length
            if (!areFieldsLengthValid(name, username, password, cardNumber, cardExpirationMonth, cardExpirationYear)) return@setOnClickListener

            // If the passwords don't match, don't register
            if (password != repeatPassword) {
                Toast.makeText(this, "Passwords must be equal", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Generate RSA & EC Key Pairs
            val rsaPublic = generateAndStoreRSAKeyPair()
            val ecPublic = generateAndStoreECKeyPair()

            val publicRSA = Base64.encodeToString(rsaPublic.encoded, Base64.NO_WRAP)
            val publicEC = Base64.encodeToString(ecPublic.encoded, Base64.NO_WRAP)

            val cardInfo = CardInfo(
                cardType = cardType,
                cardExpirationMonth = cardExpirationMonth,
                cardExpirationYear = cardExpirationYear,
                cardNumber = cardNumber
            )

            // Server call to handle user registration and keys storage
            registerUser(this, name, username, password, cardInfo, publicRSA, publicEC) {
                // Only executed if registration is successful
                val intent = Intent(this, HomePageActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    // Function to verify that all fields have the correct length
    private fun areFieldsLengthValid(name: String, username: String, password: String, cardNumber: String, cardMonth: String, cardYear: String): Boolean {
        // Tuple in the format of (text content, minimum chars accepted, max chars accepted)
        val fields = listOf(
            Triple(name, 4, 30) to "Name",
            Triple(username, 4, 30) to "Username",
            Triple(password, 8, 50) to "Password"
        )

        for ((field, fieldName) in fields) {
            val (value, min, max) = field
            when {
                value.length < min -> {
                    Toast.makeText(this, "$fieldName must be at least $min characters long", Toast.LENGTH_LONG).show()
                    return false
                }
                value.length > max -> {
                    Toast.makeText(this, "$fieldName is too long. Max length is $max characters long", Toast.LENGTH_LONG).show()
                    return false
                }
            }
        }

        if (cardNumber.length != 16) {
            Toast.makeText(this, "Credit card number must have 16 digits", Toast.LENGTH_LONG).show()
            return false
        }

        if (cardMonth.length != 2) {
            Toast.makeText(this, "Card Expiration Month field must have 2 digits", Toast.LENGTH_LONG).show()
            return false
        }

        if (cardYear.length != 2) {
            Toast.makeText(this, "Card Expiration Year field must have 2 digits", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    // Register user in the server
    private fun registerUser(
        act: RegisterActivity,
        name: String,
        username: String,
        password: String,
        cardInfo: CardInfo,
        publicRSA: String,
        publicEC: String,
        onSuccess: () -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val nonce = requestNonce(username)
            if (nonce == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(act, "Failed to get nonce", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val signedNonce = signDataWithEC(nonce)

            val url = URL("$SERVER_URL/users")

            val requestBody = JSONObject().apply {
                put("name", name)
                put("username", username)
                put("cardType", cardInfo.cardType)
                put("cardExpMonth", cardInfo.cardExpirationMonth)
                put("cardExpYear", cardInfo.cardExpirationYear)
                put("cardNumber", cardInfo.cardNumber)
                put("publicRSA", publicRSA)
                put("publicEC", publicEC)
                put("signedNonce", signedNonce)
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
                    readStream(urlConnection.errorStream) ?: "Error: Code ${urlConnection.responseCode}"
                }

                withContext(Dispatchers.Main) {
                    if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        val responseJson = JSONObject(response)
                        val uuidBase64 = responseJson.getString("UUID")

                        // Uncomment when having supermarket keys
                        // val supermarketPublicKey = responseJson.getString("supermarketPublicKey")

                        // Update to received public key on server response
                        storeSupermarketPublicKey(act, "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAMqxIvVOUAmWyjfB2AtEPiECuwo+5U75cPwwVv75i2qaW3E26Q4CGpdsRvCOgCgQmaBo/OAh6kaJYibvnMr2sksCAwEAAQ==")

                        storeUUIDSecurely(act, uuidBase64) // Store UUID locally
                        storePasswordSecurely(act, password) // Store password locally

                        Toast.makeText(act, "Registration Successful!", Toast.LENGTH_LONG).show()
                        onSuccess()  // Call success callback
                    } else {
                        val message = try {
                            JSONObject(response).getString("error")
                        } catch (e: Exception) {
                            response
                        }
                        Toast.makeText(act, "Registration Failed: $message", Toast.LENGTH_LONG).show()
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

    // Function to request a nonce from the server
    private suspend fun requestNonce(username: String): String? {
        val url = URL("$SERVER_URL/get_nonce")
        val requestBody = JSONObject().apply {
            put("username", username)
        }.toString()

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json")
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

    // Function to read the response stream
    private fun readStream(inputStream: InputStream?): String {
        return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
    }
}
