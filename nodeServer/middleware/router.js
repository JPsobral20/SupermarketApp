const DB = require('../database/dbops');
const KoaRouter = require('koa-router');
const crypto = require("crypto");
const { log } = require('console');
const bcrypt = require('bcrypt');

const router = new KoaRouter();

router
  .get('/users', allUsers)
  .post('/get_nonce', getNonce)
  .post('/get_voucher_nonce', getVoucherNonce)
  .post('/get_analytics_nonce', getAnalyticsNonce)
  .post('/login_user', loginUser)
  .get('/users/:id', oneUser)
  .post('/users', addUser)
  .delete('/users/:id', delUser)
  .post('/userProduct/:id', delUserProd)
  .put('/users/:id', changeUser)
  .post('/vouchers', addVoucher)
  .get('/vouchers', getVouchers)
  .post('/vouchers/:id', getUserVouchers)
  .get('/vouchersUsers', getAllUsersVouchers)
  .post('/checkout', addPayment)
  .post('/admin', checkAdmin)
  .post('/products', addProduct)
  .post('/addShopList', addUserProduct)
  .get('/getShopList/:id', getUserProductByUser)
  .post('/get_transaction_nonce', getTransactionNonce)
  .post('/pastTransactions/:id', getUserPastTransactions)
  .post('/analytics/:id', getUserAnalytics);


const nonces = new Map();
const voucherNonces = new Map();
const transactionNonces = new Map();
const analyticsNonces = new Map();

async function getNonce(ctx) {
    console.log('in getNonce()');
    const { username } = ctx.request.body;

    if (!username) {
        ctx.status = 400;
        ctx.body = { error: "Username required" };
        return;
    }

    // Generate a random 32-byte nonce
    const nonce = crypto.randomBytes(32).toString('hex');
    nonces.set(username, nonce); // Store nonce temporarily

    ctx.body = { nonce };
}

async function getVoucherNonce(ctx) {
  console.log('in getVoucherNonce()');
  const { userUUID } = ctx.request.body;

  if (!userUUID) {
      ctx.status = 400;
      ctx.body = { error: "userUUID required" };
      return;
  }

  // Generate a random 32-byte nonce
  const nonce = crypto.randomBytes(32).toString('hex');
  voucherNonces.set(userUUID, nonce); // Store nonce temporarily

  ctx.body = { nonce };
}

async function getVoucherNonceWithId(ctx) {
  console.log('in getNonceWithId()');
  const { userUUID } = ctx.request.body;

  if (!userUUID) {
      ctx.status = 400;
      ctx.body = { error: "userUUID required" };
      return;
  }

  const result = await DB.getUserById(userUUID);

  const username = result.Username;

  // Generate a random 32-byte nonce
  const nonce = crypto.randomBytes(32).toString('hex');
  nonces.set(username, nonce); // Store nonce temporarily

  ctx.body = { nonce };
}

//USERS TABLE
async function allUsers(ctx) {
  console.log('in allUsers()');
  var result = await DB.getAll();
  if ('errno' in result) {
    result = [];
    ctx.status = 404;            // means not found
  }
  ctx.body = result;
}

async function oneUser(ctx) {
  console.log('in oneUser()');
  const result = await DB.getUserById(ctx.params.id);
  if (Object.keys(result).length === 0 || 'errno' in result) {
    ctx.status = 404; // Not Found
    ctx.body = {};
  } else {
    ctx.body = result;
  }
}

async function addUser(ctx) {
  const { name, username, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC, signedNonce } = ctx.request.body;

  if (!name || !username || !cardType || !cardExpMonth || !cardExpYear || !cardNumber || !publicRSA || !publicEC || !signedNonce) {
      ctx.status = 400;
      ctx.body = { error: "All fields are required" };
      return;
  }

  const nonce = nonces.get(username);
  if (!nonce) {
      ctx.status = 400;
      ctx.body = { error: "Nonce expired or invalid" };
      return;
  }

  if (!verifyECSignature(nonce, signedNonce, publicEC)) {
      ctx.status = 401;
      ctx.body = { error: "Invalid signature" };
      return;
  }

  nonces.delete(username); // Remove nonce after use

  const uuid = crypto.randomBytes(16).toString('base64');

  const result = await DB.addNewUser(uuid, name, username, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC);
  if ('errno' in result || result.lastID === 0) {
      ctx.status = 400;
      ctx.body = { error: "Username already exists. Pick another one" };
  } else {
      ctx.body = {
          UUID: uuid,
          Id: result.lastID,
          Name: name,
          Username: username,
          CardType: cardType,
          CardExpMonth: cardExpMonth,
          CardExpYear: cardExpYear,
          CardNumber: cardNumber,
          PublicRSA: publicRSA,
          PublicEC: publicEC
      };
  }
}

async function loginUser(ctx) {
  const { username, signedNonce } = ctx.request.body;

  if (!username || !signedNonce) {
      ctx.status = 400;
      ctx.body = { error: "All fields are required" };
      return;
  }

  const nonce = nonces.get(username);

  if (!nonce) {
      ctx.status = 400;
      ctx.body = { error: "Nonce expired or invalid" };
      return;
  }

  const publicEC  = await DB.getPublicECKeyByUsername(username);

  if (publicEC.length === 0) {
      ctx.status = 400;
      ctx.body = { error: "User doesn't have publicEC key stored in server" };
  }

  if (!verifyECSignature(nonce, signedNonce, publicEC)) {
      ctx.status = 401;
      ctx.body = { error: "Invalid signature" };
      return;
  }

  nonces.delete(username); // Remove nonce after use

  const result = await DB.retrieveUserKeys(username);

  if ('errno' in result || result.lastID === 0) {
      ctx.status = 400;
      ctx.body = {};
  } else {
      ctx.body = {
          publicRSA: result.PublicRSA,
          publicEC: result.publicEC,
      };
  }
}

async function delUser(ctx) {
  console.log('in delUser()');
  const result = await DB.deleteUserById(ctx.params.id);
  if ('errno' in result || result.changes === 0) {
    ctx.status = 404; // Not Found
  }
  ctx.body = {};
}

async function changeUser(ctx) {
  console.log('in changeUser()');
  const { name, username, password, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC } = ctx.request.body;

  if (!name || !username || !password || !cardType || !cardExpMonth || !cardExpYear || !cardNumber || !publicRSA || !publicEC) {
    ctx.status = 400; // Bad Request
    ctx.body = { error: "All fields are required (name, username, password, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC)" };
    return;
  }

  const result = await DB.updateUserById(ctx.params.id, name, username, password, cardType, cardExpMonth, cardExpYear, cardNumber, publicRSA, publicEC);
  if ('errno' in result || result.changes === 0) {
    ctx.status = 404; // Not Found
    ctx.body = {};
  } else {
    ctx.body = {
      Id: result.lastID,
      Name: name,
      Username: username,
      Password: password,
      CardType: cardType,
      CardExpMonth: cardExpMonth,
      CardExpYear: cardExpYear,
      CardNumber: cardNumber,
      PublicRSA: publicRSA,
      PublicEC: publicEC
    };
  }
}

// PRODUCT TABLE
async function addProduct(ctx) {
  const { uuid, productName, price } = ctx.request.body;
  if (!uuid || !productName || !price) {
    ctx.status = 400;
    ctx.body = { error: "UUID, Name and Price are required" };
    return;
  }
  const result = await DB.addNewProduct(uuid, productName, price);
  if ('errno' in result || result.lastID === 0) {
    ctx.status = 400;
    ctx.body = {};
  } else {
    ctx.body = { Id: result.lastID, UUID:uuid, Name:productName, Price: price };
  }
}


async function addPayment(ctx) {

  const {
    userUUID,
    products, // array of { name: name, price: price, quantity: quantity }
    voucherUUID,
    useAccumulatedDiscount
  } = ctx.request.body;

  if (!userUUID || !products) {
    ctx.status = 400;
    ctx.body = { error: "Missing required fields" };
    return;
  }

  const result = await DB.addNewPayment(userUUID, voucherUUID, products, useAccumulatedDiscount);

  console.log("result:", result);

  if ('errno' in result || result.changes === 0) {
    ctx.status = 404;
    ctx.body = {};
  } else {
    ctx.status = 200;
    ctx.body = {
      Id: result.lastID,                           
      Total: result.total,                           
      FinalAccumulatedDiscount: result.finalAccumulatedDiscount,
      VouchersAdded: result.vouchersAdded,
    };
  }
}

// ADMIN TABLE
async function checkAdmin(ctx) {
  const { username, password } = ctx.request.body;
  if (!username || !password) {
    ctx.status = 400;
    ctx.body = { error: "Username and Password are required" };
    return;
  }

  console.log(password)
  const result = await DB.getAdminByUsername(username);
  
  if (!result || !result.Password) {
    ctx.status = 400;
    ctx.body = { error: "User not found or invalid credentials" };
    return;
  }

  console.log("result:", result)

  // Compare the provided password with the hashed password
  const passwordMatch = await bcrypt.compare(password, result.Password);

  console.log("password:", passwordMatch)

  ctx.body = { passwordMatch };
}

// VOUCHER TABLE
async function getVouchers(ctx) {
  const result = await DB.getAllVouchers();

  if ('errno' in result || result.lastID === 0) {
    ctx.status = 400;
    ctx.body = {error: "Could not get the vouchers"};
  }
  else {
    ctx.status = 200;
    ctx.body = { vouchers: result };
  }
}


async function getUserVouchers(ctx) {
  console.log('in getUserVouchers()');
  const { signedNonce } = ctx.request.body;
  const userUUID = ctx.params.id;

  const voucherNonce = voucherNonces.get(userUUID);
  const publicRSA  = await DB.getPublicRSAKeyById(userUUID);

  if (!verifyRSASignature(signedNonce, publicRSA, voucherNonce)) {
    ctx.status = 401;
    ctx.body = { error: "Invalid signature" };
    return;
  }

  const usedVoucher = true;

  try {
    const usedUserVouchers = await DB.getUserVouchers(ctx.params.id, usedVoucher);
    const unusedUserVouchers = await DB.getUserVouchers(ctx.params.id, !usedVoucher);

    voucherNonces.delete(userUUID);

    ctx.status = 200;
    ctx.body = { 
        usedVouchers: usedUserVouchers || [], 
        unusedVouchers: unusedUserVouchers || [] 
    };
  } catch (error) {
      console.error("Error fetching vouchers:", error);
      ctx.status = 500;
      ctx.body = { error: "Internal Server Error" };
  }
}

async function getAllUsersVouchers(ctx) {
  const result = await DB.getAllUsersVouchers();

  if ('errno' in result || result.lastID === 0) {
    ctx.status = 400;
    ctx.body = {error: "Could not get the vouchers"};
  }
  else {
    ctx.status = 200;
    ctx.body = { vouchers: result };
  }
}

async function addVoucher(ctx) {
  const { username, voucherName, discountPercentage } = ctx.request.body;

  if (!username || !voucherName || !discountPercentage) {
    ctx.status = 400;
    ctx.body = { error: "Username and VoucherName are required" };
    return;
  }

  const voucherUUID = crypto.randomBytes(16).toString('base64');

  const result = await DB.addNewVoucher(voucherUUID, username, voucherName, discountPercentage);

  if ('errno' in result || result.lastID === 0) {
    ctx.status = 400;
    ctx.body = {error: "Could not add the voucher"};
  }
  else {
    ctx.body = { Id: result.lastID, VoucherName: voucherName, DiscountPercentage: discountPercentage };
  }
}

// USER-PRODUCT RELATIONSHIP
async function getUserProductByUser(ctx) {
    try {
      const userProduct = await DB.getUserProductByUser(ctx.params.id);
      console.log(userProduct);
      ctx.status = 200;
      ctx.body = {
          products: userProduct || []
      };
    } catch (error) {
        console.error("Error fetching vouchers:", error);
        ctx.status = 500;
        ctx.body = { error: "Internal Server Error" };
    }
}

async function delUserProd(ctx) {
  const userId = ctx.params.id;
  const { productId } = ctx.request.body;

  try {
    await DB.deleteUserProduct(userId, productId);
    ctx.status = 200;
    ctx.body = { message: 'Product removed successfully' };
  } catch (error) {
    console.error("Error deleting product:", error);
    ctx.status = 500;
    ctx.body = { error: "Internal Server Error" };
  }
}

async function addUserProduct(ctx) {
  const { userId, productId } = ctx.request.body;
  console.log(userId);
  if (!userId || !productId) {
    ctx.status = 400;
    ctx.body = { error: "UserID and ProductID are required" };
    return;
  }
  const result = await DB.addUserProduct(userId, productId);
  if ('errno' in result) {
    ctx.status = 400;
    ctx.body = { error: "Could not add the user-product relationship" };
  } else {
    ctx.body = { UserID: userId, ProductID: productId };
  }
}

// USER-TRANSACTION RELATIONSHIP
async function getTransactionNonce(ctx) {
  console.log('in getTransactionNonce()');
  const { userUUID } = ctx.request.body;

  if (!userUUID) {
      ctx.status = 400;
      ctx.body = { error: "userUUID required" };
      return;
  }

  // Generate a random 32-byte nonce
  const nonce = crypto.randomBytes(32).toString('hex');
  transactionNonces.set(userUUID, nonce); // Store nonce temporarily

  ctx.body = { nonce };
}

async function getUserPastTransactions(ctx) {
  const { signedNonce } = ctx.request.body;
  const userUUID = ctx.params.id;

  const transactionNonce = transactionNonces.get(userUUID);
  const publicRSA  = await DB.getPublicRSAKeyById(userUUID);

  if (!verifyRSASignature(signedNonce, publicRSA, transactionNonce)) {
    ctx.status = 401;
    ctx.body = { error: "Invalid signature" };
    return;
  }
  
  const result = await DB.getUserTransactions(userUUID);

  console.log("result:", result);

  transactionNonces.delete(userUUID); // Remove nonce after use

  if ('errno' in result || result.lastID === 0) {
    ctx.status = 400;
    ctx.body = {error: "Could not get the transactions"};
  }
  else {
    ctx.status = 200;
    ctx.body = result;
  }
}

async function getAnalyticsNonce(ctx) {
  console.log('in getAnalyticsNonce()');
  const { userUUID } = ctx.request.body;

  if (!userUUID) {
      ctx.status = 400;
      ctx.body = { error: "userUUID required" };
      return;
  }

  // Generate a random 32-byte nonce
  const nonce = crypto.randomBytes(32).toString('hex');
  analyticsNonces.set(userUUID, nonce); // Store nonce temporarily

  ctx.body = { nonce };
}

async function getUserAnalytics(ctx) {
  const { signedNonce } = ctx.request.body;
  const userUUID = ctx.params.id;

  const nonce = analyticsNonces.get(userUUID);
  const publicRSA = await DB.getPublicRSAKeyById(userUUID);

  if (!verifyRSASignature(signedNonce, publicRSA, nonce)) {
    ctx.status = 401;
    ctx.body = { error: "Invalid signature" };
    return;
  }

  try {
    const totalSpent = await DB.getTotalSpent(userUUID);
    const purchaseCount = await DB.getPurchaseCount(userUUID);
    const avgSpend = await DB.getAvgSpend(userUUID);
    const topProducts = await DB.getTopProducts(userUUID);
    const totalDiscount = await DB.getTotalDiscount(userUUID);
    const vouchersUsed = await DB.getVouchersUsed(userUUID);

    analyticsNonces.delete(userUUID); // Remove nonce after use

    ctx.body = {
      totalSpent,
      purchaseCount,
      avgSpend,
      topProducts,
      totalDiscount,
      vouchersUsed
    };
  } catch (e) {
    console.error("Analytics Error:", e);
    ctx.status = 500;
    ctx.body = { error: "Internal Server Error" };
  }
}

function verifyRSASignature(signedNonceBase64, publicRSA, originalNonce) {
  try {
    const verifier = crypto.createVerify('SHA256');
    verifier.update(originalNonce);
    verifier.end();

    const signatureBuffer = Buffer.from(signedNonceBase64, 'base64');

    const pemPublicKey = `-----BEGIN PUBLIC KEY-----\n${publicRSA}\n-----END PUBLIC KEY-----`;

    const isValid = verifier.verify(pemPublicKey, signatureBuffer);

    console.log("RSA signature verification result:", isValid);
    return isValid;
  } catch (error) {
    console.error("Error verifying RSA signature:", error);
    return false;
  }
}

function verifyECSignature(nonce, signedNonce, publicEC) {
  try {
    const verifier = crypto.createVerify('SHA256');
    verifier.update(nonce);
    verifier.end();

    // Ensure the public key is in PEM format
    const pemPublicKey = `-----BEGIN PUBLIC KEY-----\n${publicEC}\n-----END PUBLIC KEY-----`;

    // Convert the signedNonce from Base64
    const signatureBuffer = Buffer.from(signedNonce, 'base64');

    // Verify the signature with the public key and the nonce
    const isValid = verifier.verify(pemPublicKey, signatureBuffer);

    console.log("Signature verification result:", isValid);
    return isValid;
  } catch (error) {
    console.error("Error during signature verification:", error);
    return false;
  }
}


module.exports = router;