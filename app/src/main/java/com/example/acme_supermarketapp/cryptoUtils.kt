package com.example.acme_supermarketapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.crypto.Cipher

const val RSA_KEY_ALIAS = "my_rsa_key"
const val EC_KEY_ALIAS = "my_ec_key"

// Function to check if password is stored on the device
fun isPasswordStored(context: Context): Boolean {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    return sharedPreferences.contains("user_password")  // Check if password exists
}

// Function to retrieve the stored password on the device
fun getStoredPassword(context: Context): String? {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    return sharedPreferences.getString("user_password", null) // Return stored password or null if not found
}

// Function to generate and store RSA Key Pair
fun generateAndStoreRSAKeyPair(): PublicKey {
    val keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
    keyGen.initialize(
        KeyGenParameterSpec.Builder(
            RSA_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.RSAKeyGenParameterSpec(512, java.math.BigInteger("65537")))
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()
    )
    val keyPair = keyGen.genKeyPair()
    return keyPair.public // Retrieve public key
}

// Function to generate and store EC Key Pair
fun generateAndStoreECKeyPair(): PublicKey {
    val keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
    keyGen.initialize(
        KeyGenParameterSpec.Builder(
            EC_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .build()
    )
    val keyPair = keyGen.genKeyPair()
    return keyPair.public // Public key remains accessible
}

// Function to return the public key of a given alias
fun getPublicKey(alias: String): PublicKey? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    return keyStore.getCertificate(alias)?.publicKey
}

// Function to return the private key of a given alias
fun getPrivateKey(alias: String): PrivateKey? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
    return entry?.privateKey
}

// Function to encrypt data with RSA
fun signChallengeWithRSA(data: String): String? {
    val privateKey = getPrivateKey(RSA_KEY_ALIAS) ?: return null
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(data.toByteArray())

    val signedBytes = signature.sign()

    return Base64.encodeToString(signedBytes, Base64.DEFAULT)
}

// Function to store the supermarket public key securely
fun storeSupermarketPublicKey(context: Context, publicKey: String) {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    with(sharedPreferences.edit()) {
        putString("supermarket_public_key", publicKey)
        apply()
    }
}

// Function to retrieve the stored supermarket public key
fun getStoredSupermarketPublicKey(context: Context): String? {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    return sharedPreferences.getString("supermarket_public_key", null)
}

// Function to sign data with EC
fun signDataWithEC(data: String): String? {
    val privateKey = getPrivateKey(EC_KEY_ALIAS) ?: return null
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(privateKey)
    signature.update(data.toByteArray())

    val signedBytes = signature.sign()

    return Base64.encodeToString(signedBytes, Base64.DEFAULT)
}

// Function to store the password securely
fun storePasswordSecurely(context: Context, password: String) {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    with(sharedPreferences.edit()) {
        putString("user_password", password)
        apply()
    }
}

// Function to store the supermarkets' UUID securely
fun storeUUIDSecurely(context: Context, uuidBase64: String) {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Store the UUID securely in EncryptedSharedPreferences
    with(sharedPreferences.edit()) {
        putString("user_uuid", uuidBase64)
        apply()
    }
}

// Function to retrieve the stored user's UUID
fun getStoredUserUUID(context: Context): String? {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    return sharedPreferences.getString("user_uuid", null)
}

// Function to store the admin status on the device
fun saveAdminStatus(context: Context, isAdmin: Boolean) {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    sharedPreferences.edit().putBoolean("isAdmin", isAdmin).apply()
}

// Function to check if the user is an admin
fun isAdmin(context: Context): Boolean {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    return sharedPreferences.getBoolean("isAdmin", false)
}
