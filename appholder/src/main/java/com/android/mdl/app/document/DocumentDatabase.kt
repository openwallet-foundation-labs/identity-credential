/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.android.mdl.app.document

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 2,
    entities = [Document::class]
)
@TypeConverters(Converters::class)
abstract class DocumentDatabase : RoomDatabase() {

    abstract fun credentialDao(): DocumentDao

    companion object {

        private const val DATABASE_NAME = "mdoc-document-database"

        // For Singleton instantiation
        @Volatile
        private var instance: DocumentDatabase? = null

        fun getInstance(context: Context): DocumentDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        // TODO: According to https://developer.android.com/guide/topics/data/autobackup this
        //   database ends up being backed up and restored. We should probably opt out of doing
        //   that since all of these documents will reference HW-backed keys that are not backed
        //   up and restored.
        private fun buildDatabase(context: Context): DocumentDatabase {
            return Room.databaseBuilder(context, DocumentDatabase::class.java, DATABASE_NAME)
                .addMigrations(MigrationV1ToV2)
                .build()
        }
    }
}