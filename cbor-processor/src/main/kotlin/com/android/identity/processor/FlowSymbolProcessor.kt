package com.android.identity.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType

/**
 * Kotlin Annotation Processor that generates dispatching code and stub
 * implementations for flow-based network calls. It processes annotations
 * defined in [com.android.identity.flow.annotation] package.
 */
class FlowSymbolProcessor(
    private val options: Map<String, String>,
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    companion object {
        const val ANNOTATION_PACKAGE = "com.android.identity.flow.annotation"
        const val ANNOTATION_STATE = "FlowState"
        const val ANNOTATION_INTERFACE = "FlowInterface"
        const val ANNOTATION_METHOD = "FlowMethod"
        const val ANNOTATION_GETTER = "FlowGetter"
        const val ANNOTATION_JOIN = "FlowJoin"
        const val FLOW_HANDLER = "com.android.identity.flow.handler.FlowHandler"
        const val BASE_INTERFACE = "com.android.identity.flow.FlowBaseInterface"
        const val FLOW_HANDLER_LOCAL = "com.android.identity.flow.handler.FlowHandlerLocal"
        const val FLOW_ENVIRONMENT = "com.android.identity.flow.handler.FlowEnvironment"

        val stateSuffix = Regex("State$")
    }

    /**
     * Processor main entry point.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation("$ANNOTATION_PACKAGE.$ANNOTATION_STATE")
            .filterIsInstance<KSClassDeclaration>().forEach(this::processStateClass)
        resolver.getSymbolsWithAnnotation("$ANNOTATION_PACKAGE.$ANNOTATION_INTERFACE")
            .filterIsInstance<KSClassDeclaration>().forEach(this::processFlowInterface)
        return listOf()
    }

    private fun processStateClass(stateClass: KSClassDeclaration) {
        val annotation = findAnnotation(stateClass, ANNOTATION_STATE)
        val path = getStringArgument(annotation, "path", stateClass.simpleName.getShortName())
        val flowInterface = getClassArgument(annotation, "flowInterface")
        val flowInterfaceName = getFlowInterfaceName(stateClass, annotation)!!
        val joins = mutableMapOf<String, String>()  // interface to path
        val operations = mutableListOf<FlowOperationInfo>()
        val getters = mutableListOf<FlowGetterInfo>()
        val companionClass = CborSymbolProcessor.getCompanion(stateClass)
        if (companionClass == null) {
            logger.error("Companion object required", stateClass)
            return
        }

        collectFlowJoins(stateClass, joins, operations)
        collectFlowMethods(stateClass, joins, operations)
        collectFlowGetters(companionClass, getters)

        val lastDot = flowInterfaceName.lastIndexOf('.')
        val interfacePackage = flowInterfaceName.substring(0, lastDot)
        val interfaceName = flowInterfaceName.substring(lastDot + 1)
        val flowInfo = FlowInterfaceInfo(
            path, stateClass, interfacePackage, interfaceName, getters.toList(), operations.toList()
        )

        if (flowInterface == null) {
            val flowImplName = getStringArgument(
                annotation, "flowImplementationName", flowInterfaceName + "Impl"
            )
            val containingFile = stateClass.containingFile!!
            generateFlowInterface(flowInfo)
            generateFlowImplementation(
                containingFile, flowInterfaceName, interfaceName, flowImplName, getters, operations
            )
        }
        generateFlowRegistration(flowInfo)
    }

    private fun processFlowInterface(flowClass: KSClassDeclaration) {
        val annotation = findAnnotation(flowClass, ANNOTATION_INTERFACE)
        val operations = mutableListOf<FlowOperationInfo>()
        flowClass.getAllFunctions().forEach { function ->
            val methodAnnotation = findAnnotation(function, ANNOTATION_METHOD) ?: return@forEach
            val returnType = function.returnType?.resolve()
            val type = if (returnType?.declaration?.qualifiedName?.asString() == "kotlin.Unit") {
                null
            } else {
                returnType
            }
            val parameters = mutableListOf<FlowOperationParameterInfo>()
            function.parameters.forEach { parameter ->
                val parameterType = parameter.type.resolve()
                val parameterName = parameter.name?.asString()
                if (parameterName == null) {
                    logger.error("Parameter name required", parameter)
                } else {
                    val clientTypeInfo = getInterfaceFlowTypeInfo(parameterType)
                    parameters.add(
                        FlowOperationParameterInfo(
                            parameterName, clientTypeInfo, parameterType
                        )
                    )
                }
            }
            val clientTypeInfo = getInterfaceFlowTypeInfo(type)
            val methodName = function.simpleName.getShortName()
            val methodPath = getStringArgument(methodAnnotation, "path", methodName)
            operations.add(
                FlowOperationInfo(
                    false, methodPath, methodName, parameters, type, clientTypeInfo
                )
            )
        }
        val getters = mutableListOf<FlowGetterInfo>()
        flowClass.getAllFunctions().forEach { function ->
            val getterAnnotation = findAnnotation(function, ANNOTATION_GETTER)
            if (getterAnnotation != null) {
                val type = function.returnType!!.resolve()
                val getterName = function.simpleName.getShortName()
                val getterPath = getStringArgument(getterAnnotation, "path", getterName)
                getters.add(FlowGetterInfo(getterPath, getterName, type))
            }
        }
        val interfaceName = flowClass.simpleName.asString()
        val interfaceFullName = flowClass.qualifiedName!!.asString()
        val flowImplName = getStringArgument(
            annotation, "flowImplementationName", "${interfaceFullName}Impl"
        )
        val containingFile = flowClass.containingFile!!
        generateFlowImplementation(
            containingFile, interfaceFullName, interfaceName, flowImplName, getters, operations
        )
    }

    private fun collectFlowJoins(
        stateClass: KSClassDeclaration,
        joins: MutableMap<String, String>,
        operations: MutableList<FlowOperationInfo>
    ) {
        stateClass.getAllFunctions().forEach { function ->
            val joinAnnotation = findAnnotation(function, ANNOTATION_JOIN) ?: return@forEach
            val returnType = function.returnType?.resolve()?.declaration?.qualifiedName?.asString()
            if (returnType != null && returnType != "kotlin.Unit") {
                logger.error("FlowJoin methods must not return any value", function)
                return@forEach
            }
            val params = function.parameters
            if (params.size != 2) {
                logger.error("FlowJoin method must take 2 parameters", function)
                return@forEach
            }
            val param0Type = params[0].type.resolve()
            if (param0Type.declaration.qualifiedName?.asString() != FLOW_ENVIRONMENT) {
                logger.error(
                    "FlowJoin method's first parameter is not FlowEnvironment", function
                )
                return@forEach
            }
            val type = params[1].type.resolve()
            val typeInfo = getStateFlowTypeInfo(type)
            if (typeInfo == null) {
                logger.error("FlowJoin method's second parameter is not a flow state", function)
                return@forEach
            }
            val methodName = function.simpleName.getShortName()
            val methodPath = getStringArgument(joinAnnotation, "path", methodName)
            if (joins.containsKey(typeInfo.qualifiedInterfaceName)) {
                logger.error("Duplicate flow join for ${typeInfo.qualifiedInterfaceName}", function)
            } else {
                joins[typeInfo.qualifiedInterfaceName] = methodPath
                operations.add(
                    FlowOperationInfo(
                        true, methodPath, methodName, listOf(
                            FlowOperationParameterInfo("joiningFlow", typeInfo, type)
                        ), null, null
                    )
                )
            }
        }
    }

    private fun collectFlowMethods(
        stateClass: KSClassDeclaration,
        joins: Map<String, String>,
        operations: MutableList<FlowOperationInfo>
    ) {
        stateClass.getAllFunctions().forEach { function ->
            val methodAnnotation = findAnnotation(function, ANNOTATION_METHOD) ?: return@forEach
            val returnType = function.returnType?.resolve()
            val type = if (returnType?.declaration?.qualifiedName?.asString() == "kotlin.Unit") {
                null
            } else {
                returnType
            }
            val parameters = mutableListOf<FlowOperationParameterInfo>()
            var index = 0
            function.parameters.forEach { parameter ->
                val parameterType = parameter.type.resolve()
                if (index == 0) {
                    if (parameterType.declaration.qualifiedName?.asString() != FLOW_ENVIRONMENT) {
                        logger.error("First parameter must be FlowEnvironment", parameter)
                    }
                } else {
                    val parameterName = parameter.name?.asString()
                    if (parameterName == null) {
                        logger.error("Parameter name required", parameter)
                    } else {
                        val clientTypeInfo = getStateFlowTypeInfo(parameterType)
                        parameters.add(
                            FlowOperationParameterInfo(
                                parameterName, clientTypeInfo, parameterType
                            )
                        )
                    }
                }
                index++
            }
            val clientTypeInfo = getStateFlowTypeInfo(type)
            if (clientTypeInfo != null) {
                clientTypeInfo.joinPath = joins[clientTypeInfo.qualifiedInterfaceName]
            }
            val methodName = function.simpleName.getShortName()
            val methodPath = getStringArgument(methodAnnotation, "path", methodName)
            operations.add(
                FlowOperationInfo(
                    false, methodPath, methodName, parameters, type, clientTypeInfo
                )
            )
        }
    }

    private fun collectFlowGetters(
        stateCompanionClass: KSClassDeclaration, getters: MutableList<FlowGetterInfo>
    ) {
        stateCompanionClass.getAllFunctions().forEach { function ->
            val getterAnnotation = findAnnotation(function, ANNOTATION_GETTER)
            if (getterAnnotation != null) {
                val type = function.returnType!!.resolve()
                val methodName = function.simpleName.getShortName()
                val methodPath = getStringArgument(getterAnnotation, "path", methodName)
                getters.add(FlowGetterInfo(methodPath, methodName, type))
            }
        }
    }

    private fun generateFlowInterface(flowInfo: FlowInterfaceInfo) {
        with(CodeBuilder()) {
            importQualifiedName(CborSymbolProcessor.DATA_ITEM_CLASS)
            importQualifiedName(FLOW_HANDLER)
            importQualifiedName(BASE_INTERFACE)

            emptyLine()
            block("interface ${flowInfo.interfaceName}: FlowBaseInterface") {
                flowInfo.getters.forEach { getter ->
                    val type = CborSymbolProcessor.typeRef(this, getter.type)
                    line("suspend fun ${getter.name}(): $type")
                }
                flowInfo.operations.forEach { op ->
                    if (!op.hidden) {
                        line("suspend fun ${opDeclaration(this, op)}")
                    }
                }
            }

            writeToFile(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(false, flowInfo.stateClass.containingFile!!),
                packageName = flowInfo.interfacePackage,
                fileName = flowInfo.interfaceName
            )
        }
    }

    private fun generateFlowImplementation(
        containingFile: KSFile,
        interfaceFullName: String,
        interfaceName: String,
        flowImplName: String,
        getters: List<FlowGetterInfo>,
        operations: List<FlowOperationInfo>
    ) {
        val lastDotImpl = flowImplName.lastIndexOf('.')
        val packageName = flowImplName.substring(0, lastDotImpl)
        val baseName = flowImplName.substring(lastDotImpl + 1)
        with(CodeBuilder()) {
            importQualifiedName(CborSymbolProcessor.DATA_ITEM_CLASS)
            importQualifiedName(FLOW_HANDLER)
            importQualifiedName(CborSymbolProcessor.BSTR_TYPE)
            importQualifiedName(interfaceFullName)

            emptyLine()
            line("class $baseName(")
            withIndent {
                line("private val flowHandler: FlowHandler,")
                line("private val flowPath: String,")
                line("override var flowState: DataItem = Bstr(byteArrayOf()),")
                line("private val onComplete: suspend (DataItem) -> Unit = {}")
            }
            block("): $interfaceName") {
                line("private var flowComplete = false")
                getters.forEach { getter ->
                    generateFlowGetterStub(this, getter)
                }
                operations.forEach { op ->
                    if (!op.hidden) {
                        generateFlowMethodStub(this, op)
                    }
                }
                emptyLine()
                block("override suspend fun complete()") {
                    line("checkFlowNotComplete()")
                    line("onComplete(flowState)")
                    line("flowState = Bstr(byteArrayOf())")
                    line("flowComplete = true")
                }
                emptyLine()
                block("fun checkFlowNotComplete()") {
                    block("if (flowComplete)") {
                        line("throw IllegalStateException(\"flow is already complete\")")
                    }
                }
            }

            writeToFile(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(false, containingFile),
                packageName = packageName,
                fileName = baseName
            )
        }
    }

    private fun generateFlowGetterStub(codeBuilder: CodeBuilder, getter: FlowGetterInfo) {
        with(codeBuilder) {
            val type = CborSymbolProcessor.typeRef(this, getter.type)
            emptyLine()
            block("override suspend fun ${getter.name}(): $type") {
                line("val response = flowHandler.get(flowPath, \"${getter.path}\")")
                val result = CborSymbolProcessor.deserializeValue(
                    this, "response", getter.type
                )
                line("return $result")
            }
        }
    }

    private fun generateFlowMethodStub(codeBuilder: CodeBuilder, op: FlowOperationInfo) {
        with(codeBuilder) {
            emptyLine()
            block("override suspend fun ${opDeclaration(this, op)}") {
                line("checkFlowNotComplete()")
                line("val flowParameters = listOf<DataItem>(")
                withIndent {
                    line("this.flowState,")
                    op.parameters.forEach { parameter ->
                        val serialization = if (parameter.flowTypeInfo == null) {
                            CborSymbolProcessor.serializeValue(
                                this, parameter.name, parameter.type
                            )
                        } else {
                            "${parameter.name}.flowState"
                        }
                        line("$serialization,")
                    }
                }
                line(")")
                line("val flowMethodResponse = flowHandler.post(flowPath, \"${op.path}\", flowParameters)")
                line("this.flowState = flowMethodResponse[0]")
                if (op.flowTypeInfo != null) {
                    importQualifiedName(op.flowTypeInfo.qualifiedImplName)
                    val ctr = op.flowTypeInfo.simpleImplName
                    line("val packedFlow = flowMethodResponse[1].asArray")
                    line("val joinPath = packedFlow[1].asTstr")
                    block(
                        "return $ctr(flowHandler, packedFlow[0].asTstr, packedFlow[2])",
                        lambdaParameters = "joiningState"
                    ) {
                        block("if (joinPath.isNotEmpty())") {
                            line("val joinArgs = listOf<DataItem>(this.flowState, joiningState)")
                            line("val joinResponse = flowHandler.post(flowPath, joinPath, joinArgs)")
                            line("this.flowState = joinResponse[0]")
                        }
                    }
                } else if (op.type != null) {
                    val result = CborSymbolProcessor.deserializeValue(
                        this, "flowMethodResponse[1]", op.type
                    )
                    line("return $result")
                }
            }
        }
    }

    private fun getStateFlowTypeInfo(type: KSType?): FlowTypeInfo? {
        if (type == null) {
            return null
        }
        val annotation = findAnnotation(type.declaration, ANNOTATION_STATE)
        val fullInterfaceName = getFlowInterfaceName(
            type.declaration as KSClassDeclaration, annotation
        ) ?: return null
        val simpleInterfaceName =
            fullInterfaceName.substring(fullInterfaceName.lastIndexOf('.') + 1)
        val fullImplName = getStringArgument(
            annotation, "flowImplementationName", fullInterfaceName + "Impl"
        )
        val simpleImplName = fullImplName.substring(fullImplName.lastIndexOf('.') + 1)
        val path = getStringArgument(annotation, "path", type.declaration.simpleName.asString())
        return FlowTypeInfo(
            fullInterfaceName,
            simpleInterfaceName,
            fullImplName,
            simpleImplName,
            path
        )
    }

    private fun getInterfaceFlowTypeInfo(type: KSType?): FlowTypeInfo? {
        if (type == null) {
            return null
        }
        val annotation = findAnnotation(type.declaration, ANNOTATION_INTERFACE) ?: return null
        val fullInterfaceName = type.declaration.qualifiedName!!.asString()
        val simpleInterfaceName = type.declaration.simpleName.asString()
        val fullImplName = getStringArgument(
            annotation, "flowImplementationName", fullInterfaceName + "Impl"
        )
        val simpleImplName = fullImplName.substring(fullImplName.lastIndexOf('.') + 1)
        return FlowTypeInfo(
            fullInterfaceName,
            simpleInterfaceName,
            fullImplName,
            simpleImplName,
            null
        )
    }

    private fun getFlowInterfaceName(
        stateClass: KSClassDeclaration, annotation: KSAnnotation?
    ): String? {
        if (annotation == null) {
            return null
        }
        val flowInterface = getClassArgument(annotation, "flowInterface")
        return if (flowInterface != null) {
            flowInterface.qualifiedName!!.asString()
        } else {
            val defaultName =
                stateSuffix.replace(stateClass.qualifiedName!!.asString(), "") + "Flow"
            getStringArgument(annotation, "flowInterfaceName", defaultName)
        }
    }

    private fun opDeclaration(codeBuilder: CodeBuilder, op: FlowOperationInfo): String {
        val signature = signature(codeBuilder, op)
        if (op.type == null) {
            return "${op.name}($signature)"
        } else {
            val type = if (op.flowTypeInfo != null) {
                codeBuilder.importQualifiedName(op.flowTypeInfo.qualifiedInterfaceName)
                op.flowTypeInfo.simpleInterfaceName
            } else {
                CborSymbolProcessor.typeRef(codeBuilder, op.type)
            }
            return "${op.name}($signature): $type"
        }
    }

    private fun signature(codeBuilder: CodeBuilder, op: FlowOperationInfo): String {
        val builder = StringBuilder()
        op.parameters.forEach { parameter ->
            if (builder.isNotEmpty()) {
                builder.append(", ")
            }
            val type = if (parameter.flowTypeInfo != null) {
                codeBuilder.importQualifiedName(parameter.flowTypeInfo.qualifiedInterfaceName)
                parameter.flowTypeInfo.simpleInterfaceName
            } else {
                CborSymbolProcessor.typeRef(codeBuilder, parameter.type)
            }
            builder.append("${parameter.name}: $type")
        }
        return builder.toString()
    }

    private fun generateFlowRegistration(flowInfo: FlowInterfaceInfo) {
        val containingFile = flowInfo.stateClass.containingFile!!
        val packageName = flowInfo.stateClass.packageName.asString()
        val baseName = flowInfo.stateClass.simpleName.asString()
        with(CodeBuilder()) {
            importQualifiedName(CborSymbolProcessor.DATA_ITEM_CLASS)
            importQualifiedName(flowInfo.stateClass)
            importQualifiedName(FLOW_HANDLER_LOCAL)
            importQualifiedName(CborSymbolProcessor.BSTR_TYPE)

            block("private fun serialize(state: $baseName?): DataItem") {
                line("return state?.toDataItem ?: Bstr(byteArrayOf())")
            }

            emptyLine()
            block("private fun deserialize(state: DataItem): $baseName") {
                block(
                    "return if (state is Bstr && state.value.isEmpty())", hasBlockAfter = true
                ) {
                    line("$baseName()")
                }
                block("else", hasBlockBefore = true) {
                    line("$baseName.fromDataItem(state)")
                }
            }

            emptyLine()
            block("fun $baseName.Companion.register(flowHandlerBuilder: FlowHandlerLocal.Builder)") {
                block("flowHandlerBuilder.addFlow(\"${flowInfo.path}\", ::serialize, ::deserialize)") {
                    flowInfo.getters.forEach { getter ->
                        block(
                            "get(\"${getter.path}\")", lambdaParameters = "flowEnvironment"
                        ) {
                            line("val result = $baseName.${getter.name}(flowEnvironment)")
                            line(
                                CborSymbolProcessor.serializeValue(
                                    this, "result", getter.type
                                )
                            )
                        }
                    }
                    flowInfo.operations.forEach { op ->
                        block(
                            "post(\"${op.path}\")",
                            lambdaParameters = "flowEnvironment, cipher, flowState, flowMethodArgList"
                        ) {
                            var index = 0
                            val params = op.parameters.map { parameter ->
                                var param = "flowMethodArgList[$index]"
                                index++
                                if (parameter.flowTypeInfo != null) {
                                    importQualifiedName(CborSymbolProcessor.CBOR_TYPE)
                                    param = "Cbor.decode(cipher.decrypt($param.asBstr))"
                                }
                                CborSymbolProcessor.deserializeValue(
                                    this, param, parameter.type
                                )
                            }
                            val resVal = if (op.type == null) "" else "val result = "
                            line("${resVal}flowState.${op.name}(")
                            withIndent {
                                line("flowEnvironment,")
                                params.forEach {
                                    line("$it, ")
                                }
                            }
                            line(")")
                            if (op.type == null) {
                                line("Bstr(byteArrayOf())")
                            } else {
                                val serialized = CborSymbolProcessor.serializeValue(
                                    this, "result", op.type
                                )
                                if (op.flowTypeInfo != null) {
                                    importQualifiedName(CborSymbolProcessor.BSTR_TYPE)
                                    importQualifiedName(CborSymbolProcessor.TSTR_TYPE)
                                    importQualifiedName(CborSymbolProcessor.CBOR_TYPE)
                                    importQualifiedName(CborSymbolProcessor.CBOR_ARRAY_TYPE)
                                    line("CborArray(mutableListOf(")
                                    withIndent {
                                        line("Tstr(\"${op.flowTypeInfo.path}\"),")
                                        line("Tstr(\"${op.flowTypeInfo.joinPath}\"),")
                                        line("Bstr(cipher.encrypt(Cbor.encode($serialized)))")
                                    }
                                    line("))")
                                } else {
                                    line(
                                        CborSymbolProcessor.serializeValue(
                                            this, "result", op.type
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            writeToFile(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(false, containingFile),
                packageName = packageName,
                fileName = "${baseName}_Registration"
            )
        }
    }

    private fun findAnnotation(declaration: KSDeclaration, simpleName: String): KSAnnotation? {
        for (annotation in declaration.annotations) {
            if (annotation.shortName.asString() == simpleName && annotation.annotationType.resolve().declaration.packageName.asString() == ANNOTATION_PACKAGE) {
                return annotation
            }
        }
        return null
    }

    private fun getClassArgument(annotation: KSAnnotation?, name: String): KSClassDeclaration? {
        annotation?.arguments?.forEach { arg ->
            if (arg.name?.asString() == name) {
                val field = arg.value
                if (field is KSType && field.declaration.qualifiedName?.asString() != "kotlin.Unit") {
                    return field.declaration as KSClassDeclaration
                }
            }
        }
        return null
    }

    private fun getStringArgument(
        annotation: KSAnnotation?, name: String, defaultValue: String
    ): String {
        annotation?.arguments?.forEach { arg ->
            if (arg.name?.asString() == name) {
                val field = arg.value.toString()
                if (field.isNotEmpty()) {
                    return field
                }
            }
        }
        return defaultValue
    }

    data class FlowInterfaceInfo(
        val path: String,
        val stateClass: KSClassDeclaration,
        val interfacePackage: String,
        val interfaceName: String,
        val getters: List<FlowGetterInfo>,
        val operations: List<FlowOperationInfo>
    )

    data class FlowGetterInfo(
        val path: String, val name: String, val type: KSType
    )

    data class FlowOperationInfo(
        val hidden: Boolean,  // used for flow join methods
        val path: String,
        val name: String,
        val parameters: List<FlowOperationParameterInfo>,
        val type: KSType?,
        val flowTypeInfo: FlowTypeInfo?,
    )

    data class FlowOperationParameterInfo(
        val name: String, val flowTypeInfo: FlowTypeInfo?, val type: KSType
    )

    data class FlowTypeInfo(
        val qualifiedInterfaceName: String,
        val simpleInterfaceName: String,
        val qualifiedImplName: String,
        val simpleImplName: String,
        val path: String? = null,
        var joinPath: String? = null
    )
}