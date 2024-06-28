package com.csp.plugin.compile

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction

class TemplateFunctionTransformer(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext
) : IrElementTransformerVoidWithContext() {

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        declaration.transform(TemplateReplaceValTransformer1(messageCollector, pluginContext), null)
        return super.visitFunctionNew(declaration)
    }
}
