package com.example.acme_supermarketapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator

// Class for the login page
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_login)

        val usernameInput: EditText = findViewById(R.id.usernameInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val registerLink: TextView = findViewById(R.id.registerLink)
        val loginSubmitButton: Button = findViewById(R.id.loginSubmitButton)

        // Link in case the user doesn't have an account
        registerLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }

        loginSubmitButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val enteredPassword = passwordInput.text.toString()

            // Using a separate coroutine for the login process
            CoroutineScope(Dispatchers.Main).launch {
                // Check if login is from admin
                val isAdmin = checkAdmin(username, enteredPassword)
                saveAdminStatus(this@LoginActivity, isAdmin)

                // If its from admin, accept it
                if (isAdmin) {
                    Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@LoginActivity, HomePageActivity::class.java))
                    finish()
                }
                else {
                    // If it is from a normal user, accept it if the password is correct according to the stored one
                    val storedPassword = getStoredPassword(this@LoginActivity)
                    if (enteredPassword == storedPassword) {
                        CoroutineScope(Dispatchers.Main).launch {
                            authenticateUser(username)
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, "Incorrect credentials. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Function to verify from the server if the user is an admin
    private suspend fun checkAdmin(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = URL("$SERVER_URL/admin")

            val requestBody = JSONObject().apply {
                put("username", username)
                put("password", password)
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
                    JSONObject(readStream(urlConnection.inputStream))
                } else {
                    null
                }

                return@withContext response?.optBoolean("passwordMatch") ?: false
            } catch (e: IOException) {
                Log.e("NetworkError", "Network request failed", e)
                return@withContext false
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    // Function to authenticate the user
    private suspend fun authenticateUser(username: String) {
        withContext(Dispatchers.IO) {
            val nonce = requestNonce(username) // Server sends a nonce

            if (nonce == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Failed to get nonce", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }

            val signedNonce = signDataWithEC(nonce) // User signs the nonce with their private key

            val url = URL("$SERVER_URL/login_user")

            // Submit to the server the signed nonce and username
            val requestBody = JSONObject().apply {
                put("username", username)
                put("signedNonce", signedNonce)
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
                    JSONObject(readStream(urlConnection.inputStream))
                } else {
                    null
                }

                withContext(Dispatchers.Main) {
                    if (response != null) {
                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@LoginActivity, HomePageActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Incorrect credentials. Please try again", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                        finish()
                    }
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Network request failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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

    // Function to read the response stream
    private fun readStream(inputStream: InputStream?): String {
        return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
    }
}
