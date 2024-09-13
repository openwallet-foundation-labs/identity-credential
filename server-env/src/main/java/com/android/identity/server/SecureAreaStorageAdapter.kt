package com.android.identity.server

import com.android.identity.flow.server.Storage
import com.android.identity.storage.StorageEngine
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString

internal class SecureAreaStorageAdapter(private val storage: Storage) : StorageEngine {
    companion object {
        const val TABLE_NAME = "ServerKeys"
    }

    override fun get(key: String): ByteArray? {
        return runBlocking {
            storage.get(TABLE_NAME, "", key)?.toByteArray()
        }
    }

    override fun put(key: String, data: ByteArray) {
        runBlocking {
            val dataBytes = ByteString(data)
            if (storage.get(TABLE_NAME, "", key) == null) {
                storage.insert(TABLE_NAME, "", dataBytes, key)
            } else {
                storage.update(TABLE_NAME, "", key, dataBytes)
            }
        }
    }

    override fun delete(key: String) {
        runBlocking {
            storage.delete(TABLE_NAME, "", key)
        }
    }

    override fun deleteAll() {
        runBlocking {
            for (key in storage.enumerate(TABLE_NAME, "")) {
                storage.delete(TABLE_NAME, "", key)
            }
        }
    }

    override fun enumerate(): Collection<String> {
        return runBlocking {
            storage.enumerate(TABLE_NAME, "")
        }
    }
}
