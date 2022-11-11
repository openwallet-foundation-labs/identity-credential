package com.android.mdl.app.document

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MigrationV1ToV2: Migration(1, 2) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE document ADD COLUMN card_art INTEGER NOT NULL DEFAULT 0")
    }
}