package org.multipaz.mdoc.zkp.longfellow

import kotlinx.io.bytestring.ByteString
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile

open class LongfellowZkSystem : LongfellowZkSystemBase() {

    /**
     * Locates all circuit file names for the current ZK system. This method supports two deployment modes:
     *
     * @return a list of circuit file names relative to `circuits/{system}/`
     * @throws IOException if the circuits directory is missing or unsupported protocol is encountered
     */
    protected override fun getAllCircuitFileNames(): List<String> {
        val circuitsDirectory = "circuits/$name"

        val dirURL = this::class.java.classLoader.getResource(circuitsDirectory)
            ?: throw IOException("Directory $circuitsDirectory not found in the classpath.")

        return when (dirURL.protocol) {
            "file" -> {
                // Local file system access
                Files.walk(Paths.get(dirURL.toURI()), 2)
                    .filter { Files.isRegularFile(it) }
                    .map { path ->
                        val circuitsPath = Paths.get(dirURL.toURI())
                        val relativePath = circuitsPath.relativize(path)
                        relativePath.toString().replace(File.separatorChar, '/')
                    }
                    .toList()
            }
            "jar" -> {
                // Inside a JAR file
                val jarPath = dirURL.path.substringBefore("!").removePrefix("file:")
                JarFile(jarPath).use { jar ->
                    jar.entries().asSequence() // Use asSequence for efficiency with iterators
                        .filter { it.name.startsWith(circuitsDirectory) && !it.isDirectory }
                        .map { it.name.removePrefix(circuitsDirectory) }
                        .filter { it.contains("/") } // Must be inside a system directory
                        .toList()
                }
            }
            else -> throw IOException("Unsupported protocol: ${dirURL.protocol}")
        }
    }

    /**
     * Loads a circuit file by name from the classpath, under the `circuits/` directory.
     *
     * @param circuitFileName the file name relative to `circuits/`
     * @return the circuit contents as a [ByteString]
     * @throws IOException if the circuit resource is not found or cannot be read
     */
    protected override fun loadCircuit(circuitFileName: String): ByteString {
        val circuitLocation = "circuits/$name/$circuitFileName"
        val inputStream = this::class.java.classLoader.getResourceAsStream(circuitLocation)
            ?: throw IOException("Could not locate resource at $circuitLocation")

        val circuitBytes = inputStream.use { it.readBytes() }
        return ByteString(circuitBytes)
    }
}