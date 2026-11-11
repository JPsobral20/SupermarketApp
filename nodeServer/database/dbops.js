'use strict';
const path = require('path');
const crypto = require('crypto')

var db;

(async () => {
  const { Database } = await import('sqlite-async');
  db = await Database.open(path.resolve('database/database.db'));
  console.log('connected to database.db');
})();

class DBOps {
  async dbTest(id) {
    const result = await db.get("select Name from Users where Id=?", [id]);
    console.log(result);
    return result.Name;
  }

// USER TABLE
  async getUserById(id) {
    var result;
    try {
      result = await db.get("SELECT * FROM Users WHERE UUID=?;", [id]);  // one object with the selected fields
      if (result == null)
        result = {};
    }
    catch (err) {
      result = err;
    }
    return result;
  }

  async setUserVoucherReward(id, moneySpentUntilReward) {
    let result;
    try {
      result = await db.run("UPDATE Users SET VoucherReward=? WHERE UUID=?;", [moneySpentUntilReward, id]);
      return result;
    } catch (err) {
      console.error("Database Error:", err);
      return err;
    }
  }

  async getPublicRSAKeyById(id) {
    var result;
    try {
      result = await db.get("SELECT PublicRSA FROM Users WHERE UUID=?;", id);
      if (result == null)
        result = {};
    }
    catch (err) {
      result = err;
    }
    return result?.PublicRSA || "";
  }

  async getPublicECKeyByUsername(username) {
    var result;
    try {
      result = await db.get("SELECT PublicEC FROM Users WHERE Username=?;", username);
      if (result == null)
        result = {};
    }
    catch (err) {
      result = err;
    }
    return result?.PublicEC || "";
  }

  async addNewUser(uuid, name, username, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC) {
    var result;
    try {
        result = await db.run(
          "INSERT INTO Users (UUID, Name, Username, CardType, CardExpMonth, CardExpYear, CardNumber, PublicRSA, PublicEC) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);",
          [uuid, name, username, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC]
        );
        if (result.changes === 0)
          result.lastID = 0;
    }
    catch (err) {
      result = err;
    }
    return result;
  }

  async retrieveUserKeys(username) {
    var result;
    try {
        result = await db.get(
          "SELECT PublicRSA, PublicEC FROM Users WHERE Username=?;",
          username
        );
        if (!result) {  // If no user is found
          return { lastID: 0 };
      }
    }
    catch (err) {
      result = err;
    }
    return result;
  }

  async deleteUserById(id) {
    var result;
    try {
      result = await db.run('DELETE FROM Users WHERE ID=?;', [id]);
    }
    catch (err) {
      result = err;
    }
    return result;
  }

  async updateUserById(id, name, username, password, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC) {
    var result;
    try {
      result = await db.run(
        "UPDATE Users SET Name = ?, Username = ?, Password = ?, CardType = ?, CardExpMonth = ?, CardExpYear = ?, CardNumber = ?, PublicRSA = ?, PublicEC = ? WHERE ID = ?;",
        [name, username, password, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC, id]
      );
    }
    catch (err) {
      result = err;
    }
    return result;
  }

// PRODUCT TABLE
  async addNewProduct(uuid, name, price) {
      var result;
      try {
        result = await db.run('INSERT INTO Product (UUID, Name, Price) VALUES (?, ?, ?);', [uuid, name, price]);
        if (result.changes === 0)
          result.lastID = 0;
      }
      catch (err) {
        result = err;
      }
      return result;
  }

// ADMIN TABLE
   async getAdminByUsername(username) {
    var result;
    try {
      result = await db.get("SELECT * FROM Admin WHERE Username=?", [username]);

      if (result == null)
        result = {};
    }
    catch (err) {
      result = err;
    }
    return result;
  }

// VOUCHER TABLE
  async getAllVouchers() {
    let result;
    try {
      result = await db.all("SELECT * FROM Voucher;");
      if (!result) result = [];
    } catch (err) {
      result = err;
    }
    return result;
  }

  async setUserAccumulatedDiscount(id, value) {
    let result;
    try {
      result = await db.run("UPDATE Users SET AccumulatedDiscount=? WHERE UUID=?;", [value, id]);
      return result;
    } catch (err) {
      console.error("Database Error:", err);
      return err;
    }
  }

  async setVoucherUsed(id) {
    let result;
    try {
      result = await db.run("UPDATE Voucher SET Used=1 WHERE UUID=?;", [id]);
      return result;
    } catch (err) {
      console.error("Database Error:", err);
      return err;
    }
  }

  async addNewVoucher(voucherUUID, username, voucherName, discountPercentage) {
    let result;
    try {
      result = await db.run(
          'INSERT INTO Voucher (UUID, Name, Percentage) VALUES (?, ?, ?);',
          [voucherUUID, voucherName, discountPercentage]
      );
      if (result.changes === 0) result = { lastID: 0 };
      else {
          let user = await db.get('SELECT UUID FROM Users WHERE Username = ?;', [username]);
          if (user) {
            let userVoucherResult = await db.run(
                'INSERT INTO UserVoucher (UserID, VoucherID) VALUES (?, ?);',
                [user.UUID, voucherUUID]
            );
            if (userVoucherResult.changes === 0) result = { lastID: 0 };
          }
        }
    } catch (err) {
      console.error("Database Error:", err);
        result = err;
    }
    return result;
  }

// PAYMENT TABLE
  async addNewPayment(userUUID, voucherUUID, products, applyDiscount) {
    try {
      const user = await this.getUserById(userUUID);
      let accumulatedDiscount = user.AccumulatedDiscount;
      let total = products.reduce((acc, item) => acc + item.price * item.quantity, 0);
      let voucherUsed = false;

      // Apply accumulated discount
      if (applyDiscount) {
        if (accumulatedDiscount > total) {
          accumulatedDiscount -= total;
          total = 0;
        } else {
          total -= accumulatedDiscount;
          accumulatedDiscount = 0;
        }
        accumulatedDiscount = Math.round(accumulatedDiscount * 100) / 100;
        await this.setUserAccumulatedDiscount(userUUID, accumulatedDiscount);
      }

      // Apply voucher (if any)
      if (voucherUUID) {
        const userVouchers = await this.getUserVouchers(userUUID, false);
        const ownsVoucher = userVouchers.some(v => v.UUID === voucherUUID);
        if (!ownsVoucher) throw new Error("User does not own the voucher.");

        await this.setVoucherUsed(voucherUUID);

        let newAccumulated = accumulatedDiscount + total * 0.15;
        newAccumulated = Math.round(newAccumulated * 100) / 100;
        await this.setUserAccumulatedDiscount(userUUID, newAccumulated);
      }

      // Handle voucher rewards
      const currentReward = user.VoucherReward;
      const rewardSum = total + currentReward;
      const vouchersToAdd = Math.floor(rewardSum / 100);
      const newReward = parseFloat(rewardSum - vouchersToAdd * 100).toFixed(2);
      await this.setUserVoucherReward(userUUID, newReward);

      for (let i = 0; i < vouchersToAdd; i++) {
        const newVoucherUUID = crypto.randomBytes(16).toString("base64");
        const voucherName = `Voucher(${i + 1})`;
        await this.addNewVoucher(newVoucherUUID, user.Username, voucherName, 15);
      }

      // Save payment
      const paymentUUID = crypto.randomUUID();
      await db.run(
        `INSERT INTO Payment (UUID, Total) VALUES (?, ?);`,
        [paymentUUID, total]
      );      

      // Link user to payment
      await db.run(
        `INSERT INTO UserPayment (UserID, PaymentID) VALUES (?, ?);`,
        [userUUID, paymentUUID]
      );

      // Save transaction
      const transactionUUID = crypto.randomUUID();
      await db.run(
        `INSERT INTO Transactions (UUID, UserID, PaymentID) VALUES (?, ?, ?);`,
        [transactionUUID, userUUID, paymentUUID]
      );

      // Link products to transaction
      for (const item of products) {
        const product = await db.get(`SELECT UUID FROM Product WHERE Name = ?;`, [item.name]);
        if (!product) throw new Error(`Product "${item.name}" not found.`);

        await db.run(
          `INSERT INTO TransactionProduct (TransactionID, ProductID, Quantity, PriceAtPurchase)
          VALUES (?, ?, ?, ?);`,
          [transactionUUID, product.UUID, item.quantity, item.price]
        );
      }

      const userAfter = await this.getUserById(userUUID);
      return {
        transactionId: transactionUUID,
        total,
        finalAccumulatedDiscount: userAfter.AccumulatedDiscount,
        vouchersAdded: vouchersToAdd
      };

    } catch (err) {
      console.error("Database Error:", err);
      return { error: err.message };
    }
  }

// USER-PRODUCT RELATIONSHIP
  // Get full product details for a specific user
  async getUserProductByUser(userId) {
    let result;
    try {
      result = await db.all(`
        SELECT Product.UUID, Product.Name, Product.Price
        FROM Product
        JOIN UserProduct ON Product.UUID = UserProduct.ProductID
        WHERE UserProduct.UserID = ?
      `, [userId]);
    } catch (err) {
      result = err;
    }
    return result;
  }

  async deleteUserProduct(userId, productId) {
    return await db.run(`
      DELETE FROM UserProduct
      WHERE UserID = ? AND ProductID = ?
    `, [userId, productId]);
  }


   async addUserProduct(userId, productId) {
     let result;
     try {
       result = await db.run('INSERT INTO UserProduct (UserID, ProductID) VALUES (?, ?);', [userId, productId]);
     } catch (err) {
       result = err;
     }
     return result;
   }

   async deleteUserProduct(userId, productId) {
     let result;
     try {
       result = await db.run('DELETE FROM UserProduct WHERE UserID=? AND ProductID=?;', [userId, productId]);
     } catch (err) {
       result = err;
     }
     return result;
   }

// USER-VOUCHER RELATIONSHIP
  async getUserVouchers(id, isUsed) {
    let result;
    try {
      result = await db.all("SELECT V.* FROM UserVoucher UV JOIN Voucher V ON UV.VoucherID = V.UUID WHERE UV.UserID = ? AND V.Used = ?;",
         [id, isUsed]);

      if (!result) result = [];
    } catch (err) {
      console.error("Database Error:", err);
      result = [];
    }
    return result;
  }

  async getAllUsersVouchers() {
    let result;
    try {
      result = await db.all("SELECT * FROM UserVoucher;");
      if (!result) result = [];
    } catch (err) {
      result = err;
    }
    return result;
  }

  async getAllUserVouchers(userId) {
    let result;
    try {
      result = await db.all(
        "SELECT V.* FROM UserVoucher UV JOIN Voucher V ON UV.VoucherID = V.UUID WHERE UV.UserID = ?;",
        [userId]
      );
  
      if (!result) result = [];
    } catch (err) {
      console.error("Database Error:", err);
      result = [];
    }
    return result;
  }

// TRANSACTION RELATIONSHIP
async getUserTransactions(userId) {
  try {
    // Get all transactions for the user
    const transactions = await db.all(`
      SELECT 
        t.UUID AS transactionId,
        p.Total AS total,
        t.PurchaseDate AS date,
        p.VoucherUsed AS usedVoucher,
        p.AccumulatedDiscount AS accumulatedDiscount
      FROM Transactions t
      JOIN Payment p ON t.PaymentID = p.UUID
      WHERE t.UserID = ?;
    `, [userId]);

    // For each transaction, fetch the associated products
    for (const transaction of transactions) {
      const products = await db.all(`
        SELECT 
          pr.Name AS name,
          tp.Quantity AS quantity,
          tp.PriceAtPurchase AS price
        FROM TransactionProduct tp
        JOIN Product pr ON tp.ProductID = pr.UUID
        WHERE tp.TransactionID = ?;
      `, [transaction.transactionId]);

      transaction.products = products;
    }

    return transactions;
  } catch (err) {
    console.error("Database Error:", err);
    return [];
  }
}


// User analytics
async getTotalSpent(userId) {
  const result = await db.get(`
    SELECT SUM(P.Total) as total FROM Transactions T
    JOIN Payment P ON T.PaymentID = P.UUID
    WHERE T.UserID = ?;
  `, [userId]);
  return result?.total || 0;
}

async getPurchaseCount(userId) {
  const result = await db.get(`
    SELECT COUNT(*) as count FROM Transactions WHERE UserID = ?;
  `, [userId]);
  return result?.count || 0;
}

async getAvgSpend(userId) {
  const result = await db.get(`
    SELECT AVG(P.Total) as avg FROM Transactions T
    JOIN Payment P ON T.PaymentID = P.UUID
    WHERE T.UserID = ?;
  `, [userId]);
  return result?.avg || 0;
}

async getTopProducts(userId) {
  return await db.all(`
    SELECT P.Name, SUM(TP.Quantity) as Quantity
    FROM Transactions T
    JOIN TransactionProduct TP ON T.UUID = TP.TransactionID
    JOIN Product P ON TP.ProductID = P.UUID
    WHERE T.UserID = ?
    GROUP BY TP.ProductID
    ORDER BY Quantity DESC
    LIMIT 5;
  `, [userId]);
}

async getTotalDiscount(userId) {
  const result = await db.get(`
    SELECT SUM(P.AccumulatedDiscount) as total FROM Transactions T
    JOIN Payment P ON T.PaymentID = P.UUID
    WHERE T.UserID = ?;
  `, [userId]);
  return result?.total || 0;
}

async getVouchersUsed(userId) {
  const result = await db.get(`
    SELECT COUNT(*) as used FROM UserVoucher UV
    JOIN Voucher V ON UV.VoucherID = V.UUID
    WHERE UV.UserID = ? AND V.Used = 1;
  `, [userId]);
  return result?.used || 0;
}
}

module.exports = new DBOps();