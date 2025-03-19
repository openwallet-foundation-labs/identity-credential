package com.android.mdl.app.credman

import android.content.Context
import com.google.android.gms.identitycredentials.RegistrationRequest
import kotlinx.io.bytestring.buildByteString
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.multipaz.util.appendByteArray
import org.multipaz.util.appendUInt32Le

class IdentityCredentialRegistry(
  val entries: List<IdentityCredentialEntry>,
) {
  fun toRegistrationRequest(context: Context): RegistrationRequest {

    return RegistrationRequest(
      credentials = credentialBytes(),
      matcher = loadMatcher(context),
      type = "com.credman.IdentityCredential",
      requestType = "",
      protocolTypes = emptyList(),
    )
  }

  private fun loadMatcher(context: Context): ByteArray {
    val stream = context.assets.open("identitycredentialmatcher.wasm");
    val matcher = ByteArray(stream.available())
    stream.read(matcher)
    stream.close()
    return matcher
  }

  private fun credentialBytes(): ByteArray {
    val json = JSONObject()
    val credListJson = JSONArray()
    val icons = ByteArrayOutputStream()
    val iconSizeList = mutableListOf<Int>()
    entries.forEach { entry ->
      val iconBytes = entry.getIconBytes()
      credListJson.put(entry.toJson(iconSizeList.size))
      iconSizeList.add(iconBytes.size())
      iconBytes.writeTo(icons)
    }
    json.put("credentials", credListJson)
    val credsBytes = json.toString(0).toByteArray()

    return buildByteString {
      // header_size
      appendUInt32Le((3 + iconSizeList.size) * Int.SIZE_BYTES)
      // creds_size
      appendUInt32Le(credsBytes.size)
      // icon_size_array_size
      appendUInt32Le(iconSizeList.size)
      // icon offsets
      iconSizeList.forEach(::appendUInt32Le)

      appendByteArray(credsBytes)
      appendByteArray(icons.toByteArray())
    }.toByteArray()
  }
}