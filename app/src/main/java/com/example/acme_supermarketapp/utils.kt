package com.example.acme_supermarketapp

import android.os.Parcel
import android.os.Parcelable
import org.json.JSONObject

const val BASE_ADDRESS = "10.0.2.2" // Emulator address
//const val BASE_ADDRESS = "192.168.1.117"
const val PORT = "8000"
const val SERVER_URL = "http://$BASE_ADDRESS:$PORT" // Endpoint to fetch all users

data class CardInfo(
    val cardType: String,
    val cardExpirationMonth: String,
    val cardExpirationYear: String,
    val cardNumber: String
)

data class Voucher(
    val uuid: String,
    val name: String,
    val discountPercentage: Int
)

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    var quantity: Int = 1
)

// Data class for Transaction and Product
data class Transaction(
    val date: String,
    val total: Double,
    val voucherUsed: Boolean,
    val accumulatedDiscount: Double,
    val products: List<ShoppingCartItem>
)


data class ShoppingCartItem(
    val name: String,
    var quantity: Int,
    val price: Double
) : Parcelable { // Parceable to allow passing the shopping cart info through activities
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(quantity)
        parcel.writeDouble(price)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShoppingCartItem> {
        override fun createFromParcel(parcel: Parcel): ShoppingCartItem {
            return ShoppingCartItem(parcel)
        }

        override fun newArray(size: Int): Array<ShoppingCartItem?> {
            return arrayOfNulls(size)
        }
    }
}

data class ClientAnalytics(
    val totalSpent: Double,
    val purchaseCount: Int,
    val avgSpend: Double,
    val topProducts: List<Pair<String, Int>>,
    val totalDiscount: Double,
    val vouchersUsed: Int
) {
    companion object {
        fun fromJson(json: JSONObject): ClientAnalytics {
            val productsArray = json.getJSONArray("topProducts")
            val products = mutableListOf<Pair<String, Int>>()
            for (i in 0 until productsArray.length()) {
                val obj = productsArray.getJSONObject(i)
                products.add(obj.getString("Name") to obj.getInt("Quantity"))
            }

            return ClientAnalytics(
                totalSpent = json.getDouble("totalSpent"),
                purchaseCount = json.getInt("purchaseCount"),
                avgSpend = json.getDouble("avgSpend"),
                topProducts = products,
                totalDiscount = json.getDouble("totalDiscount"),
                vouchersUsed = json.getInt("vouchersUsed")
            )
        }
    }
}
