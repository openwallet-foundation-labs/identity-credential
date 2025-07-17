package org.multipaz.mdoc.zkp.longfellow

internal actual object NativeLoader {
    actual fun loadLibrary(libraryName: String) {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch")

        val platformDir = when {
            os.contains("mac") && arch == "x86_64" -> "macos-x86_64"
            os.contains("mac") && arch == "aarch64" -> "macos-arm64"
            os.contains("linux") && arch == "amd64" -> "linux-x86_64"
            else -> error("Unsupported platform: $os $arch")
        }

        val libPath = "/nativeLibs/$platformDir/libzkp.${if (os.contains("mac")) "dylib" else "so"}"

        val url = {}::class.java.getResource(libPath)
            ?: error("Could not find native library: $libPath")

        val tempFile = kotlin.io.path.createTempFile().toFile().apply {
            deleteOnExit()
            writeBytes(url.readBytes())
        }

        kotlin.runCatching {
            System.load(tempFile.absolutePath)
        }.onFailure { error ->
            throw RuntimeException("Failed to load native library '$libraryName'", error)
        }
    }
}
