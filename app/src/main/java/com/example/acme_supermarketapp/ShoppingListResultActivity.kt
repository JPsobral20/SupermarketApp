package com.example.acme_supermarketapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

// Class for the shopping list result page
class ShoppingListResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        setContentView(R.layout.activity_shopping_list_result)

        val isApproved = intent.getBooleanExtra("isApproved", false)
        val finalTotal = intent.getDoubleExtra("finalTotal", 0.0)
        val finalAccumulatedDiscount = intent.getDoubleExtra("finalAccumulatedDiscount", 0.0)
        val vouchersAdded = intent.getIntExtra("vouchersAdded", 0)

        val resultTextView: TextView = findViewById(R.id.resultTextView)
        val backgroundLayout: View = findViewById(R.id.resultLayout)

        val totalTextView: TextView = findViewById(R.id.totalTextView)
        val discountTextView: TextView = findViewById(R.id.discountTextView)
        val vouchersTextView: TextView = findViewById(R.id.vouchersTextView)

        if (isApproved) {
            resultTextView.text = "List Approved"
            backgroundLayout.setBackgroundColor(Color.GREEN)
        } else {
            resultTextView.text = "List Failed"
            backgroundLayout.setBackgroundColor(Color.RED)
        }

        totalTextView.text = "Total: €%.2f".format(finalTotal)
        discountTextView.text = "Accumulated Discount: €%.2f".format(finalAccumulatedDiscount)
        vouchersTextView.text = "New Vouchers: $vouchersAdded"

        // Delay 25 seconds, then go back to ScanShoppingListActivity
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, ScanShoppingListActivity::class.java)
            startActivity(intent)
            finish()
        }, 25000)
    }

}
