package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration131to132_ZakatRedesign : Migration(131, 132) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add tracking state columns
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN trackingState TEXT NOT NULL DEFAULT 'CONFIGURED'"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN priceSource TEXT NOT NULL DEFAULT 'MANUAL'"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN manualGoldPricePerGram REAL"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN manualSilverPricePerGram REAL"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN physicalGoldGrams REAL NOT NULL DEFAULT 0.0"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN physicalSilverGrams REAL NOT NULL DEFAULT 0.0"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN defaultDeductionAccountId TEXT"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN hijriOffset INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN nisabReachedDate INTEGER"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN lastCheckDate INTEGER"
        )
        db.execSQL(
            "ALTER TABLE zakat_configs ADD COLUMN lastCheckWealth REAL"
        )

        // Migrate existing data: copy manual prices and physical holdings
        db.execSQL(
            "UPDATE zakat_configs SET manualGoldPricePerGram = goldPricePerGram " +
                "WHERE goldPricePerGram > 0"
        )
        db.execSQL(
            "UPDATE zakat_configs SET manualSilverPricePerGram = silverPricePerGram " +
                "WHERE silverPricePerGram > 0"
        )
        db.execSQL(
            "UPDATE zakat_configs SET physicalGoldGrams = goldValueGrams"
        )
        db.execSQL(
            "UPDATE zakat_configs SET physicalSilverGrams = silverValueGrams"
        )
    }
}
