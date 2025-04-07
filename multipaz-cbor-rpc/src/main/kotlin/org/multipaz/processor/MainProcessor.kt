package org.multipaz.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * Processor that multiplexes [CborSymbolProcessor] and [RpcSymbolProcessor].
 */
class MainProcessor(
    private val options: Map<String, String>,
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
    val cborSymbolProcessor = CborSymbolProcessor(options, logger, codeGenerator)
    val rpcSymbolProcessor = RpcSymbolProcessor(options, logger, codeGenerator)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val list = mutableListOf<KSAnnotated>()
        list.addAll(cborSymbolProcessor.process(resolver))
        list.addAll(rpcSymbolProcessor.process(resolver))
        return list
    }

}