package com.csp.plugin.common

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

fun MessageCollector.info(msg: String) {
    report(CompilerMessageSeverity.INFO, msg)
}