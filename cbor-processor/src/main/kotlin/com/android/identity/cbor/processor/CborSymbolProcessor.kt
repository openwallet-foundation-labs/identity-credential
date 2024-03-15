package com.android.identity.cbor.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/**
 * Kotlin Annotation Processor that generates serialization and deserialization code for
 * `@CborSerializable` annotation.
 */
class CborSymbolProcessor(
    private val options: Map<String, String>,
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    companion object {
        const val annotationPackage = "com.android.identity.cbor.annotation"
        const val annotationSerializable = "CborSerializable"
        const val annotationMerge = "CborMerge"
        const val bstrType = "com.android.identity.cbor.Bstr"
        const val tstrType = "com.android.identity.cbor.Tstr"
        const val cborMapType = "com.android.identity.cbor.CborMap"
        const val cborArrayType = "com.android.identity.cbor.CborArray"
        const val dataItemClass = "com.android.identity.cbor.DataItem"
    }

    /**
     * Processor main entry point.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation("$annotationPackage.$annotationSerializable")
            .filterIsInstance<KSClassDeclaration>().forEach Declaration@{ clazz ->
                logger.warn("Processing file", clazz)
                for (supertype in clazz.superTypes) {
                    val superDeclaration = supertype.resolve().declaration
                    if (superDeclaration is KSClassDeclaration &&
                        superDeclaration.classKind == ClassKind.CLASS &&
                        superDeclaration.modifiers.contains(Modifier.SEALED)
                    ) {
                        val superAnnotation =
                            findAnnotation(superDeclaration, annotationSerializable)
                        if (superAnnotation == null) {
                            logger.error(
                                "Superclass is not marked with @$annotationSerializable",
                                clazz
                            )
                        } else {
                            // Annotated subclass
                            processSubclass(clazz, superDeclaration)
                            return@Declaration
                        }
                    }
                }
                if (clazz.modifiers.contains(Modifier.SEALED)) {
                    val subclasses = clazz.getSealedSubclasses()
                    processSuperclass(clazz, subclasses)
                    for (subclass in subclasses) {
                        // if annotated, it will be processed in another branch
                        if (findAnnotation(subclass, annotationSerializable) == null) {
                            processSubclass(subclass, clazz)
                        }
                    }
                } else {
                    processClass(clazz)
                }
            }
        return listOf()
    }

    // Handle a standalone data class.
    private fun processClass(classDeclaration: KSClassDeclaration) {
        processDataClass(classDeclaration, null, null)
    }

    // Handle a leaf (data) class that has sealed superclass.
    private fun processSubclass(
        classDeclaration: KSClassDeclaration,
        superDeclaration: KSClassDeclaration
    ) {
        val typeKey: String = getTypeKey(findAnnotation(superDeclaration, annotationSerializable))
        val typeId: String = getTypeId(superDeclaration, classDeclaration)
        processDataClass(classDeclaration, typeKey, typeId)
    }

    // Handle a sealed class that has subclasses.
    private fun processSuperclass(
        classDeclaration: KSClassDeclaration,
        subclasses: Sequence<KSClassDeclaration>
    ) {
        val containingFile = classDeclaration.containingFile
        val packageName = classDeclaration.packageName.asString()
        val baseName = classDeclaration.simpleName.asString()
        val annotation = findAnnotation(classDeclaration, annotationSerializable)

        with(CodeBuilder()) {
            importClass(dataItemClass)

            block("fun $baseName.toCborDataItem(): DataItem") {
                block("return when (this)") {
                    for (subclass in subclasses) {
                        importClass(subclass)
                        val subclassName = subclass.simpleName.asString()
                        line("is $subclassName -> (this as $subclassName).toCborDataItem()")
                    }
                }
            }

            emptyLine()
            block("fun ${deserializerName(classDeclaration)}(dataItem: DataItem): $baseName") {
                val typeKey = getTypeKey(annotation)
                line("val type = dataItem[\"$typeKey\"].asTstr")
                block("return when (type)") {
                    for (subclass in subclasses) {
                        val typeId = getTypeId(classDeclaration, subclass)
                        line("\"$typeId\" -> ${deserializerName(subclass)}(dataItem)")
                    }
                    line("else -> throw IllegalArgumentException(\"wrong type: \$type\")")
                }
            }

            writeToFile(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(false, containingFile!!),
                packageName = packageName,
                fileName = "${baseName}_Cbor"
            )
        }
    }

    private fun processDataClass(
        classDeclaration: KSClassDeclaration,
        typeKey: String?, typeId: String?
    ) {
        val containingFile = classDeclaration.containingFile
        val packageName = classDeclaration.packageName.asString()
        val baseName = classDeclaration.simpleName.asString()
        with(CodeBuilder()) {
            importClass(dataItemClass)

            var hadMergedMap = false

            block("fun $baseName.toCborDataItem(): DataItem") {
                importClass(cborMapType)
                line("val builder = CborMap.builder()")
                if (typeKey != null) {
                    line("builder.put(\"$typeKey\", \"$typeId\")")
                }
                classDeclaration.getAllProperties().forEach { property ->
                    val name = property.simpleName.asString()
                    val type = property.type.resolve()
                    optionalBlock(if (type.isMarkedNullable) "if ($name != null)" else null) {
                        if (findAnnotation(property, annotationMerge) != null) {
                            if (hadMergedMap) {
                                logger.error("@$annotationMerge can be used only on a single field")
                            } else if (type.declaration.qualifiedName!!.asString() != "kotlin.collections.Map") {
                                logger.error("@$annotationMerge requires field of type Map")
                            } else {
                                hadMergedMap = true
                                addSerializedMapValues(
                                    this, "builder",
                                    "this.$name", type
                                )
                            }
                        } else {
                            val code = serializeValue(this, "this.$name", type)
                            line("builder.put(\"$name\", $code)")
                        }
                    }
                }
                line("return builder.end().build()")
            }

            val fieldNameSet = if (hadMergedMap) varName("fieldNameSet") else null
            val dataItem = varName("dataItem")

            emptyLine()
            block("fun ${deserializerName(classDeclaration)}($dataItem: DataItem): $baseName") {
                classDeclaration.getAllProperties().forEach { property ->
                    val fieldName = property.simpleName.asString()
                    val name = varName(fieldName)
                    val type = property.type.resolve()
                    if (findAnnotation(property, annotationMerge) != null) {
                        if (type.declaration.qualifiedName!!.asString() == "kotlin.collections.Map") {
                            line("val $name = mutableMapOf<${typeArguments(this, type)}>()")
                            addDeserializedMapValues(this, name, dataItem, type, fieldNameSet)
                        }
                    } else {
                        val item = "$dataItem[\"$fieldName\"]"
                        if (type.isMarkedNullable) {
                            block(
                                "val $name = if ($dataItem.hasKey(\"$fieldName\"))",
                                hasBlockAfter = true
                            ) {
                                line(deserializeValue(this, item, type))
                            }
                            block("else", hasBlockBefore = true) {
                                line("null")
                            }
                        } else {
                            line("val $name = ${deserializeValue(this, item, type)}")
                        }
                    }
                }
                line {
                    append("return $baseName(")
                    var first = true
                    classDeclaration.getAllProperties().forEach { property ->
                        if (first) {
                            first = false
                        } else {
                            append(", ")
                        }
                        append(property.simpleName.asString())
                    }
                    append(")")
                }
            }

            if (hadMergedMap) {
                emptyLine()
                line("private val $fieldNameSet = setOf(")
                withIndent {
                    classDeclaration.getAllProperties().forEach { property ->
                        val name = property.simpleName.asString()
                        line("\"$name\",")
                    }
                    if (typeKey != null) {
                        line("\"$typeKey\"")
                    }
                }
                line(")")
            }

            writeToFile(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(false, containingFile!!),
                packageName = packageName,
                fileName = "${baseName}_Cbor"
            )
        }
    }

    private fun getTypeKey(annotation: KSAnnotation?): String {
        annotation?.arguments?.forEach { arg ->
            when (arg.name?.asString()) {
                "typeKey" -> {
                    val field = arg.value.toString()
                    if (field.isNotEmpty()) {
                        return field
                    }
                }
            }
        }
        return "type"
    }

    private fun getTypeId(superclass: KSClassDeclaration, subclass: KSClassDeclaration): String {
        findAnnotation(subclass, annotationSerializable)?.arguments?.forEach { arg ->
            when (arg.name?.asString()) {
                "typeId" -> {
                    val field = arg.value.toString()
                    if (field.isNotEmpty()) {
                        return field
                    }
                }
            }
        }
        val superName = superclass.simpleName.asString()
        val name = subclass.simpleName.asString()
        return if (!name.startsWith(superName)) {
            logger.warn("Subtype name is not created by appending to supertype, rename or specify typeId explicitly")
            name
        } else {
            name.substring(superName.length)
        }
    }

    private fun findAnnotation(declaration: KSDeclaration, simpleName: String): KSAnnotation? {
        for (annotation in declaration.annotations) {
            if (annotation.shortName.asString() == simpleName &&
                annotation.annotationType.resolve().declaration.packageName.asString() == annotationPackage
            ) {
                return annotation
            }
        }
        return null
    }

    private fun deserializerName(classDeclaration: KSClassDeclaration): String {
        val baseName = classDeclaration.simpleName.asString()
        return if (hasCompanion(classDeclaration)) {
            "${baseName}.Companion.fromCborDataItem"
        } else {
            "${baseName}_fromCborDataItem"
        }
    }

    private fun hasCompanion(declaration: KSClassDeclaration): Boolean {
        return declaration.declarations.filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject } != null
    }

    private fun serializeValue(
        codeBuilder: CodeBuilder,
        code: String,
        type: KSType
    ): String {
        val declaration = type.declaration
        when (declaration.qualifiedName!!.asString()) {
            "kotlin.collections.Map" ->
                with(codeBuilder) {
                    val map = varName("map")
                    val mapBuilder = varName("mapBuilder")
                    importClass(cborMapType)
                    line("val $mapBuilder = CborMap.builder()")
                    addSerializedMapValues(this, mapBuilder, code, type)
                    line("val $map: DataItem = $mapBuilder.end().build()")
                    return map
                }

            "kotlin.collections.List" ->
                with(codeBuilder) {
                    val array = varName("array")
                    val arrayBuilder = varName("arrayBuilder")
                    importClass(cborArrayType)
                    line("val $arrayBuilder = CborArray.builder()")
                    block("for (value in $code)") {
                        val value = serializeValue(
                            this, "value",
                            type.arguments[0].type!!.resolve()
                        )
                        line("$arrayBuilder.add($value)")
                    }
                    line("val $array: DataItem = $arrayBuilder.end().build()")
                    return array
                }

            "kotlin.String" -> return code
            "kotlin.ByteArray" -> {
                codeBuilder.importClass(bstrType)
                return "Bstr($code)"
            }

            "kotlin.Long" -> return code
            "kotlin.Int" -> return "${code}.toLong()"
            "kotlin.Boolean" -> return code
            dataItemClass -> return code
            else -> return if (declaration is KSClassDeclaration &&
                declaration.classKind == ClassKind.ENUM_CLASS
            ) {
                "$code.name"
            } else {
                "$code.toCborDataItem()"
            }
        }
    }

    private fun addSerializedMapValues(
        codeBuilder: CodeBuilder,
        targetCode: String, sourceCode: String, sourceType: KSType
    ) {
        codeBuilder.block("for (entry in $sourceCode.entries)") {
            val key = serializeValue(
                this, "entry.key",
                sourceType.arguments[0].type!!.resolve()
            )
            val value = serializeValue(
                this, "entry.value",
                sourceType.arguments[1].type!!.resolve()
            )
            line("$targetCode.put($key, $value)")
        }
    }

    private fun deserializeValue(
        codeBuilder: CodeBuilder,
        code: String,
        type: KSType
    ): String {
        val declaration = type.declaration
        return when (declaration.qualifiedName!!.asString()) {
            "kotlin.collections.Map" ->
                with(codeBuilder) {
                    val map = varName("map")
                    line("val $map = mutableMapOf<${typeArguments(this, type)}>()")
                    addDeserializedMapValues(this, map, code, type)
                    map
                }

            "kotlin.collections.List" ->
                with(codeBuilder) {
                    val array = varName("array")
                    line("val $array = mutableListOf<${typeArguments(this, type)}>()")
                    block("for (value in $code.asArray)") {
                        val value = deserializeValue(
                            this, "value",
                            type.arguments[0].type!!.resolve()
                        )
                        line("$array.add($value)")
                    }
                    array
                }

            "kotlin.String" -> return "$code.asTstr"
            "kotlin.ByteArray" -> return "$code.asBstr"
            "kotlin.Long" -> return "$code.asNumber"
            "kotlin.Int" -> return "$code.asNumber.toInt()"
            "kotlin.Boolean" -> return "$code.asBoolean"
            dataItemClass -> return code
            else -> return if (declaration is KSClassDeclaration &&
                declaration.classKind == ClassKind.ENUM_CLASS
            ) {
                "${typeRef(codeBuilder, type)}.valueOf($code.asTstr)"
            } else {
                "${deserializerName(declaration as KSClassDeclaration)}($code)"
            }
        }
    }

    private fun addDeserializedMapValues(
        codeBuilder: CodeBuilder,
        targetCode: String, sourceCode: String, targetType: KSType,
        fieldNameSet: String? = null
    ) {
        val entry = codeBuilder.varName("entry")
        codeBuilder.block("for ($entry in $sourceCode.asMap.entries)") {
            if (fieldNameSet != null) {
                importClass(tstrType)
                block("if ($entry.key is Tstr && $fieldNameSet.contains($entry.key.asTstr))") {
                    line("continue")
                }
            }
            val key = deserializeValue(
                this, "$entry.key",
                targetType.arguments[0].type!!.resolve()
            )
            val value = deserializeValue(
                this, "$entry.value",
                targetType.arguments[1].type!!.resolve()
            )
            line("$targetCode.put($key, $value)")
        }
    }

    private fun typeArguments(codeBuilder: CodeBuilder, type: KSType): String {
        val str = StringBuilder()
        for (arg in type.arguments) {
            if (str.isNotEmpty()) {
                str.append(", ")
            }
            str.append(typeRef(codeBuilder, arg.type!!.resolve()))
        }
        return str.toString()
    }

    private fun typeRef(codeBuilder: CodeBuilder, type: KSType): String {
        return when (val qualifiedName = type.declaration.qualifiedName!!.asString()) {
            "kotlin.collections.Map" -> "Map<${typeArguments(codeBuilder, type)}>"
            "kotlin.collections.List" -> "List<${typeArguments(codeBuilder, type)}>"
            "kotlin.String" -> "String"
            "kotlin.ByteArray" -> "ByteArray"
            "kotlin.Long" -> "Long"
            "kotlin.Int" -> "Int"
            "kotlin.Boolean" -> "Boolean"
            dataItemClass -> "DataItem"
            else -> {
                codeBuilder.importClass(qualifiedName)
                return type.declaration.simpleName.asString()
            }
        }
    }

}

