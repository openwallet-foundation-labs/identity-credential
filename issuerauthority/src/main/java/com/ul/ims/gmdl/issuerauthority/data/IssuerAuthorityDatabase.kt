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

package com.ul.ims.gmdl.issuerauthority.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Credential::class], version = 1)
abstract class IssuerAuthorityDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao


    companion object {

        private const val DATABASE_NAME = "issuer-authority-db"

        // For Singleton instantiation
        @Volatile
        private var instance: IssuerAuthorityDatabase? = null

        fun getInstance(context: Context): IssuerAuthorityDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): IssuerAuthorityDatabase {
            return Room.databaseBuilder(
                context, IssuerAuthorityDatabase::class.java, DATABASE_NAME
            ).build()
        }
    }
}