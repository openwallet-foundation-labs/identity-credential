package org.multipaz.processor

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
        const val ANNOTATION_PACKAGE = "org.multipaz.cbor.annotation"
        const val ANNOTATION_SERIALIZABLE = "CborSerializable"
        const val ANNOTATION_MERGE = "CborMerge"
        const val CBOR_TYPE = "org.multipaz.cbor.Cbor"
        const val BSTR_TYPE = "org.multipaz.cbor.Bstr"
        const val TSTR_TYPE = "org.multipaz.cbor.Tstr"
        const val SIMPLE_TYPE = "org.multipaz.cbor.Simple"
        const val CBOR_MAP_TYPE = "org.multipaz.cbor.CborMap"
        const val CBOR_ARRAY_TYPE = "org.multipaz.cbor.CborArray"
        const val DATA_ITEM_CLASS = "org.multipaz.cbor.DataItem"
        const val BYTESTRING_TYPE = "kotlinx.io.bytestring.ByteString"
        const val TO_DATAITEM_DATETIMESTRING_FUN = "org.multipaz.cbor.toDataItemDateTimeString"
        const val TO_DATAITEM_FULLDATE_FUN = "org.multipaz.cbor.toDataItemFullDate"
        const val TO_DATAITEM_FUN = "org.multipaz.cbor.toDataItem"

        fun deserializeValue(
            codeBuilder: CodeBuilder,
            code: String,
            type: KSType
        ): String {
            val declaration = type.declaration
            val qualifiedName = declaration.qualifiedName!!.asString()
            return when (qualifiedName) {
                "kotlin.collections.Map", "kotlin.collections.MutableMap" ->
                    with(codeBuilder) {
                        val map = varName("map")
                        line("val $map = mutableMapOf<${typeArguments(this, type)}>()")
                        addDeserializedMapValues(this, map, code, type)
                        map
                    }

                "kotlin.collections.List", "kotlin.collections.MutableList",
                "kotlin.collections.Set", "kotlin.collections.MutableSet" ->
                    with(codeBuilder) {
                        val array = varName("array")
                        val builder = if (qualifiedName.endsWith("Set")) {
                            "mutableSetOf"
                        } else {
                            "mutableListOf"
                        }
                        line("val $array = $builder<${typeArguments(this, type)}>()")
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
                BYTESTRING_TYPE -> {
                    codeBuilder.importQualifiedName(BYTESTRING_TYPE)
                    return "ByteString($code.asBstr)"
                }
                "kotlin.Long" -> return "$code.asNumber"
                "kotlin.Int" -> return "$code.asNumber.toInt()"
                "kotlin.Float" -> return "$code.asFloat"
                "kotlin.Double" -> return "$code.asDouble"
                "kotlin.Boolean" -> return "$code.asBoolean"
                "kotlinx.datetime.Instant" -> "$code.asDateTimeString"
                "kotlinx.datetime.LocalDate" -> "$code.asDateString"
                DATA_ITEM_CLASS -> return code
                else -> return if (declaration is KSClassDeclaration &&
                    declaration.classKind == ClassKind.ENUM_CLASS
                ) {
                    "${typeRef(codeBuilder, type)}.valueOf($code.asTstr)"
                } else {
                    codeBuilder.importQualifiedName(qualifiedName)
                    val deserializer = deserializerName(declaration as KSClassDeclaration, false)
                    whenSerializationGenerated(declaration) {
                        val shortName = deserializer.substring(deserializer.lastIndexOf(".") + 1)
                        codeBuilder.importFunctionName(
                            shortName,
                            declaration.packageName.asString()
                        )
                    }
                    "${deserializer}($code)"
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
                    importQualifiedName(TSTR_TYPE)
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

        private fun findAnnotation(declaration: KSDeclaration, simpleName: String): KSAnnotation? {
            for (annotation in declaration.annotations) {
                if (annotation.shortName.asString() == simpleName &&
                    annotation.annotationType.resolve().declaration.packageName.asString() == ANNOTATION_PACKAGE
                ) {
                    return annotation
                }
            }
            return null
        }

        private fun deserializerName(
            classDeclaration: KSClassDeclaration, forDeclaration: Boolean): String {
            val baseName = classDeclaration.simpleName.asString()
            return if (hasCompanion(classDeclaration)) {
                if (forDeclaration) {
                    "${baseName}.Companion.fromDataItem"
                } else {
                    // for call
                    "${baseName}.fromDataItem"
                }
            } else {
                "${baseName}_fromDataItem"
            }
        }

        private fun hasCompanion(declaration: KSClassDeclaration): Boolean {
            return getCompanion(declaration) != null
        }

        fun getCompanion(declaration: KSClassDeclaration): KSClassDeclaration? {
            return declaration.declarations.filterIsInstance<KSClassDeclaration>()
                .firstOrNull { it.isCompanionObject }
        }

        fun serializeValue(
            codeBuilder: CodeBuilder,
            code: String,
            type: KSType
        ): String {
            val declaration = type.declaration
            val declarationQualifiedName = declaration.qualifiedName
                ?: throw NullPointerException("Declaration $declaration with null qualified name!")
            when (val qualifiedName = declarationQualifiedName.asString()) {
                "kotlin.collections.Map", "kotlin.collections.MutableMap" ->
                    with(codeBuilder) {
                        val map = varName("map")
                        val mapBuilder = varName("mapBuilder")
                        importQualifiedName(CBOR_MAP_TYPE)
                        line("val $mapBuilder = CborMap.builder()")
                        addSerializedMapValues(this, mapBuilder, code, type)
                        line("val $map: DataItem = $mapBuilder.end().build()")
                        return map
                    }

                "kotlin.collections.List", "kotlin.collections.MutableList",
                "kotlin.collections.Set", "kotlin.collections.MutableSet" ->
                    with(codeBuilder) {
                        val array = varName("array")
                        val arrayBuilder = varName("arrayBuilder")
                        importQualifiedName(CBOR_ARRAY_TYPE)
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

                "kotlin.String" -> {
                    codeBuilder.importQualifiedName(TSTR_TYPE)
                    return "Tstr($code)"
                }
                "kotlin.ByteArray" -> {
                    codeBuilder.importQualifiedName(BSTR_TYPE)
                    return "Bstr($code)"
                }
                BYTESTRING_TYPE -> {
                    codeBuilder.importQualifiedName(BSTR_TYPE)
                    return "Bstr($code.toByteArray())"
                }
                "kotlin.Int" -> {
                    codeBuilder.importQualifiedName(TO_DATAITEM_FUN)
                    return "$code.toLong().toDataItem()"
                }

                "kotlin.Long", "kotlin.Float", "kotlin.Double", "kotlin.Boolean" -> {
                    codeBuilder.importQualifiedName(TO_DATAITEM_FUN)
                    return "$code.toDataItem()"
                }

                "kotlinx.datetime.Instant" -> {
                    codeBuilder.importQualifiedName(TO_DATAITEM_DATETIMESTRING_FUN)
                    return "$code.toDataItemDateTimeString()"
                }

                "kotlinx.datetime.LocalDate" -> {
                    codeBuilder.importQualifiedName(TO_DATAITEM_FULLDATE_FUN)
                    return "$code.toDataItemFullDate()"
                }

                DATA_ITEM_CLASS -> return code
                else -> return if (declaration is KSClassDeclaration &&
                    declaration.classKind == ClassKind.ENUM_CLASS
                ) {
                    codeBuilder.importQualifiedName(TSTR_TYPE)
                    "Tstr($code.name)"
                } else {
                    codeBuilder.importQualifiedName(qualifiedName)
                    whenSerializationGenerated(declaration) { serializableDeclaration ->
                        codeBuilder.importFunctionName("toDataItem",
                            serializableDeclaration.packageName.asString())
                    }
                    "$code.toDataItem()"
                }
            }
        }

        private fun whenSerializationGenerated(
            declaration: KSDeclaration,
            block: (serializableDeclaration: KSDeclaration) -> Unit
        ) {
            var decl = declaration
            while (true) {
                if (findAnnotation(decl, ANNOTATION_SERIALIZABLE) != null) {
                    block(decl)
                } else if (decl is KSClassDeclaration) {
                    val supertypes = decl.superTypes.iterator()
                    if (supertypes.hasNext()) {
                        decl = supertypes.next().resolve().declaration
                        continue
                    }
                }
                break
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

        fun typeRef(codeBuilder: CodeBuilder, type: KSType): String {
            return when (val qualifiedName = type.declaration.qualifiedName!!.asString()) {
                "kotlin.collections.Map" -> "Map<${typeArguments(codeBuilder, type)}>"
                "kotlin.collections.List" -> "List<${typeArguments(codeBuilder, type)}>"
                "kotlin.String" -> "String"
                "kotlin.ByteArray" -> "ByteArray"
                "kotlin.Long" -> "Long"
                "kotlin.Int" -> "Int"
                "kotlin.Float" -> "Float"
                "kotlin.Double" -> "Double"
                "kotlin.Boolean" -> "Boolean"
                DATA_ITEM_CLASS -> "DataItem"
                else -> {
                    codeBuilder.importQualifiedName(qualifiedName)
                    return type.declaration.simpleName.asString()
                }
            }
        }

        fun typeRefNullable(codeBuilder: CodeBuilder, type: KSType): String {
            val typeStr = typeRef(codeBuilder, type)
            return if (type.isMarkedNullable) {
                "$typeStr?"
            } else {
                typeStr
            }
        }
    }

    /**
     * Processor main entry point.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation("$ANNOTATION_PACKAGE.$ANNOTATION_SERIALIZABLE")
            .filterIsInstance<KSClassDeclaration>().forEach Declaration@{ clazz ->
                for (supertype in clazz.superTypes) {
                    val superDeclaration = supertype.resolve().declaration
                    if (superDeclaration is KSClassDeclaration &&
                        superDeclaration.classKind == ClassKind.CLASS &&
                        superDeclaration.modifiers.contains(Modifier.SEALED)
                    ) {
                        val superAnnotation =
                            findAnnotation(superDeclaration, ANNOTATION_SERIALIZABLE)
                        if (superAnnotation == null) {
                            logger.error(
                                "Superclass is not marked with @$ANNOTATION_SERIALIZABLE",
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
                        if (findAnnotation(subclass, ANNOTATION_SERIALIZABLE) == null) {
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
        val typeKey: String = getTypeKey(findAnnotation(superDeclaration, ANNOTATION_SERIALIZABLE))
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
        val annotation = findAnnotation(classDeclaration, ANNOTATION_SERIALIZABLE)

        with(CodeBuilder()) {
            importQualifiedName(DATA_ITEM_CLASS)
            importQualifiedName(classDeclaration)

            generateSerialization(this, classDeclaration)

            block("fun $baseName.toDataItem(): DataItem") {
                block("return when (this)") {
                    for (subclass in subclasses) {
                        importQualifiedName(subclass)
                        val subclassName = subclass.simpleName.asString()
                        line("is $subclassName -> (this as $subclassName).toDataItem()")
                    }
                }
            }

            emptyLine()
            val deserializer = deserializerName(classDeclaration, true)
            block("fun $deserializer(dataItem: DataItem): $baseName") {
                val typeKey = getTypeKey(annotation)
                line("val type = dataItem[\"$typeKey\"].asTstr")
                block("return when (type)") {
                    for (subclass in subclasses) {
                        val typeId = getTypeId(classDeclaration, subclass)
                        line("\"$typeId\" -> ${deserializerName(subclass, false)}(dataItem)")
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
            importQualifiedName(DATA_ITEM_CLASS)
            importQualifiedName(classDeclaration)

            generateSerialization(this, classDeclaration)

            var hadMergedMap = false

            block("fun $baseName.toDataItem(): DataItem") {
                importQualifiedName(CBOR_MAP_TYPE)
                line("val builder = CborMap.builder()")
                if (typeKey != null) {
                    line("builder.put(\"$typeKey\", \"$typeId\")")
                }
                classDeclaration.getAllProperties().forEach { property ->
                    if (!property.hasBackingField) {
                        return@forEach
                    }
                    val name = property.simpleName.asString()
                    val type = property.type.resolve()
                    // We want exceptions to be serializable (if marked with @CborSerializable),
                    // but we want to skip cause
                    if (name == "cause" && type.declaration.qualifiedName?.asString() == "kotlin.Throwable") {
                        return@forEach
                    }
                    val (source, condition) = if (type.isMarkedNullable) {
                        val valueVar = varName(name)
                        line("val $valueVar = this.$name")
                        Pair(valueVar, "if ($valueVar != null)")
                    } else {
                        Pair("this.$name", null)
                    }
                    optionalBlock(condition) {
                        if (findAnnotation(property, ANNOTATION_MERGE) != null) {
                            if (hadMergedMap) {
                                logger.error("@$ANNOTATION_MERGE can be used only on a single field")
                            } else if (type.declaration.qualifiedName!!.asString() != "kotlin.collections.Map") {
                                logger.error("@$ANNOTATION_MERGE requires field of type Map")
                            } else {
                                hadMergedMap = true
                                addSerializedMapValues(
                                    this, "builder",
                                    source, type
                                )
                            }
                        } else {
                            val code = serializeValue(this, source, type)
                            line("builder.put(\"$name\", $code)")
                        }
                    }
                }
                line("return builder.end().build()")
            }

            val fieldNameSet = if (hadMergedMap) varName("fieldNameSet") else null
            val dataItem = varName("dataItem")

            emptyLine()
            val deserializer = deserializerName(classDeclaration, true)
            block("fun $deserializer($dataItem: DataItem): $baseName") {
                val constructorParameters = mutableListOf<String>()
                classDeclaration.getAllProperties().forEach { property ->
                    if (!property.hasBackingField) {
                        return@forEach
                    }
                    val type = property.type.resolve()
                    val fieldName = property.simpleName.asString()
                    // We want exceptions to be serializable (if marked with @CborSerializable),
                    // but we want to skip cause
                    if (fieldName == "cause" && type.declaration.qualifiedName?.asString() == "kotlin.Throwable") {
                        return@forEach
                    }
                    val name = varName(fieldName)
                    // Always use field names (and require constructor to use them as parameter
                    // names!), because the order of parameters in the constructor sometimes have
                    // to differ from the order of the fields (esp. when using inheritance).
                    constructorParameters.add("$fieldName = $name")
                    if (findAnnotation(property, ANNOTATION_MERGE) != null) {
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
                line("return $baseName(")
                withIndent {
                    constructorParameters.forEach { parameter ->
                        line("$parameter,")
                    }
                }
                line(")")
            }

            if (hadMergedMap) {
                emptyLine()
                line("private val $fieldNameSet = setOf(")
                withIndent {
                    classDeclaration.getAllProperties().forEach { property ->
                        if (!property.hasBackingField) {
                            return@forEach
                        }
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
        findAnnotation(subclass, ANNOTATION_SERIALIZABLE)?.arguments?.forEach { arg ->
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

    private fun generateSerialization(
        codeBuilder: CodeBuilder, classDeclaration: KSClassDeclaration) {

        codeBuilder.importQualifiedName("org.multipaz.cbor.Cbor")
        val baseName = classDeclaration.simpleName.asString()

        codeBuilder.block("fun $baseName.toCbor(): ByteArray") {
            line("return Cbor.encode(toDataItem())")
        }
        codeBuilder.emptyLine()

        if (hasCompanion(classDeclaration)) {
            codeBuilder.block("fun $baseName.Companion.fromCbor(data: ByteArray): $baseName") {
                line("return $baseName.fromDataItem(Cbor.decode(data))")
            }
            codeBuilder.emptyLine()
        }
    }
}

