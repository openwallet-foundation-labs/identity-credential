package com.android.identity.wallet.viewmodel

import android.app.Application
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.credentialtype.MdocDataElement
import com.android.identity.wallet.document.DocumentManager
import com.android.identity.wallet.selfsigned.SelfSignedDocumentData
import com.android.identity.wallet.util.Field
import com.android.identity.wallet.util.SampleDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelfSignedViewModel(val app: Application) :
    AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "SelfSignedViewModel"
    }

    private val documentManager = DocumentManager.getInstance(app.applicationContext)
    val loading = MutableLiveData<Int>()
    val created = MutableLiveData<Boolean>()
    private val fieldsByDocType: MutableMap<String, MutableList<Field>> = mutableMapOf()
    private var id = 1

    init {
        loading.value = View.GONE
        for (credentialType in CredentialTypeRepository.getCredentialTypes()
            .filter { it.mdocCredentialType != null }) {
            id = 1 // reset the id to 1
            fieldsByDocType[credentialType.mdocCredentialType?.docType!!] = createFields(credentialType.mdocCredentialType!!)
        }
    }

    fun getFields(docType: String): MutableList<Field> {
        return fieldsByDocType[docType]
            ?: throw IllegalArgumentException("No field list valid for $docType")
    }

    fun createSelfSigned(documentData: SelfSignedDocumentData) {
        loading.value = View.VISIBLE
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                documentManager.createSelfSignedDocument(documentData)
            }
            created.value = true
            loading.value = View.GONE
        }
    }

    private fun createFields(mdocCredentialType: MdocCredentialType): MutableList<Field>
    {
        val fields: MutableList<Field> = mutableListOf()
        for (namespace in mdocCredentialType.namespaces) {
            for (dataElement in namespace.dataElements.filter {
                !it.attribute.identifier.contains(
                    "."
                )
            }) {
                when (dataElement.attribute.type) {
                    is CredentialAttributeType.ComplexType -> {

                        if ((dataElement.attribute.type as CredentialAttributeType.ComplexType).isArray) {
                            val arrayLength =
                                SampleDataProvider.getArrayLength(
                                    namespace.namespace,
                                    dataElement
                                )
                            val parent = Field(
                                id++,
                                "${dataElement.attribute.displayName} ($arrayLength items)",
                                dataElement.attribute.identifier,
                                dataElement.attribute.type,
                                null,
                                namespace = namespace.namespace
                            )
                            fields.add(parent)
                            addArrayFields(
                                parent,
                                fields,
                                dataElement,
                                namespace.dataElements.filter {
                                    it.attribute.identifier.contains(".")
                                })
                        } else {
                            val parent = Field(
                                id++,
                                dataElement.attribute.displayName,
                                dataElement.attribute.identifier,
                                dataElement.attribute.type,
                                null,
                                namespace = namespace.namespace
                            )
                            fields.add(parent)
                            addMapFields(
                                parent,
                                fields,
                                dataElement,
                                namespace.dataElements.filter {
                                    it.attribute.identifier.contains(".")
                                })
                        }
                    }

                    else -> {

                        val sampleValue = SampleDataProvider.getSampleValue(
                            app,
                            namespace.namespace,
                            dataElement
                        )
                        val field = Field(
                            id++,
                            dataElement.attribute.displayName,
                            dataElement.attribute.identifier,
                            dataElement.attribute.type,
                            sampleValue,
                            namespace = namespace.namespace
                        )
                        addOptions(field, dataElement)
                        fields.add(field)
                    }
                }
            }
        }
        return fields
    }


    private fun addArrayFields(
        parentField: Field,
        fields: MutableList<Field>,
        parentElement: MdocDataElement,
        dataElements: List<MdocDataElement>,
        prefix: String = ""
    ) {
        val arrayLength = SampleDataProvider.getArrayLength(parentField.namespace!!, parentElement)
        val typeName =
            (parentElement.attribute.type as CredentialAttributeType.ComplexType).typeName
        val childElements = dataElements.filter { it.attribute.identifier.startsWith("$typeName.") }
        for (i in 0..arrayLength - 1) {
            for (childElement in childElements) {
                if (childElement.attribute.type is CredentialAttributeType.ComplexType) {

                    if ((childElement.attribute.type as CredentialAttributeType.ComplexType).isArray) {
                        val childField = Field(
                            id++,
                            "$prefix${i + 1} | ${childElement.attribute.displayName} (${
                                SampleDataProvider.getArrayLength(parentField.namespace, childElement)
                            } items)",
                            childElement.attribute.identifier.substringAfter("."),
                            childElement.attribute.type,
                            null,
                            namespace = parentField.namespace,
                            parentId = parentField.id
                        )
                        fields.add(childField)
                        addArrayFields(
                            childField,
                            fields,
                            childElement,
                            dataElements,
                            "$prefix${i + 1} | "
                        )
                    } else {
                        val childField = Field(
                            id++,
                            "$prefix${i + 1} | ${childElement.attribute.displayName}",
                            childElement.attribute.identifier.substringAfter("."),
                            childElement.attribute.type,
                            null,
                            namespace = parentField.namespace,
                            parentId = parentField.id
                        )
                        fields.add(childField)
                        addMapFields(
                            childField,
                            fields,
                            childElement,
                            dataElements,
                            "$prefix${i + 1} | "
                        )
                    }
                } else {
                    val sampleValue =
                        SampleDataProvider.getSampleValue(parentField.namespace, childElement, i)
                    val childField = Field(
                        id++,
                        "$prefix${i + 1} | ${childElement.attribute.displayName}",
                        childElement.attribute.identifier.substringAfter("."),
                        childElement.attribute.type,
                        sampleValue,
                        namespace = parentField.namespace,
                        parentId = parentField.id
                    )
                    addOptions(childField, childElement)
                    fields.add(childField)
                }
            }
        }
    }

    private fun addMapFields(
        parentField: Field,
        fields: MutableList<Field>,
        parentElement: MdocDataElement,
        dataElements: List<MdocDataElement>,
        prefix: String = ""
    ) {
        val typeName =
            (parentElement.attribute.type as CredentialAttributeType.ComplexType).typeName
        val childElements = dataElements.filter { it.attribute.identifier.startsWith("$typeName.") }
        for (childElement in childElements) {
            if (childElement.attribute.type is CredentialAttributeType.ComplexType) {
                val childField = Field(
                    id++,
                    "$prefix${childElement.attribute.displayName}",
                    childElement.attribute.identifier.substringAfter("."),
                    childElement.attribute.type,
                    null,
                    namespace = parentField.namespace,
                    parentId = parentField.id
                )
                fields.add(childField)
                if ((childElement.attribute.type as CredentialAttributeType.ComplexType).isArray) {
                    addArrayFields(childField, fields, childElement, dataElements, prefix)
                } else {
                    addMapFields(childField, fields, childElement, dataElements, prefix)
                }
            } else {
                val sampleValue =
                    SampleDataProvider.getSampleValue(
                        app,
                        parentField.namespace!!,
                        childElement,
                        parentElement
                    )
                val childField = Field(
                    id++,
                    "$prefix${childElement.attribute.displayName}",
                    childElement.attribute.identifier.substringAfter("."),
                    childElement.attribute.type,
                    sampleValue,
                    namespace = parentField.namespace,
                    parentId = parentField.id
                )
                addOptions(childField, childElement)
                fields.add(childField)
            }

        }
    }

    fun addOptions(field: Field, dataElement: MdocDataElement) {
        when (dataElement.attribute.type) {
            is CredentialAttributeType.StringOptions -> field.stringOptions =
                (dataElement.attribute.type as CredentialAttributeType.StringOptions).options

            is CredentialAttributeType.IntegerOptions -> field.integerOptions =
                (dataElement.attribute.type as CredentialAttributeType.IntegerOptions).options

            else -> {}
        }
    }

}

