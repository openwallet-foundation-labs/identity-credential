package com.android.identity.wallet.document

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.*
import com.android.identity.android.legacy.*
import com.android.identity.credential.Credential
import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.wallet.selfsigned.SelfSignedDocumentData
import com.android.identity.wallet.util.Field
import com.android.identity.wallet.util.FormatUtil
import com.android.identity.wallet.util.ProvisioningUtil
import com.android.identity.wallet.util.ProvisioningUtil.Companion.toDocumentInformation
import java.io.ByteArrayOutputStream
import java.util.*

class DocumentManager private constructor(private val context: Context) {

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DocumentManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DocumentManager(context).also { instance = it }
            }
    }

    fun getDocumentInformation(documentName: String): DocumentInformation? {
        val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
        val credential = credentialStore.lookupCredential(documentName)
        return credential.toDocumentInformation()
    }

    fun getCredentialByName(documentName: String): Credential? {
        val documentInfo = getDocumentInformation(documentName)
        documentInfo?.let {
            val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
            return credentialStore.lookupCredential(documentName)
        }
        return null
    }

    fun getDocuments(): List<DocumentInformation> {
        val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
        return credentialStore.listCredentials().mapNotNull { documentName ->
            val credential = credentialStore.lookupCredential(documentName)
            credential.toDocumentInformation()
        }
    }

    fun deleteCredentialByName(documentName: String) {
        val document = getDocumentInformation(documentName)
        document?.let {
            val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
            credentialStore.deleteCredential(documentName)
        }
    }

    fun createSelfSignedDocument(documentData: SelfSignedDocumentData) {
        val docName = getUniqueDocumentName(documentData)
        documentData.provisionInfo.docName = docName
        try {
            provisionSelfSignedDocument(documentData)
        } catch (e: IdentityCredentialException) {
            throw IllegalStateException("Error creating self signed credential", e)
        }
    }

    private fun getUniqueDocumentName(
        documentData: SelfSignedDocumentData,
        docName: String = documentData.provisionInfo.docName,
        count: Int = 1
    ): String {
        val store = ProvisioningUtil.getInstance(context).credentialStore
        store.listCredentials().forEach { name ->
            if (name == docName) {
                return getUniqueDocumentName(documentData, "$docName ($count)", count + 1)
            }
        }
        return docName
    }

    private fun provisionSelfSignedDocument(documentData: SelfSignedDocumentData) {
        val builder = NameSpacedData.Builder()

        for (field in documentData.fields.filter { it.parentId == null }) {
            when (field.fieldType) {
                is CredentialAttributeType.DATE, CredentialAttributeType.DATE_TIME -> {
                    val date = UnicodeString(field.getValueString())
                    date.setTag(1004)
                    builder.putEntry(
                        field.namespace!!,
                        field.name,
                        FormatUtil.cborEncode(date)
                    )
                }

                is CredentialAttributeType.BOOLEAN -> {
                    builder.putEntryBoolean(
                        field.namespace!!,
                        field.name,
                        field.getValueBoolean()
                    )
                }

                is CredentialAttributeType.PICTURE -> {
                    val bitmap = field.getValueBitmap()
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                    val bytes = baos.toByteArray()
                    builder.putEntryByteString(field.namespace!!, field.name, bytes)
                }

                is CredentialAttributeType.IntegerOptions -> {
                    if (field.hasValue()) {
                        builder.putEntryNumber(
                            field.namespace!!,
                            field.name,
                            field.getValueLong()
                        )
                    }
                }

                is CredentialAttributeType.COMPLEX_TYPE -> {

                    val dataItem = when (field.isArray) {
                        true -> {
                            createArrayDataItem(field, documentData)
                        }

                        false -> {
                            createMapDataItem(field, documentData)
                        }
                    }
                    builder.putEntry(
                        field.namespace!!,
                        field.name,
                        FormatUtil.cborEncode(dataItem)
                    )
                }

                else -> {

                    builder.putEntryString(
                        field.namespace!!,
                        field.name,
                        field.getValueString()
                    )
                }
            }
        }
        ProvisioningUtil.getInstance(context)
            .provisionSelfSigned(builder.build(), documentData.provisionInfo)
    }

    private fun createArrayDataItem(field: Field, documentData: SelfSignedDocumentData): DataItem {
        val childFields = documentData.fields.filter { it.parentId == field.id }
        val childDataItems = mutableListOf<DataItem>()

        val fieldsPerItem = childFields.distinctBy { it.name }.count()
        val itemCount = childFields.count() / fieldsPerItem

        for (i in 0 until itemCount) {
            childDataItems.add(
                createMapDataItem(
                    childFields.subList(
                        i * fieldsPerItem,
                        (i + 1) * fieldsPerItem
                    ), documentData
                )
            )
        }

        val arrayBuilder = CborBuilder().addArray()
        for (childDataItem in childDataItems) {
            arrayBuilder.add(childDataItem)
        }
        return arrayBuilder.end().build()[0]
    }


    private fun createMapDataItem(field: Field, documentData: SelfSignedDocumentData): DataItem {
        val childFields = documentData.fields.filter { it.parentId == field.id }
        return createMapDataItem(childFields, documentData)
    }

    private fun createMapDataItem(
        fields: List<Field>,
        documentData: SelfSignedDocumentData
    ): DataItem {
        val mapBuilder = CborBuilder().addMap()
        for (field in fields) {
            when (field.fieldType) {
                is CredentialAttributeType.DATE, CredentialAttributeType.DATE_TIME -> {
                    val date = UnicodeString(field.getValueString())
                    date.setTag(1004)
                    mapBuilder.put(
                        UnicodeString(field.name),
                        date
                    )
                }

                is CredentialAttributeType.BOOLEAN -> {
                    mapBuilder.put(
                        field.name,
                        field.getValueBoolean()
                    )
                }

                is CredentialAttributeType.PICTURE -> {
                    val bitmap = field.getValueBitmap()
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                    val bytes = baos.toByteArray()
                    mapBuilder.put(field.name, bytes)
                }

                is CredentialAttributeType.IntegerOptions -> {
                    if (field.value != "") {
                        mapBuilder.put(
                            field.name,
                            field.getValueLong()
                        )
                    }
                }

                is CredentialAttributeType.COMPLEX_TYPE -> {
                    val dataItem = when (field.isArray) {
                        true -> {
                            createArrayDataItem(field, documentData)
                        }

                        false -> {
                            createMapDataItem(field, documentData)
                        }
                    }
                    mapBuilder.put(UnicodeString(field.name), dataItem)
                }

                else -> {
                    mapBuilder.put(field.name, field.value as String)
                }
            }
        }

        return mapBuilder.end().build()[0]
    }

    fun refreshAuthKeys(documentName: String) {
        val documentInformation = requireNotNull(getDocumentInformation(documentName))
        val credential = requireNotNull(getCredentialByName(documentName))
        ProvisioningUtil.getInstance(context).refreshAuthKeys(credential, documentInformation.docType)
    }
}
