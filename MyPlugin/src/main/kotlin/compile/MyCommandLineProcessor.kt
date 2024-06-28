package com.csp.plugin.compile

import com.csp.plugin.groupId
import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
class MyCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = groupId

    override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}