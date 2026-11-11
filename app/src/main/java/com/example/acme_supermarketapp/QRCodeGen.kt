package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal


// Utility top-level function used for converting a ByteArray to a String
fun byteArrayToHex(ba: ByteArray): String {
    val sb = StringBuilder(ba.size * 2)
    for (b in ba) sb.append(String.format("%02x", b))
    return sb.toString()
}

// Function used for converting a String to a ByteArray
fun hexStringToByteArray(s: String): ByteArray {
    val data = ByteArray(s.length / 2)
    var k = 0
    while (k < s.length) {
        data[k/2] = ((Character.digit(s[k], 16) shl 4) + Character.digit(s[k+1], 16)).toByte()
        k += 2
    }
    return data
}

// Class for the QR code generator page
class QRCodeGen : AppCompatActivity() {
    private val appTag = "GenProductTag"
    private var hasKey = true
    private var encTag: ByteArray? = null
    private var pri: PrivateKey? = null
    private var pub: PublicKey? = null
    private var productUUID = UUID(0L, 0L)
    private var entry: KeyStore.PrivateKeyEntry? = null
        get() {
            if (field == null)
                field = KeyStore.getInstance(Constants.ANDROID_KEYSTORE).run {
                    load(null)
                    getEntry(Constants.keyname, null) as KeyStore.PrivateKeyEntry?
                }
            return field
        }

    private val edUUID by lazy { findViewById<EditText>(R.id.ed_uuid) }
    private val edName by lazy { findViewById<EditText>(R.id.ed_name) }
    private val edEuros by lazy { findViewById<EditText>(R.id.ed_euros) }
    private val edCents by lazy { findViewById<EditText>(R.id.ed_cents) }
    private val tvNoKey by lazy { findViewById<TextView>(R.id.tv_nokey) }
    private val tvKey by lazy { findViewById<TextView>(R.id.tv_pubkey) }
    private val btGenTag by lazy { findViewById<Button>(R.id.bt_gentag) }
    private val btDecTag by lazy { findViewById<Button>(R.id.bt_dectag) }
    private val btUUIDGen by lazy { findViewById<Button>(R.id.bt_ugen) }

    private val privateKey = "MIIBVgIBADANBgkqhkiG9w0BAQEFAASCAUAwggE8AgEAAkEAyrEi9U5QCZbKN8HYC0Q+IQK7Cj7lTvlw/DBW/vmLappbcTbpDgIal2xG8I6AKBCZoGj84CHqRoliJu+cyvaySwIDAQABAkBXUlznJafhma7guogiwnGpsw11aodLsLb2TrUL/+IKOFQhGE/450jgtjiHL+owzHOr4t5m21fgCyCw1OOop4TxAiEA/21NGdCi9xjYpnmTkWTeNG9Tzcaiuih9Zgn+GDOvHPUCIQDLJYxWHFHdzgx60tgHsMRHxmmmerLBQCqNZ4PR5qkKPwIhAJT33DWoFsNh/EjrMwNi/q06GHg01wcXQjtWrJjU29ZZAiEArZJ7gefYmye+ygL0yj1it+DzzGbx7OSidweHPgVTUYkCIQDnpgF2eqvmvecx7KiWJoWyRy03dUxvbq6DV7MT7QHiSw=="

    private fun getPrivateKey(privateKeyString: String): PrivateKey {
        val keyBytes = Base64.decode(privateKeyString, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    private val priKey = getPrivateKey(privateKey)

    private val publicKey = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAMqxIvVOUAmWyjfB2AtEPiECuwo+5U75cPwwVv75i2qaW3E26Q4CGpdsRvCOgCgQmaBo/OAh6kaJYibvnMr2sksCAwEAAQ=="

    // Function to retrieve the public key
    private fun getPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    private val pubKey = getPublicKey(publicKey)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        //enableEdgeToEdge()
        setContentView(R.layout.activity_qr_code_gen)
        //setStatusBarIconColor(window, Lightness.LIGHT)
        //setInsetsPadding(tvKey, bottom=0)
        try {
            hasKey = entry != null
            if (hasKey) {
                pub = entry?.certificate?.publicKey
                pri = entry?.privateKey
                tvNoKey.text = ""
            }
        }
        catch (e: Exception) {
            hasKey = false
        }
        btGenTag.setOnClickListener {
            genTag()
            val productUUID = edUUID.text.toString()
            val productName = edName.text.toString()
            val price = edEuros.text.toString() + "." + edCents.text.toString()
            registerProduct(productUUID, productName, price) {
                Toast.makeText(this@QRCodeGen, "Product registered successfully!", Toast.LENGTH_LONG).show()
            }
        }
        btDecTag.setOnClickListener { decodeTag() }
        btUUIDGen.setOnClickListener {
            productUUID = UUID.randomUUID()
            edUUID.setText(productUUID.toString())
        }

        val menuButton = findViewById<Button>(R.id.menuButton)
        menuButton.setOnClickListener {
            val intent = Intent(this, HomePageActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    // Function to generate the QR Code tag
    private fun genTag() {
        var name = edName.text.toString()
        if (edUUID.text.toString().isEmpty() || name.isEmpty() || edEuros.text.toString().isEmpty() || edCents.text.toString().isEmpty()) {
            tvNoKey.setText(R.string.msg_empty)
            return
        }

        if (name.length > 29)
            name = name.substring(0, 29)
        val len = 4 + 16 + 2 + 1 + 1 + name.length  // length of (tagID, UUID, euros(short), cents(byte), nr_bytes(name)(byte), name)
        val tag = ByteBuffer.allocate(len).apply {  // building an array of bytes (binary)
            putInt(Constants.tagId)
            putLong(productUUID.mostSignificantBits)
            putLong(productUUID.leastSignificantBits)
            putShort(edEuros.text.toString().toShort())
            put(edCents.text.toString().toByte())
            put(name.length.toByte())
            put(name.toByteArray(StandardCharsets.ISO_8859_1))   // 1 byte per char without code translation
        }
        try {
            encTag = Cipher.getInstance(Constants.ENC_ALGO).run {
                init(Cipher.ENCRYPT_MODE, priKey)
                doFinal(tag.array())
            }
            tvKey.text = resources.getString(R.string.msg_enctag, encTag!!.size, byteArrayToHex(encTag!!))
            tvNoKey.text = ""
        }
        catch (e: Exception) {
            tvNoKey.text = e.message
        }
        val price = edEuros.text.toString() + "." + edCents.text.toString()
        val productName = edName.text.toString()
        startActivity(Intent(this, QRTagActivity::class.java).apply {
            putExtra("data", encTag);
            putExtra("price", price);
            putExtra("name", productName);
        })
    }

    // Function to decode the QR Code tag
    private fun decodeTag() {
        val clearTextTag: ByteArray
        if (encTag == null || encTag!!.isEmpty()) {
            tvNoKey.setText(R.string.msg_notag)
            return
        }
        try {
            clearTextTag = Cipher.getInstance(Constants.ENC_ALGO).run {
                init(Cipher.DECRYPT_MODE, pubKey)
                doFinal(encTag)
            }
        }
        catch (e: Exception) {
            tvNoKey.text = e.message
            return
        }
        val tag = ByteBuffer.wrap(clearTextTag)
        val tId = tag.int
        val id = UUID(tag.long, tag.long)
        val euros = tag.short.toInt()
        val cents = tag.get().toInt()
        val bName = ByteArray(tag.get().toInt())
        tag[bName]
        val name = String(bName, StandardCharsets.ISO_8859_1)
        val strCents = String.format(Locale.ENGLISH, "%02d", cents)
        val text = """
      DecTag (${clearTextTag.size}):
      ${byteArrayToHex(clearTextTag)}
           
      ${if (tId == Constants.tagId) "correct" else "wrong"}
      ID: $id
      Name: $name
      Price: €$euros.$strCents
    """.trimIndent()
        tvNoKey.text = ""
        tvKey.text = text
    }

    // Function to register the product in the server
    private fun registerProduct(uuid: String, productName: String, price: String, onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = URL("$SERVER_URL/products")

            val requestBody = JSONObject().apply {
                put("uuid", uuid)
                put("productName", productName)
                put("price", price)
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
                        Toast.makeText(this@QRCodeGen, "You don't have any vouchers", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("NetworkError", "Network request failed", e)
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    private fun readStream(inputStream: InputStream?): String {
        return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
    }
}