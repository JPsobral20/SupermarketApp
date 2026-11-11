package com.example.acme_supermarketapp

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Text
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// Class for the checkout page
class CheckoutActivity : AppCompatActivity() {
    private lateinit var selectedVoucherTextView: TextView
    private lateinit var shoppingCart: List<ShoppingCartItem>
    private var totalPrice = 0.0;
    private var totalPriceWithoutVoucher = 0.0;
    private var selectedVoucherUUID: String? = null
    private var selectedVoucherDiscount: Int? = null
    private lateinit var updateAccumulationInfo: () -> Unit
    private var nfcAdapter: NfcAdapter? = null
    private var nfcMessage: NdefMessage? = null


    // MUDAR
    private val privateKey = "MIIBVgIBADANBgkqhkiG9w0BAQEFAASCAUAwggE8AgEAAkEAyrEi9U5QCZbKN8HYC0Q+IQK7Cj7lTvlw/DBW/vmLappbcTbpDgIal2xG8I6AKBCZoGj84CHqRoliJu+cyvaySwIDAQABAkBXUlznJafhma7guogiwnGpsw11aodLsLb2TrUL/+IKOFQhGE/450jgtjiHL+owzHOr4t5m21fgCyCw1OOop4TxAiEA/21NGdCi9xjYpnmTkWTeNG9Tzcaiuih9Zgn+GDOvHPUCIQDLJYxWHFHdzgx60tgHsMRHxmmmerLBQCqNZ4PR5qkKPwIhAJT33DWoFsNh/EjrMwNi/q06GHg01wcXQjtWrJjU29ZZAiEArZJ7gefYmye+ygL0yj1it+DzzGbx7OSidweHPgVTUYkCIQDnpgF2eqvmvecx7KiWJoWyRy03dUxvbq6DV7MT7QHiSw=="
    private fun getPrivateKey(privateKeyString: String): PrivateKey {
        val keyBytes = Base64.decode(privateKeyString, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }
    private val priKey = getPrivateKey(privateKey)
    //MUDAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_checkout)

        // Use lifecycleScope to wait for the current discount amount retrieval before continuing
        lifecycleScope.launch {
            val accumulatedDiscountValue: TextView = findViewById(R.id.accumulatedDiscount)
            val discount = getAccumulatedDiscount(this@CheckoutActivity, getStoredUserUUID(this@CheckoutActivity))
            accumulatedDiscountValue.text = "Accumulated Discount: $discount"

            val applyDiscountCheckbox = findViewById<CheckBox>(R.id.applyDiscountCheckbox)
            val totalPriceTextView: TextView = findViewById(R.id.totalPriceTextView)
            val accumulationInfoTextView = findViewById<TextView>(R.id.voucherAccumulationInfo)

            // Receive shopping cart from previous activity
            shoppingCart = intent.getParcelableArrayListExtra<ShoppingCartItem>("shopping_cart") ?: emptyList()
            totalPrice = shoppingCart.sumOf { it.price * it.quantity }

            // Function to update total and discount accumulation info
            fun updateFinalTotalAndAccumulation() {
                val isChecked = applyDiscountCheckbox.isChecked
                val adjustedTotal = if (isChecked) totalPrice - discount else totalPrice
                totalPriceTextView.text = String.format("Total: $%,.2f", adjustedTotal)

                selectedVoucherDiscount?.let { voucherDiscount ->
                    val accumulationAmount = adjustedTotal * voucherDiscount / 100.0
                    accumulationInfoTextView.text = String.format(
                        "You will accumulate $%,.2f in your account after using this voucher.",
                        accumulationAmount
                    )
                }
            }

            // Set listener for checkbox to recalculate total and accumulation
            applyDiscountCheckbox.setOnCheckedChangeListener { _, _ ->
                updateFinalTotalAndAccumulation()
            }

            totalPriceTextView.text = String.format("Total: $%,.2f", totalPrice)

            selectedVoucherTextView = findViewById(R.id.selectedVoucherTextView)

            // If clicking on the Select Voucher button, launch the SelectVoucherActivity to pick a voucher
            val selectVoucherButton: Button = findViewById(R.id.selectVoucherButton)
            selectVoucherButton.setOnClickListener {
                val intent = Intent(this@CheckoutActivity, SelectVoucherActivity::class.java)
                voucherResultLauncher.launch(intent)
            }

            val qrCodeButton: Button = findViewById(R.id.qrCodeButton)
            val nfcButton: Button = findViewById(R.id.nfcButton)

            qrCodeButton.setOnClickListener {
                genTag()
            }

            nfcButton.setOnClickListener {
                prepareNfcMessage()
            }


            // Helper for setting accumulation info from voucherResultLauncher
            this@CheckoutActivity.updateAccumulationInfo = {
                updateFinalTotalAndAccumulation()
            }
        }
    }

    // Function to handle the result from SelectVoucherActivity (receiving the picked voucher)
    private val voucherResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedVoucherUUID = result.data?.getStringExtra("selectedVoucherUuid")
            val selectedVoucherName = result.data?.getStringExtra("selectedVoucherName")
            selectedVoucherDiscount = result.data?.getIntExtra("selectedVoucherDiscount", 0)

            selectedVoucherTextView.text = "Selected Voucher: $selectedVoucherName - $selectedVoucherDiscount% Discount Accumulation"

            updateAccumulationInfo()
        }
    }

    // Function to generate the tag for the checkout
    private fun genTag() {
        if (shoppingCart.isEmpty() || totalPrice <= 0) {
            Toast.makeText(this, "Invalid checkout data!", Toast.LENGTH_SHORT).show()
            return
        }

        val userUUID = getStoredUserUUID(this)
        val userUUIDBytes = userUUID?.toByteArray(StandardCharsets.ISO_8859_1)
        val voucherBytes = selectedVoucherUUID?.toByteArray(StandardCharsets.ISO_8859_1)
        val accumulateDiscount = findViewById<CheckBox>(R.id.applyDiscountCheckbox).isChecked
        val accumulateDiscountByte = if (accumulateDiscount) 1.toByte() else 0.toByte()

        val cartBytes = shoppingCart.sumOf {
            1 + it.name.toByteArray(StandardCharsets.ISO_8859_1).size + 4 + 8
        }
        val voucherLength = if (voucherBytes != null) 1 + voucherBytes.size else 1

        val len = 4 + (userUUIDBytes?.size ?: 0) + 4 + cartBytes + voucherLength + 1

        val tagData = ByteBuffer.allocate(len).apply {
            putInt(userUUIDBytes?.size ?: 0)
            if (userUUIDBytes != null) put(userUUIDBytes)

            putInt(shoppingCart.size)
            for (item in shoppingCart) {
                val nameBytes = item.name.toByteArray(StandardCharsets.ISO_8859_1)
                put(nameBytes.size.toByte())
                put(nameBytes)
                putInt(item.quantity)
                putDouble(item.price)
            }

            if (voucherBytes != null) {
                put(voucherBytes.size.toByte())
                put(voucherBytes)
            } else {
                put(0.toByte())
            }
            put(accumulateDiscountByte)
        }.array()

        try {
            // Generate AES key
            val aesKey = ByteArray(16)
            SecureRandom().nextBytes(aesKey)
            val secretKeySpec = SecretKeySpec(aesKey, "AES")

            // Encrypt tag data with AES
            val cipherAES = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipherAES.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            val encryptedData = cipherAES.doFinal(tagData)

            // Encrypt AES key with RSA
            val cipherRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipherRSA.init(Cipher.ENCRYPT_MODE, priKey)
            val encryptedAESKey = cipherRSA.doFinal(aesKey)

            // Combine both
            val finalPayload = ByteBuffer.allocate(2 + encryptedAESKey.size + encryptedData.size).apply {
                putShort(encryptedAESKey.size.toShort())
                put(encryptedAESKey)
                put(encryptedData)
            }.array()

            startActivity(Intent(this, QRTagCartActivity::class.java).apply {
                putExtra("data", finalPayload)
                putExtra("price", String.format("%,.2f", totalPrice))
            })
            Toast.makeText(this, "Tag generated successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun prepareNfcMessage() {
        if (shoppingCart.isEmpty() || totalPrice <= 0) {
            Toast.makeText(this, "Invalid checkout data!", Toast.LENGTH_SHORT).show()
            return
        }

        val userUUID = getStoredUserUUID(this)
        val userUUIDBytes = userUUID?.toByteArray(StandardCharsets.ISO_8859_1)
        val voucherBytes = selectedVoucherUUID?.toByteArray(StandardCharsets.ISO_8859_1)
        val accumulateDiscount = findViewById<CheckBox>(R.id.applyDiscountCheckbox).isChecked
        val accumulateDiscountByte = if (accumulateDiscount) 1.toByte() else 0.toByte()

        val cartBytes = shoppingCart.sumOf {
            1 + it.name.toByteArray(StandardCharsets.ISO_8859_1).size + 4 + 8
        }
        val voucherLength = if (voucherBytes != null) 1 + voucherBytes.size else 1

        val len = 4 + (userUUIDBytes?.size ?: 0) + 4 + cartBytes + voucherLength + 1

        val tagData = ByteBuffer.allocate(len).apply {
            putInt(userUUIDBytes?.size ?: 0)
            if (userUUIDBytes != null) put(userUUIDBytes)

            putInt(shoppingCart.size)
            for (item in shoppingCart) {
                val nameBytes = item.name.toByteArray(StandardCharsets.ISO_8859_1)
                put(nameBytes.size.toByte())
                put(nameBytes)
                putInt(item.quantity)
                putDouble(item.price)
            }

            if (voucherBytes != null) {
                put(voucherBytes.size.toByte())
                put(voucherBytes)
            } else {
                put(0.toByte())
            }
            put(accumulateDiscountByte)
        }.array()

        try {
            // AES key
            val aesKey = ByteArray(16)
            SecureRandom().nextBytes(aesKey)
            val secretKeySpec = SecretKeySpec(aesKey, "AES")

            val cipherAES = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipherAES.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            val encryptedData = cipherAES.doFinal(tagData)

            val cipherRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipherRSA.init(Cipher.ENCRYPT_MODE, priKey)
            val encryptedAESKey = cipherRSA.doFinal(aesKey)

            val finalPayload = ByteBuffer.allocate(2 + encryptedAESKey.size + encryptedData.size).apply {
                putShort(encryptedAESKey.size.toShort())
                put(encryptedAESKey)
                put(encryptedData)
            }.array()

            // Create the NFC message
            nfcMessage = NdefMessage(
                arrayOf(
                    NdefRecord.createMime("application/vnd.com.example.checkout", finalPayload)
                )
            )

            Toast.makeText(this, "Ready to transmit over NFC! Please tap another device.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcAdapter?.let { adapter ->
            nfcMessage?.let { message ->
                val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE)
                val nfcIntentFilter = arrayOf(IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                    addDataType("application/vnd.com.example.checkout") // same MIME type
                })
                adapter.enableForegroundDispatch(this, pendingIntent, nfcIntentFilter, null)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (nfcMessage != null) {
            try {
                val nfcTag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                nfcTag?.let { tag ->
                    val ndef = Ndef.get(tag)
                    ndef?.connect()
                    ndef?.writeNdefMessage(nfcMessage)
                    ndef?.close()
                    Toast.makeText(this, "Data sent via NFC!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "NFC transmission failed: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }


}

// Function to retrieve the user's current total accumulated discount
private suspend fun getAccumulatedDiscount(act: Context, userUUID: String?): Double {
    // Using withContext to use a separate thread for network operations
    return withContext(Dispatchers.IO) {
        val encodedUUID = URLEncoder.encode(userUUID, StandardCharsets.UTF_8.toString()) // encode to prevent URL encoding issues
        val url = URL("$SERVER_URL/users/$encodedUUID")
        var urlConnection: HttpURLConnection? = null

        try {
            urlConnection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                useCaches = false
            }

            val response = if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                readStream(urlConnection.inputStream)
            } else {
                return@withContext 0.0
            }

            if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = JSONObject(response)
                val accumulatedDiscount = responseJson.getDouble("AccumulatedDiscount") // Retrieve the discount from the response sent by the server
                accumulatedDiscount
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(act, "Failed to retrieve discount", Toast.LENGTH_LONG).show()
                }
                0.0
            }
        } catch (e: IOException) {
            Log.e("NetworkError", "Network request failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(act, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            0.0
        } finally {
            urlConnection?.disconnect()
        }
    }
}

// Function to read the response stream
private fun readStream(inputStream: InputStream?): String {
    return inputStream?.bufferedReader().use { it?.readText() ?: "No response" }
}