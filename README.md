# 🛒 SupermarketApp

A local-first Android application designed to make supermarket shopping more convenient and autonomous. The app allows customers to manage their shopping experience, scan shopping lists, apply vouchers, and complete checkout using QR codes and NFC technology.

## ✨ Features

- **User Authentication** – Secure registration and login using cryptographic keys and nonce-based signature verification.
- **Product Management** – Browse products and manage a personal shopping list.
- **QR Code Integration** – Scan QR codes to import encrypted shopping lists.
- **NFC Integration** – Transfer and read shopping list information using NFC.
- **Shopping Cart** – Review products, quantities, and prices before checkout.
- **Voucher System** – Generate, manage, and apply discount vouchers.
- **Secure Checkout** – Server-side transaction validation and price calculation.
- **Purchase History** – Retrieve previous transactions.
- **Shopping Analytics** – Access purchase statistics, spending information, and frequently purchased products.

## 🛠️ Tech Stack

| Component | Technologies |
|---|---|
| Mobile Application | Kotlin, Android SDK |
| Backend | Node.js, Koa |
| Database | SQLite |
| QR Code Scanning | ZXing |
| Security | RSA, ECC, AES, Bcrypt |
| Build System | Gradle, Kotlin DSL |

## 🏗️ Architecture

The project follows a client-server architecture:

- **Android Client:** Handles the user interface, authentication, shopping lists, QR/NFC interactions, and checkout experience.
- **Backend Server:** Provides REST API endpoints for user management, products, vouchers, transactions, and analytics.
- **Database:** SQLite is used to persist user information, products, vouchers, and transaction records.

## 🚀 Getting Started

### Prerequisites

- Android Studio
- JDK 11 or higher
- Node.js
- An Android device or emulator
- NFC-compatible device for NFC functionality

### 1. Clone the repository

```bash
git clone https://github.com/JPsobral20/SupermarketApp.git
cd SupermarketApp
```

### 2. Configure the backend

```bash
cd nodeServer
npm install
```

Start the server using:

```bash
node server.js
```

The backend listens on port `8000`.

### 3. Run the Android application

Open the project in Android Studio, allow Gradle to synchronize, and run the application on an Android device or emulator.

**Note:** Configure the backend server URL in the Android application to match your development environment. An emulator and a physical device may require different network addresses.

## 📁 Project Structure

```text
SupermarketApp/
├── app/                    # Android application
│   └── src/main/
│       ├── java/            # Kotlin source code
│       └── res/             # Android resources
├── nodeServer/              # Backend server
│   ├── database/             # Database operations
│   ├── middleware/           # API routes and business logic
│   └── server.js             # Server entry point
├── gradle/                  # Gradle configuration
├── build.gradle.kts
└── settings.gradle.kts
```

## 🎯 Project Goals

This project explores how mobile technologies, cryptographic mechanisms, and contactless communication can improve the supermarket shopping experience.

It combines Android development with backend services and database integration to provide a more autonomous, convenient, and secure shopping process.
