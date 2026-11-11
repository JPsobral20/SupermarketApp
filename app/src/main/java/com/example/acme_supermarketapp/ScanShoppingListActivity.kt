package com.example.acme_supermarketapp

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// Class for the scan shopping list page
class ScanShoppingListActivity : AppCompatActivity() {

    private val PUBLIC_KEY_BASE64 = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAMqxIvVOUAmWyjfB2AtEPiECuwo+5U75cPwwVv75i2qaW3E26Q4CGpdsRvCOgCgQmaBo/OAh6kaJYibvnMr2sksCAwEAAQ=="

    private lateinit var backgroundLayout: View
    private var nfcAdapter: NfcAdapter? = null
    private var finalTotal = 0.0;
    private var finalAccumulatedDiscount = 0.0;
    private var vouchersAdded = 0;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_scan_shopping_list)

        backgroundLayout = findViewById(R.id.mainLayout) // Change to your root layout ID

        val scanQRCodeButton: Button = findViewById(R.id.scanQRCodeButton)
        scanQRCodeButton.setOnClickListener { scanQRCode() }

        val scanNFCButton: Button = findViewById(R.id.scanNFCButton)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            scanNFCButton.isEnabled = false
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_LONG).show()
        } else {
            scanNFCButton.setOnClickListener {
                Toast.makeText(this, "Approach the NFC tag", Toast.LENGTH_SHORT).show()
            }
        }

        val backButton = findViewById<Button>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Toast.makeText(this, "NFC detected", Toast.LENGTH_SHORT).show()
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { readNfcTag(it) }
        }
    }

    // Function to initiate the scan of the QR code
    private fun scanQRCode() {
        val integrator = IntentIntegrator(this)
        integrator.setPrompt("Scan the shopping list QR code")
        integrator.setBeepEnabled(false)
        integrator.setOrientationLocked(false)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        qrCodeLauncher.launch(integrator.createScanIntent())
    }

    // Function to handle the result of the QR code scan
    private val qrCodeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
                if (scanResult.contents != null) {
                    processShoppingList(scanResult.contents)
                } else {
                    Toast.makeText(this, "Failed to scan QR code", Toast.LENGTH_SHORT).show()
                    updateScreen(false)
                }
            } else {
                Toast.makeText(this, "QR scanning was canceled or failed", Toast.LENGTH_SHORT).show()
                updateScreen(false)
            }
        }

    // Function to read the NFC tag
    private fun readNfcTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                if (ndefMessage != null && ndefMessage.records.isNotEmpty()) {
                    val payload = ndefMessage.records[0].payload

                    processShoppingList(String(payload, Charsets.ISO_8859_1))

                } else {
                    Toast.makeText(this, "Empty NFC message", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NFC", "Error reading NFC tag", e)
                Toast.makeText(this, "Failed to read NFC tag", Toast.LENGTH_LONG).show()
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {}
            }
        } else {
            Toast.makeText(this, "Unsupported NFC tag", Toast.LENGTH_SHORT).show()
        }
    }


    // Function to process the shopping list
    private fun processShoppingList(qrText: String) {
        try {
            val decryptedBytes = decryptQRCode(qrText) ?: throw Exception("Decryption returned null")
            val buffer = ByteBuffer.wrap(decryptedBytes)

            // Read user UUID length and UUID bytes
            val userUUIDLength = buffer.int
            val userUUIDBytes = ByteArray(userUUIDLength)
            buffer.get(userUUIDBytes)

            // Read number of items
            val itemCount = buffer.int
            val items = mutableListOf<ShoppingCartItem>()

            repeat(itemCount) {
                val nameLength = buffer.get().toInt()
                val nameBytes = ByteArray(nameLength)
                buffer.get(nameBytes)
                val name = String(nameBytes, Charsets.ISO_8859_1)

                val quantity = buffer.int
                val price = buffer.double

                items.add(ShoppingCartItem(name, quantity, price))
            }

            // Read voucher
            val voucherLength = buffer.get().toInt()
            val voucher = if (voucherLength > 0) {
                val voucherBytes = ByteArray(voucherLength)
                buffer.get(voucherBytes)
                String(voucherBytes, Charsets.ISO_8859_1)
            } else null

            // Read accumulateDiscount flag
            val accumulateDiscountByte = buffer.get()
            val useDiscount = accumulateDiscountByte.toInt() == 1

            lifecycleScope.launch {
                val isApproved = checkShoppingListApproval(String(userUUIDBytes, Charsets.ISO_8859_1), items, voucher, useDiscount)

                updateScreen(isApproved && items.isNotEmpty())
                if (items.isEmpty()) {
                    Toast.makeText(this@ScanShoppingListActivity, "No items found in the shopping list", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Decryption failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("ScanDecrypt", "Error during processing", e)
            updateScreen(false)
        }
    }

    // Function to parse the shopping list items from the buffer
    private fun parseShoppingCartItems(buffer: ByteBuffer): List<ShoppingCartItem> {
        val itemCount = buffer.int
        val items = mutableListOf<ShoppingCartItem>()
        repeat(itemCount) {
            val nameLength = buffer.get().toInt()
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val name = String(nameBytes, Charsets.ISO_8859_1)
            val quantity = buffer.int
            val price = buffer.double
            items.add(ShoppingCartItem(name, quantity, price))
        }
        return items
    }

    // Function to parse the voucher from the buffer
    private fun parseVoucher(buffer: ByteBuffer): String? {
        val voucherLen = buffer.get().toInt()
        return if (voucherLen > 0) {
            val voucherBytes = ByteArray(voucherLen)
            buffer.get(voucherBytes)
            String(voucherBytes, Charsets.ISO_8859_1)
        } else {
            null
        }
    }

    // Function to decrypt the QR code
    private fun decryptQRCode(qrText: String): ByteArray? {
        return try {
            val contentBytes = qrText.toByteArray(Charsets.ISO_8859_1)
            val buffer = ByteBuffer.wrap(contentBytes)

            val aesKeyLen = buffer.short.toInt()
            val encryptedAESKey = ByteArray(aesKeyLen)
            buffer.get(encryptedAESKey)

            val encryptedData = ByteArray(buffer.remaining())
            buffer.get(encryptedData)

            val publicKey = loadPublicKey(PUBLIC_KEY_BASE64)
            val cipherRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipherRSA.init(Cipher.DECRYPT_MODE, publicKey)
            val aesKey = cipherRSA.doFinal(encryptedAESKey)

            val cipherAES = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipherAES.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"))
            cipherAES.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e("Decrypt", "Error decrypting QR code", e)
            null
        }
    }

    // Function to load the public key
    private fun loadPublicKey(base64PublicKey: String): PublicKey? {
        return try {
            val decodedKey = Base64.decode(base64PublicKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(decodedKey)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading public key", Toast.LENGTH_LONG).show()
            null
        }
    }

    // Function to check the shopping list approval from the server
    private suspend fun checkShoppingListApproval(userUUID: String, cartItems: List<ShoppingCartItem>, selectedVoucherId: String?, useDiscount: Boolean): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$SERVER_URL/checkout")

        val productsArray = JSONArray().apply {
            cartItems.forEach {
                put(JSONObject().apply {
                    put("name", it.name)
                    put("price", it.price)
                    put("quantity", it.quantity)
                })
            }
        }

        val requestBody = JSONObject().apply {
            put("userUUID", userUUID)
            put("products", productsArray)
            put("voucherUUID", selectedVoucherId ?: JSONObject.NULL)
            put("useAccumulatedDiscount", useDiscount)
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

            urlConnection.outputStream.use {
                it.write(requestBody.toByteArray())
            }

            val response = if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                urlConnection.inputStream.bufferedReader().use { it.readText() }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScanShoppingListActivity, "Error: Code ${urlConnection.responseCode}", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }

            val responseJson = JSONObject(response)

            finalTotal = responseJson.getDouble("Total")
            finalAccumulatedDiscount = responseJson.getDouble("FinalAccumulatedDiscount")
            vouchersAdded = responseJson.getInt("VouchersAdded")

            return@withContext true

        } catch (e: IOException) {
            Log.e("NetworkError", "Network request failed", e)
            return@withContext false
        } finally {
            urlConnection?.disconnect()
        }
    }

    // Function to update the screen based on the approval result
    private fun updateScreen(isApproved: Boolean) {
        val intent = Intent(this, ShoppingListResultActivity::class.java).apply {
            putExtra("isApproved", isApproved)
            putExtra("finalTotal", finalTotal)
            putExtra("finalAccumulatedDiscount", finalAccumulatedDiscount)
            putExtra("vouchersAdded", vouchersAdded)
        }
        startActivity(intent)
        finish()
    }

    // Function to read the response stream
    private fun readStream(inputStream: InputStream?): String {
        return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
    }
}