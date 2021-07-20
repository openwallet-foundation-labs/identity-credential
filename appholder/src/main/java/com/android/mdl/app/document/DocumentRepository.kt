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

/**
 * Repository module for handling data operations.
 */
class DocumentRepository private constructor(private val documentDao: DocumentDao) {

    suspend fun getAll() = documentDao.getAll()

    suspend fun insert(document: Document) = documentDao.insert(document)

    suspend fun delete(document: Document) = documentDao.delete(document)

    companion object {

        // For Singleton instantiation
        @Volatile
        private var instance: DocumentRepository? = null

        fun getInstance(documentDao: DocumentDao) =
            instance ?: synchronized(this) {
                instance ?: DocumentRepository(documentDao).also { instance = it }
            }
    }
}
