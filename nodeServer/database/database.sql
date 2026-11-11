-- Drop all tables in reverse order of dependencies to handle foreign key constraints
DROP TABLE IF EXISTS UserPayment;
DROP TABLE IF EXISTS UserVoucher;
DROP TABLE IF EXISTS UserProduct;
DROP TABLE IF EXISTS Payment;
DROP TABLE IF EXISTS Voucher;
DROP TABLE IF EXISTS Admin;
DROP TABLE IF EXISTS Product;
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Transactions;
DROP TABLE IF EXISTS TransactionProduct;

-- User Table
CREATE TABLE Users (
    UUID VARCHAR(255) PRIMARY KEY,
    Name VARCHAR(100) NOT NULL,
    Username VARCHAR(100) NOT NULL UNIQUE,
    CardType VARCHAR(20) NOT NULL,
    CardExpMonth VARCHAR(2) NOT NULL,
    CardExpYear VARCHAR(2) NOT NULL,
    CardNumber VARCHAR(16) NOT NULL, -- Ns se é preciso mais pq ns se é preciso encriptar o número do cartão com algo
    PublicRSA VARCHAR(512) NOT NULL,
    PublicEC VARCHAR(512) NOT NULL,
    VoucherReward INT DEFAULT 0,
    AccumulatedDiscount INT DEFAULT 0
);

-- Product Table
CREATE TABLE Product (
    UUID VARCHAR(255) PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    Price DECIMAL(10, 2) NOT NULL
);

-- Admin Table
CREATE TABLE Admin (
    UUID VARCHAR(255) PRIMARY KEY,
    Username VARCHAR(100) NOT NULL,
    EmailAddress VARCHAR(255) UNIQUE NOT NULL,
    Password VARCHAR(255) NOT NULL
);

-- Voucher Table
CREATE TABLE Voucher (
    UUID VARCHAR(255) PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    Percentage INT NOT NULL DEFAULT 15,
    Used INTEGER NOT NULL DEFAULT 0
);

-- Transaction Table
CREATE TABLE Payment (
    UUID VARCHAR(255) PRIMARY KEY,
    Total DECIMAL(10, 2) NOT NULL,
    VoucherUsed BOOLEAN DEFAULT FALSE,
    AccumulatedDiscount DECIMAL(10, 2) DEFAULT 0.00
);

CREATE TABLE Transactions (
    UUID VARCHAR(255) PRIMARY KEY,
    UserID VARCHAR(255),
    PaymentID VARCHAR(255),
    PurchaseDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (UserID) REFERENCES Users(UUID),
    FOREIGN KEY (PaymentID) REFERENCES Payment(UUID)
);

CREATE TABLE TransactionProduct (
    TransactionID VARCHAR(255),
    ProductID VARCHAR(255),
    Quantity INT DEFAULT 1,
    PriceAtPurchase DECIMAL(10, 2),
    PRIMARY KEY (TransactionID, ProductID),
    FOREIGN KEY (TransactionID) REFERENCES Transactions(UUID),
    FOREIGN KEY (ProductID) REFERENCES Product(UUID)
);

-- Relationships
-- User can buy Products
CREATE TABLE UserProduct (
    UserID VARCHAR(255),
    ProductID VARCHAR(255),
    PRIMARY KEY (UserID, ProductID),
    FOREIGN KEY (UserID) REFERENCES Users(ID),
    FOREIGN KEY (ProductID) REFERENCES Product(ID)
);

-- User can receive vouchers
CREATE TABLE UserVoucher (
    UserID VARCHAR(255),
    VoucherID VARCHAR(255),
    PRIMARY KEY (UserID, VoucherID),
    FOREIGN KEY (UserID) REFERENCES Users(ID),
    FOREIGN KEY (VoucherID) REFERENCES Voucher(ID)
);

-- User can make payments and create transactions
CREATE TABLE UserPayment (
    UserID VARCHAR(255),
    PaymentID VARCHAR(255),
    PRIMARY KEY (UserID, PaymentID),
    FOREIGN KEY (UserID) REFERENCES Users(ID),
    FOREIGN KEY (PaymentID) REFERENCES Payment(ID)
);

-- Insert Users
INSERT INTO Users (Name, Username, CardType, CardExpMonth, CardExpYear, CardNumber, PublicRSA, PublicEC)
VALUES
('Alice Johnson', 'alicej', 'Visa', '05', '26', '4111111111111111', 'MIIBIjANBgkqhkiG9w0BAQEFA...', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...'),
('Bob Smith', 'bobsmith', 'MasterCard', '12', '25', '5555555555554444', 'MIIBIjANBgkqhkiG9w0BAQEFA...', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...'),
('Catherine Lee', 'catherineL', 'AmericanExpress', '09', '27', '378282246310005', 'MIIBIjANBgkqhkiG9w0BAQEFA...', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...'),
('David Kim', 'davidkim', 'Visa', '02', '28', '4012888888881881', 'MIIBIjANBgkqhkiG9w0BAQEFA...', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...'),
('Emma Brown', 'emmapage', 'Discover', '07', '29', '6011111111111117', 'MIIBIjANBgkqhkiG9w0BAQEFA...', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...');

-- Insert Products
INSERT INTO Product (Name, Price)
VALUES
('A', 15.99),
('B', 29.49),
('C', 9.99),
('D', 49.99);

-- Insert Admins
INSERT INTO Admin (UUID, Username, EmailAddress, Password)
VALUES
('24fb86a6-a674-40ad-9427-613cc8fe30e5', 'adminOne', 'admin1@example.com', '$2b$12$cBAuDJXQ/b2fNEauGYwYSO7aso4F65WPamnnmPm1wKBxD3k.oLPIW'), -- adminpass1
('bd357634-520f-401f-a5c5-85999955752c', 'adminTwo', 'admin2@example.com', '$2b$12$Wv5XvDjtdbhOHt4qdF65Cu3bDUOEWdAVpYCOhmARhLZWcFkFfUGje'); -- adminpass2

-- Insert Vouchers
INSERT INTO Voucher (Name, Percentage, Used)
VALUES
("Voucher1", 15, 0),
("Voucher2", 15, 0),
("Voucher3", 15, 0);

-- Insert Transactions
INSERT INTO Payment (Total)
VALUES
(15.99),
(29.49),
(9.99);

-- Insert Product
INSERT INTO Product (UUID, Name, Price)
VALUES
('1', 'Product A', 10.00),
('2', 'Product B', 20.00),
('3', 'Product C', 30.00),
('4', 'Product D', 40.00);

-- Insert User Product
INSERT INTO UserProduct (UserID, ProductID)
VALUES
('bd357634-520f-401f-a5c5-85999955752c', '1'),
('bd357634-520f-401f-a5c5-85999955752c', '2'),
('bd357634-520f-401f-a5c5-85999955752c', '3'),
('bd357634-520f-401f-a5c5-85999955752c', '4');
