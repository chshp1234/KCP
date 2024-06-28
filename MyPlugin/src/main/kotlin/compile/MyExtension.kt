package com.csp.plugin.compile

import com.csp.plugin.common.info
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump

class MyExtension(private val messageCollector: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.info("before transform ========>\n${moduleFragment.dump()}")

        moduleFragment.transform(TemplateFunctionTransformer(messageCollector,pluginContext), null)

        messageCollector.info("after  transform ========>\n${moduleFragment.dump()}")
    }
}