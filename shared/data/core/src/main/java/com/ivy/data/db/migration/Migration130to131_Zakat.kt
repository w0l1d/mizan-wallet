package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration130to131_Zakat : Migration(130, 131) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS zakat_configs (" +
                "name TEXT NOT NULL, " +
                "nisabStandard TEXT NOT NULL, " +
                "goldPricePerGram REAL NOT NULL, " +
                "silverPricePerGram REAL NOT NULL, " +
                "hawlStartDate INTEGER NOT NULL, " +
                "hawlEndDate INTEGER NOT NULL, " +
                "totalWealth REAL NOT NULL, " +
                "goldValueGrams REAL NOT NULL, " +
                "silverValueGrams REAL NOT NULL, " +
                "deductions REAL NOT NULL, " +
                "netZakatable REAL NOT NULL, " +
                "zakatDue REAL NOT NULL, " +
                "nisabAmount REAL NOT NULL, " +
                "accountIdsSerialized TEXT, " +
                "currency TEXT NOT NULL, " +
                "orderId REAL NOT NULL, " +
                "isSynced INTEGER NOT NULL, " +
                "isDeleted INTEGER NOT NULL, " +
                "id TEXT NOT NULL, " +
                "PRIMARY KEY(id))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS zakat_payments (" +
                "zakatConfigId TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "dateTime INTEGER NOT NULL, " +
                "transactionId TEXT, " +
                "note TEXT, " +
                "orderId REAL NOT NULL, " +
                "isSynced INTEGER NOT NULL, " +
                "isDeleted INTEGER NOT NULL, " +
                "id TEXT NOT NULL, " +
                "PRIMARY KEY(id))"
        )
    }
}
