package com.csp.plugin.compile

import com.csp.plugin.common.info
import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@ExperimentalCompilerApi
@AutoService(CompilerPluginRegistrar::class)
class MyRegister : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(
            CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            MessageCollector.NONE
        )
        // 使用编译选项 --info即可打印出编译信息 （print无法查看，不加--info也无法查看 ）
        messageCollector.info("-----registerExtensions-----")
        IrGenerationExtension.registerExtension(
            MyExtension(
                configuration.get(
                    CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                    MessageCollector.NONE
                )
            )
        )
    }
}