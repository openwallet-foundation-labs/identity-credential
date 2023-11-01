package com.android.mdl.appreader.issuerauth

import android.content.Context
import java.io.File
import java.nio.file.Files

/**
 * [Store] Base class for the validation, parsing and persistence of certificates or vicals
 */
abstract class Store<T>(val context: Context) {

    abstract val folderName: String
    abstract val extension: String
    private val directory: File = context.getDir(folderName, Context.MODE_PRIVATE)

    /**
     * Parse, validate and persist an item
     */
    fun save(content: ByteArray) {
        val item = parse(content)
        validate(item)
        val fileName = sanitizeFilename("${determineFileName(item)}$extension")
        val file = File(directory, fileName)
        if (file.exists()) {
            throw FileAlreadyExistsException(file, reason = "File already exist")
        } else {
            file.writeBytes(content)
        }
    }


    /**
     * Check if an item already exists in the store
     */
    fun exists(item: T): Boolean {
        val fileName = sanitizeFilename("${determineFileName(item)}$extension")
        val file = File(directory, fileName)
        return file.exists()
    }

    /**
     * Retrieve and parse all the items in the folder
     */
    fun getAll(): List<T> {
        return readFiles().map { parse(it.readBytes()) }.toList()
    }

    fun delete(item: T) {
        val fileName = sanitizeFilename("${determineFileName(item)}$extension")
        val file = File(directory, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Parse the content to an instance of <T>
     */
    protected abstract fun parse(content: ByteArray): T

    /**
     * Validate the parsed item
     */
    protected abstract fun validate(item: T)

    /**
     * Determine the filename (without extension)
     */
    abstract fun determineFileName(item: T): String

    /**
     * Replace reserved characters in the file name with underscores
     */
    private fun sanitizeFilename(filename: String): String {
        return filename.replace("[^a-zA-Z0-9.=, -]".toRegex(), "_")
    }

    /**
     * List all the files in the folder
     */
    private fun readFiles(): List<File> {
        val result = ArrayList<File>()
        if (directory.exists()) {
            directory.walk()
                .filter { file -> Files.isRegularFile(file.toPath()) }
                .filter { file -> file.toString().endsWith(extension) }
                .forEach { result.add(it) }
        }
        return result
    }
}