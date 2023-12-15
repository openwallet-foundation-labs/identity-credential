package com.android.identity.wallet.viewmodel

import android.app.Application
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.credentialtype.MdocDataElement
import com.android.identity.wallet.HolderApp
import com.android.identity.wallet.document.DocumentManager
import com.android.identity.wallet.documentdata.MdocComplexTypeDefinition
import com.android.identity.wallet.documentdata.MdocComplexTypeRepository
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
        for (credentialType in HolderApp.credentialTypeRepositoryInstance.getCredentialTypes()
            .filter { it.mdocCredentialType != null }) {
            id = 1 // reset the id to 1
            fieldsByDocType[credentialType.mdocCredentialType?.docType!!] =
                createFields(credentialType.mdocCredentialType!!)
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

    private fun createFields(mdocCredentialType: MdocCredentialType): MutableList<Field> {
        val fields: MutableList<Field> = mutableListOf()
        val complexTypes = MdocComplexTypeRepository.getComplexTypes()
            .find { it.docType == mdocCredentialType.docType }
        for (namespace in mdocCredentialType.namespaces) {
            val namespaceComplexTypes =
                complexTypes?.namespaces?.find { it.namespace == namespace.namespace }
            for (dataElement in namespace.dataElements) {
                when (dataElement.attribute.type) {
                    is CredentialAttributeType.COMPLEX_TYPE -> {
                        val complexTypeDefinitions = namespaceComplexTypes?.dataElements?.filter {
                            it.parentIdentifiers.contains(dataElement.attribute.identifier)
                        }

                        if (complexTypeDefinitions?.first()?.partOfArray == true) {
                            val arrayLength =
                                SampleDataProvider.getArrayLength(
                                    namespace.namespace,
                                    dataElement.attribute.identifier
                                )
                            val parentField = Field(
                                id++,
                                "${dataElement.attribute.displayName} ($arrayLength items)",
                                dataElement.attribute.identifier,
                                dataElement.attribute.type,
                                null,
                                namespace = namespace.namespace,
                                isArray = true,
                            )
                            fields.add(parentField)
                            addArrayFields(
                                parentField,
                                fields,
                                namespaceComplexTypes.dataElements)
                        } else {
                            val parentField = Field(
                                id++,
                                dataElement.attribute.displayName,
                                dataElement.attribute.identifier,
                                dataElement.attribute.type,
                                null,
                                namespace = namespace.namespace
                            )
                            fields.add(parentField)
                            addMapFields(
                                parentField,
                                fields,
                                namespaceComplexTypes?.dataElements!!)
                        }
                    }

                    else -> {

                        val sampleValue = SampleDataProvider.getSampleValue(
                            app,
                            namespace.namespace,
                            dataElement.attribute.identifier,
                            dataElement.attribute.type
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
        dataElements: List<MdocComplexTypeDefinition>,
        prefix: String = ""
    ) {
        val arrayLength =
            SampleDataProvider.getArrayLength(parentField.namespace!!, parentField.name)
        val childElements = dataElements.filter { it.parentIdentifiers.contains(parentField.name) }
        for (i in 0..arrayLength - 1) {
            for (childElement in childElements) {
                if (childElement.type is CredentialAttributeType.COMPLEX_TYPE) {

                    if (dataElements.any { it.parentIdentifiers.contains(childElement.identifier) && it.partOfArray }) {
                        val childField = Field(
                            id++,
                            "$prefix${i + 1} | ${childElement.displayName} (${
                                SampleDataProvider.getArrayLength(
                                    parentField.namespace,
                                    childElement.identifier
                                )
                            } items)",
                            childElement.identifier,
                            childElement.type,
                            null,
                            namespace = parentField.namespace,
                            isArray = true,
                            parentId = parentField.id
                        )
                        fields.add(childField)
                        addArrayFields(
                            childField,
                            fields,
                            dataElements,
                            "$prefix${i + 1} | "
                        )
                    } else {
                        val childField = Field(
                            id++,
                            "$prefix${i + 1} | ${childElement.displayName}",
                            childElement.identifier,
                            childElement.type,
                            null,
                            namespace = parentField.namespace,
                            parentId = parentField.id
                        )
                        fields.add(childField)
                        addMapFields(
                            childField,
                            fields,
                            dataElements,
                            "$prefix${i + 1} | "
                        )
                    }
                } else {
                    val sampleValue =
                        SampleDataProvider.getSampleValue(parentField.namespace, childElement.identifier, childElement.type, i)
                    val childField = Field(
                        id++,
                        "$prefix${i + 1} | ${childElement.displayName}",
                        childElement.identifier,
                        childElement.type,
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
        dataElements: List<MdocComplexTypeDefinition>,
        prefix: String = ""
    ) {

        val childElements = dataElements.filter { it.parentIdentifiers.contains(parentField.name) }
        for (childElement in childElements) {
            if (childElement.type is CredentialAttributeType.COMPLEX_TYPE) {
                val isArray = dataElements.any { it.parentIdentifiers.contains(childElement.identifier) && it.partOfArray }
                val childField = Field(
                    id++,
                    "$prefix${childElement.displayName}",
                    childElement.identifier,
                    childElement.type,
                    null,
                    namespace = parentField.namespace,
                    isArray = isArray,
                    parentId = parentField.id
                )
                fields.add(childField)
                if (isArray){
                    addArrayFields(childField, fields, dataElements, prefix)
                } else {
                    addMapFields(childField, fields, dataElements, prefix)
                }
            } else {
                val sampleValue =
                    SampleDataProvider.getSampleValue(
                        app,
                        parentField.namespace!!,
                        childElement.identifier,
                        childElement.type
                    )
                val childField = Field(
                    id++,
                    "$prefix${childElement.displayName}",
                    childElement.identifier,
                    childElement.type,
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

    fun addOptions(field: Field, dataElement: MdocComplexTypeDefinition) {
        when (dataElement.type) {
            is CredentialAttributeType.StringOptions -> field.stringOptions =
                dataElement.type.options

            is CredentialAttributeType.IntegerOptions -> field.integerOptions =
                dataElement.type.options

            else -> {}
        }
    }

}

