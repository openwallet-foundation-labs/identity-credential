package org.multipaz.applinks

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.yield
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.util.Logger
import java.util.Locale

/**
 * Utility to verify that app links work correctly in the hosting app environment.
 */
object AppLinksCheck {
    const val TAG = "AppLinksCheck"

    /**
     * Checks that the server setup for the Android app links is valid for the app instance
     * that uses multipaz library.
     *
     * Important: this function does not determine if app links should be trusted or not (that is
     * done independently in Android OS). Instead, it attempts to evaluate if Android is going to
     * trust the way the server set up and if the answer is no, helps to diagnose and fix the
     * problem.
     *
     * Server setup might no work for various reasons:
     *  - app was configured to use a server that simply lacks any setup
     *  - `assetlinks.json` is not formatted correctly
     *  - app was signed by a key that is not known to the server
     *
     *  This function attempts to validate the setup. If it is valid it returns `true`. If it is
     *  invalid, it prints to a log a sample `assetlinks.json` that would be acceptable and returns
     *  `false`.
     *
     *  If the server is unreachable, setup is not validated and assumed valid, `true` is returned.
     *  This is to avoid flagging errors when offline. Also no validation happens below Android "P"
     *  release.
     *
     *  Note that correct intent filter for the app links must also be specified in the
     *  application's `AndroidManifest.xml`. It is not possible to verify this part at runtime.
     *
     *  @param context application or Activity context
     *  @param appLinkServer server URL, e.g, "https://example.com" __without__ trailing slash.
     *  @param httpClient HTTP client to use for fetching `assetlinks.json`
     */
    suspend fun checkAppLinksServerSetup(
        context: Context,
        appLinkServer: String,
        httpClient: HttpClient
    ): Boolean {
        if (appLinkServer.endsWith("/")) {
            throw IllegalArgumentException("Trailing slash in server name: '$appLinkServer'")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // No validation on old Android for now
            return true
        }
        // Extract our app info
        val packageName = context.applicationInfo.packageName
        val signingInfo = context.packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        ).signingInfo!!
        val requireAllSigners = signingInfo.hasMultipleSigners()
        val signatures = if (requireAllSigners) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        val digests = signatures.map {
            ByteString(Crypto.digest(Algorithm.SHA256, it.toByteArray()))
        }.toSet()

        // Fetch assetlinks.json file
        val assetLinksUrl = "$appLinkServer/.well-known/assetlinks.json"
        val jsonText = try {
            val response = httpClient.get(assetLinksUrl)
            if (response.status != HttpStatusCode.OK) {
                Logger.e(TAG, "Error fetching '$assetLinksUrl': HTTP status ${response.status.value}")
                "[]"
            } else {
                response.readBytes().decodeToString()
            }
        } catch (err: Exception) {
            Logger.e(TAG, "Error connecting to $appLinkServer", err)
            // Assume we are offline, don't complain.
            return true
        }
        // Parse and validate assetlinks.json file
        for (permission in Json.parseToJsonElement(jsonText).jsonArray) {
            try {
                val relation = permission.jsonObject["relation"]!!.jsonArray
                if (relation[0].jsonPrimitive.content != "delegate_permission/common.handle_all_urls") {
                    continue
                }
                val target = permission.jsonObject["target"]!!.jsonObject
                if (target["namespace"]?.jsonPrimitive?.content != "android_app") {
                    continue
                }
                if (target["package_name"]?.jsonPrimitive?.content != packageName) {
                    continue
                }
                val serverDigests = target["sha256_cert_fingerprints"]!!.jsonArray.map {
                    ByteString(it.jsonPrimitive.content.split(':').map { byteCode ->
                        byteCode.toInt(16).toByte()
                    }.toByteArray())
                }.toSet()
                var digestsValid = false
                if (requireAllSigners) {
                    digestsValid = serverDigests.containsAll(digests)
                } else {
                    for (digest in digests) {
                        if (serverDigests.contains(digest)) {
                            digestsValid = true
                            break
                        }
                    }
                }
                if (digestsValid) {
                    Logger.i(TAG, "Server app link setup is validated")
                    return true
                } else {
                    Logger.e(TAG, "Server app link setup appears to be invalid")
                    break
                }
            } catch (err: Exception) {
                Logger.e(TAG, "Parsing error", err)
                continue
            }
        }
        Logger.e(TAG, "Server app link setup could not be verified")
        printSampleAssetLinksFile(assetLinksUrl, packageName,
            if (requireAllSigners) digests else setOf(digests.first()))
        return false
    }

    private fun printSampleAssetLinksFile(
        url: String,
        packageName: String,
        digests: Set<ByteString>
    ) {
        val jsonData = buildJsonArray {
            add(buildJsonObject {
                put("relation", buildJsonArray {
                    add("delegate_permission/common.handle_all_urls")
                })
                put("target", buildJsonObject {
                    put("namespace", "android_app")
                    put("package_name", packageName)
                    put("sha256_cert_fingerprints", buildJsonArray {
                        for (digest in digests) {
                            add(digest.toByteArray().joinToString(":") { byte ->
                                (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                            }.uppercase(Locale.ROOT))
                        }
                    })
                })
            })
        }
        val json = Json { prettyPrint = true }
        val jsonText = json.encodeToString(JsonArray.serializer(), jsonData)
        Logger.e(TAG, "Sample file to upload to (or merge into) '$url':\n$jsonText")
    }
}