package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// Class for the home page of the app
class HomePageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_home_page)

        val isAdmin = isAdmin(this)
        val buttonContainer: GridLayout = findViewById(R.id.buttonContainer)

        if (isAdmin) {
            val genQRCodeButton = addButton(R.layout.button_gen_qr, buttonContainer)
            val generateVoucherButton = addButton(R.layout.button_gen_voucher, buttonContainer)
            val terminalButton = addButton(R.layout.button_scan, buttonContainer)

            genQRCodeButton.setOnClickListener {
                startActivity(Intent(this, QRCodeGen::class.java))
            }

            generateVoucherButton.setOnClickListener {
                startActivity(Intent(this, GenerateVoucherActivity::class.java))
            }

            terminalButton.setOnClickListener {
                startActivity(Intent(this, ScanShoppingListActivity::class.java))
            }
        } else {
            val shoppingCartButton = addButton(R.layout.button_cart, buttonContainer)
            val checkVouchersButton = addButton(R.layout.button_vouchers, buttonContainer)
            val pastTransactionsButton = addButton(R.layout.button_transactions, buttonContainer)
            val analyticsButton = addButton(R.layout.button_analytics, buttonContainer)

            shoppingCartButton.setOnClickListener {
                startActivity(Intent(this, ShoppingCartActivity::class.java))
            }

            checkVouchersButton.setOnClickListener {
                startActivity(Intent(this, VoucherActivity::class.java))
            }

            pastTransactionsButton.setOnClickListener {
                startActivity(Intent(this, TransactionActivity::class.java))
            }

            analyticsButton.setOnClickListener {
                startActivity(Intent(this, AnalyticsActivity::class.java))
            }
        }
    }

    // Helper function to inflate button and add to container
    private fun addButton(layoutId: Int, container: GridLayout): Button {
        val button = layoutInflater.inflate(layoutId, container, false) as Button
        container.addView(button)
        return button
    }
}