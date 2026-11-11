# 🛒 ACME Supermarket App

A **local-first** Android supermarket application enabling customers to purchase products without staff assistance. Combines security, convenience, and seamless checkout using QR codes and NFC technology.

---

## ✨ Key Features

- **Reactive & Seamless UI** – Smooth, responsive interface built in Kotlin
- **Secure Authentication** – Encrypted local storage with server-side validation for enhanced security
- **Smart Shopping** – Scan product QR codes to add items to your virtual basket
- **Voucher System** – Apply discount vouchers at checkout for reduced pricing
- **Flexible Checkout** – Complete purchases via QR code scan or NFC technology
- **Server Validation** – Critical operations verified with the supermarket server for security and accuracy

> For comprehensive technical details, see the [Project Report](./CPM%20Report%20-%20Supermarket.pdf).

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| **Mobile App** | Kotlin (Android) |
| **Backend** | Node.js + Express |
| **Database** | SQL |
| **Authentication** | Encrypted Local Storage + Server Validation |

---

## 📸 Screenshots

### Main Screen
<img width="412" height="838" alt="Main Shopping Screen" src="https://github.com/user-attachments/assets/8b59bc1e-c850-4b1f-b647-6b7e33be6a4a" />

### Login & Registration
<img width="393" height="815" alt="Register Page" src="https://github.com/user-attachments/assets/c1c21563-a00a-4b6c-a0ba-b8def4b03175" />

### Shopping Cart
<img width="377" height="806" alt="Basket Page" src="https://github.com/user-attachments/assets/26d53687-56a0-4b88-8baf-e6ea253db47a" />

### Checkout
<img width="369" height="796" alt="Checkout Page" src="https://github.com/user-attachments/assets/1cdb8e99-b7d5-4066-b6b3-143d9ded0162" />

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest version)
- JDK 11 or higher
- Node.js 14+ (for backend server)
- Gradle 7.0+

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ACME_SupermarketApp
   ```

2. **Build the Android app**
   ```bash
   ./gradlew build
   ```

3. **Set up the backend server**
   ```bash
   cd nodeServer
   npm install
   npm start
   ```

4. **Deploy to device/emulator**
   ```bash
   ./gradlew installDebug
   ```

---

## 📁 Project Structure

```
ACME_SupermarketApp/
├── app/                          # Android Application
│   ├── src/main/
│   │   ├── java/                 # Kotlin source code
│   │   └── res/                  # UI resources (layouts, drawables, values)
│   └── build.gradle.kts
├── nodeServer/                   # Backend Server
│   ├── server.js                 # Express server entry point
│   ├── database/                 # Database operations
│   ├── middleware/               # API routes & middleware
│   └── package.json
└── gradle/                       # Gradle configuration
```

---

## 🔒 Security Features

- **Encrypted Authentication** – User credentials securely stored and transmitted
- **Server Validation** – All critical transactions verified server-side
- **Secure Checkout** – Final prices confirmed with server to prevent tampering
- **QR/NFC Support** – Contactless, tamper-proof transaction initiation

---

---

## 👤 Contact

Email: rafael2003t.18@gmail.com
