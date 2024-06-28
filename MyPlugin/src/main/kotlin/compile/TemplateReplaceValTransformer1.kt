package com.csp.plugin.compile

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.types.impl.toBuilder
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.util.toIrConst
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.experimental.xor

class TemplateReplaceValTransformer1(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoidWithContext() {

    //注意这里key需要用symbol，不能直接使用变量名，因为在嵌套代码中有可重复命名的问题
    private val variableMap = HashMap<IrValueSymbol, IrConst<*>>()

    // 1. 找到并缓存常量
    override fun visitVariable(declaration: IrVariable): IrStatement {
        val visitVariable = super.visitVariable(declaration)
        return if (checkAndGetConst(variableMap, declaration)) {
            DeleteStatement()
        } else {
            visitVariable
        }
    }

    private fun checkAndGetConst(map: HashMap<IrValueSymbol, IrConst<*>>, declaration: IrVariable): Boolean {
        // 首先，必须是val类型；其次赋值表达式不为空
        if (declaration.isConst || declaration.isVar || declaration.initializer == null) {
            return false
        }

        if (declaration.name.asString().isEmpty()) {
            return false
        }

        //基本数据类型
        if (declaration.type.isPrimitiveType() && declaration.initializer is IrConst<*>) {
            map[declaration.symbol] = (declaration.initializer as IrConst<*>).value.toIrConst(
                (declaration.type as IrSimpleTypeImpl).toBuilder().buildSimpleType()
            )
            return true
        }

        //如果赋值表达式本身就是常量类型,其中包括：
        //1.基本数据类型
        //2.字符串类型
        //3.null类型
        //（引用类型转换会反而多出一步指令(⊙﹏⊙)）
        /*if (declaration.initializer is IrConst<*>) {
            map[declaration.name.asString()] =
                Pair(
                    (declaration.initializer as IrConst<*>).value,
                    (declaration.type as IrSimpleTypeImpl).toBuilder().buildSimpleType()
                )
        }*/

        //如果变量类型是无符号类型
        //(不需要转换，此类型对应Java中为引用类型)
        /*if (declaration.type.isUnsignedType()) {
            map[declaration.name.asString()] =
                Pair(
                    (((declaration as IrVariableImpl).initializer as IrCallImpl).extensionReceiver as IrConstImpl<*>).value,
                    //注意，此处应传入自身类型
                    (declaration.type as IrSimpleTypeImpl).toBuilder().buildSimpleType()
                )
        }*/

        //字符串类型
        /*if (declaration.type.isString() && declaration.initializer is IrConst<*>) {
          map[declaration.symbol] = (declaration.initializer as IrConst<*>).value.toIrConst(
            (declaration.type as IrSimpleTypeImpl).toBuilder().buildSimpleType()
          )
          return true
        }*/
        return false
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        if (variableMap.containsKey(expression.symbol)) {
            return variableMap[expression.symbol]!!
        }
        return super.visitGetValue(expression)
    }

    // 2.判断是否需要删除子表达式
    override fun visitBody(body: IrBody): IrBody {
        return super.visitBody(body).also {
            // 判断是否实现了IrStatementContainer
            if (it is IrStatementContainer) {
                // 判断并删除ConstStatement类型的项
                it.statements.removeIf { statement ->
                    statement is ConstStatement
                }
            }
        }
    }

    //判断是否需要删除子表达式
    override fun visitExpression(expression: IrExpression): IrExpression {
        return super.visitExpression(expression).also {
            // 判断是否实现了IrStatementContainer
            if (it is IrStatementContainer) {
                // 判断并删除ConstStatement类型的项
                it.statements.removeIf { statement ->
                    statement is ConstStatement
                }
            }
        }
    }

    //3. 处理表达式
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
        return super.visitFunctionAccess(expression).also {
            if (it is IrCall) {
                return when (it.symbol.owner.name.asString()) {

                    //有两个参数的计算
                    "plus", "minus", "times", "div", "rem",
                    "and", "or",/* "inv",*/ "xor",
                    "shl", "shr", "ushr" -> calConst(it)

                    //只有一个参数
                    "unaryPlus", "unaryMinus", "inv" -> oneArgConst(it)

                    //比较运算
                    "greaterOrEqual", "greater", "less", "lessOrEqual", "EQEQ" -> compareConst(it)

                    else -> it
                }
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun calConst(expression: IrCall): IrExpression {
        return if (expression.dispatchReceiver is IrConst<*> &&
            expression.valueArguments.size == 1 &&
            expression.valueArguments[0] is IrConst<*>
        ) {
            val `this` = (expression.dispatchReceiver as IrConst<*>).value
            val other = (expression.valueArguments[0] as IrConst<*>).value
            val res = if (`this` == null || other == null) {
                expression
            } else when (expression.symbol.owner.name.asString()) {
                "plus" -> plusOrDefault(`this`, other, expression)
                "minus" -> minusOrDefault(`this`, other, expression)
                "times" -> timesOrDefault(`this`, other, expression)
                "div" -> divOrDefault(`this`, other, expression)
                "rem" -> remOrDefault(`this`, other, expression)
                "and" -> andOrDefault(`this`, other, expression)
                "or" -> orOrDefault(`this`, other, expression)
                "xor" -> xorOrDefault(`this`, other, expression)
                "shl" -> shlOrDefault(`this`, other, expression)
                "shr" -> shrOrDefault(`this`, other, expression)
                "ushr" -> ushrOrDefault(`this`, other, expression)
                else -> expression
            }
            res
        } else expression
    }

    fun plusOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` + other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` + other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` + other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` + other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` + other).toIrConst(pluginContext.irBuiltIns.longType)
                is Short -> (`this` + other).toIrConst(pluginContext.irBuiltIns.longType)
                is Int -> (`this` + other).toIrConst(pluginContext.irBuiltIns.longType)
                is Long -> (`this` + other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Short -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Int -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Long -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Float -> (`this` + other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Short -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Int -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Long -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Float -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Double -> (`this` + other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            else -> default
        }
    }

    fun minusOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` - other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` - other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` - other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` - other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` - other).toIrConst(pluginContext.irBuiltIns.longType)
                is Short -> (`this` - other).toIrConst(pluginContext.irBuiltIns.longType)
                is Int -> (`this` - other).toIrConst(pluginContext.irBuiltIns.longType)
                is Long -> (`this` - other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Short -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Int -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Long -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Float -> (`this` - other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Short -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Int -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Long -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Float -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Double -> (`this` - other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            else -> default
        }
    }

    fun timesOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` * other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` * other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` * other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` * other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` * other).toIrConst(pluginContext.irBuiltIns.longType)
                is Short -> (`this` * other).toIrConst(pluginContext.irBuiltIns.longType)
                is Int -> (`this` * other).toIrConst(pluginContext.irBuiltIns.longType)
                is Long -> (`this` * other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Short -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Int -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Long -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Float -> (`this` * other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Short -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Int -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Long -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Float -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Double -> (`this` * other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            else -> default
        }
    }

    fun divOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` / other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` / other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` / other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` / other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` / other).toIrConst(pluginContext.irBuiltIns.longType)
                is Short -> (`this` / other).toIrConst(pluginContext.irBuiltIns.longType)
                is Int -> (`this` / other).toIrConst(pluginContext.irBuiltIns.longType)
                is Long -> (`this` / other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Short -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Int -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Long -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Float -> (`this` / other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Short -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Int -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Long -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Float -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Double -> (`this` / other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            else -> default
        }
    }

    fun remOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` % other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` % other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Short -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Int -> (`this` % other).toIrConst(pluginContext.irBuiltIns.intType)
                is Long -> (`this` % other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` % other).toIrConst(pluginContext.irBuiltIns.longType)
                is Short -> (`this` % other).toIrConst(pluginContext.irBuiltIns.longType)
                is Int -> (`this` % other).toIrConst(pluginContext.irBuiltIns.longType)
                is Long -> (`this` % other).toIrConst(pluginContext.irBuiltIns.longType)
                is Float -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Short -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Int -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Long -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Float -> (`this` % other).toIrConst(pluginContext.irBuiltIns.floatType)
                is Double -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Short -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Int -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Long -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Float -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                is Double -> (`this` % other).toIrConst(pluginContext.irBuiltIns.doubleType)
                else -> default
            }

            else -> default
        }
    }

    fun andOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` and other).toIrConst(pluginContext.irBuiltIns.byteType)
                else -> default
            }

            is Short -> when (other) {
                is Short -> (`this` and other).toIrConst(pluginContext.irBuiltIns.shortType)
                else -> default
            }

            is Int -> when (other) {
                is Int -> (`this` and other).toIrConst(pluginContext.irBuiltIns.intType)
                else -> default
            }

            is Long -> when (other) {
                is Long -> (`this` and other).toIrConst(pluginContext.irBuiltIns.longType)
                else -> default
            }

            else -> default
        }
    }

    fun orOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` or other).toIrConst(pluginContext.irBuiltIns.byteType)
                else -> default
            }

            is Short -> when (other) {
                is Short -> (`this` or other).toIrConst(pluginContext.irBuiltIns.shortType)
                else -> default
            }

            is Int -> when (other) {
                is Int -> (`this` or other).toIrConst(pluginContext.irBuiltIns.intType)
                else -> default
            }

            is Long -> when (other) {
                is Long -> (`this` or other).toIrConst(pluginContext.irBuiltIns.longType)
                else -> default
            }

            else -> default
        }
    }

    fun invOrDefault(`this`: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> (`this`.inv()).toIrConst(pluginContext.irBuiltIns.byteType)
            is Short -> (`this`.inv()).toIrConst(pluginContext.irBuiltIns.shortType)
            is Int -> (`this`.inv()).toIrConst(pluginContext.irBuiltIns.intType)
            is Long -> (`this`.inv()).toIrConst(pluginContext.irBuiltIns.longType)
            else -> default
        }
    }

    fun xorOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` xor other).toIrConst(pluginContext.irBuiltIns.byteType)
                else -> default
            }

            is Short -> when (other) {
                is Short -> (`this` xor other).toIrConst(pluginContext.irBuiltIns.shortType)
                else -> default
            }

            is Int -> when (other) {
                is Int -> (`this` xor other).toIrConst(pluginContext.irBuiltIns.intType)
                else -> default
            }

            is Long -> when (other) {
                is Long -> (`this` xor other).toIrConst(pluginContext.irBuiltIns.longType)
                else -> default
            }

            else -> default
        }
    }

    fun shlOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Int -> when (other) {
                is Int -> (`this` shl other).toIrConst(pluginContext.irBuiltIns.intType)
                else -> default
            }

            is Long -> when (other) {
                is Int -> (`this` shl other).toIrConst(pluginContext.irBuiltIns.longType)
                else -> default
            }

            else -> default
        }
    }

    fun shrOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Int -> when (other) {
                is Int -> (`this` shr other).toIrConst(pluginContext.irBuiltIns.intType)
                else -> default
            }

            is Long -> when (other) {
                is Int -> (`this` shr other).toIrConst(pluginContext.irBuiltIns.longType)
                else -> default
            }

            else -> default
        }
    }

    fun ushrOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Int -> when (other) {
                is Int -> (`this` ushr other).toIrConst(pluginContext.irBuiltIns.intType)
                else -> default
            }

            is Long -> when (other) {
                is Int -> (`this` ushr other).toIrConst(pluginContext.irBuiltIns.longType)
                else -> default
            }

            else -> default
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun oneArgConst(expression: IrCall): IrExpression {
        return if (expression.dispatchReceiver is IrConst<*>) {
            val `this` = (expression.dispatchReceiver as IrConst<*>).value
            val res = if (`this` == null) {
                expression
            } else when (expression.symbol.owner.name.asString()) {
                "unaryPlus" -> unaryPlusOrDefault(`this`, expression)
                "unaryMinus" -> unaryMinusOrDefault(`this`, expression)
                "inv" -> invOrDefault(`this`, expression)
                else -> expression
            }
            res
        } else expression
    }

    fun unaryPlusOrDefault(`this`: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> (`this`.unaryPlus()).toIrConst(pluginContext.irBuiltIns.byteType)
            is Short -> (`this`.unaryPlus()).toIrConst(pluginContext.irBuiltIns.shortType)
            is Int -> (`this`.unaryPlus()).toIrConst(pluginContext.irBuiltIns.intType)
            is Long -> (`this`.unaryPlus()).toIrConst(pluginContext.irBuiltIns.longType)
            is Float -> (`this`.unaryPlus()).toIrConst(pluginContext.irBuiltIns.floatType)
            is Double -> (`this`.unaryPlus()).toIrConst(pluginContext.irBuiltIns.doubleType)
            else -> default
        }
    }

    fun unaryMinusOrDefault(`this`: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> (`this`.unaryMinus()).toIrConst(pluginContext.irBuiltIns.byteType)
            is Short -> (`this`.unaryMinus()).toIrConst(pluginContext.irBuiltIns.shortType)
            is Int -> (`this`.unaryMinus()).toIrConst(pluginContext.irBuiltIns.intType)
            is Long -> (`this`.unaryMinus()).toIrConst(pluginContext.irBuiltIns.longType)
            is Float -> (`this`.unaryMinus()).toIrConst(pluginContext.irBuiltIns.floatType)
            is Double -> (`this`.unaryMinus()).toIrConst(pluginContext.irBuiltIns.doubleType)
            else -> default
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun compareConst(expression: IrCall): IrExpression {
        return if (expression.valueArguments.size == 2 &&
            expression.valueArguments[0] is IrConst<*> &&
            expression.valueArguments[1] is IrConst<*>
        ) {
            val arg0 = (expression.valueArguments[0] as IrConst<*>).value
            val arg1 = (expression.valueArguments[1] as IrConst<*>).value
            val res = if (arg0 == null || arg1 == null) {
                expression
            } else when (expression.symbol.owner.name.asString()) {
                "greaterOrEqual" -> greaterOrEqualOrDefault(arg0, arg1, expression)
                "greater" -> greaterOrDefault(arg0, arg1, expression)
                "EQEQ" -> EQEQOrDefault(arg0, arg1, expression)
                "less" -> lessOrDefault(arg0, arg1, expression)
                "lessOrEqual" -> lessOrEqualOrDefault(arg0, arg1, expression)
                else -> expression
            }
            res
        } else expression
    }

    fun greaterOrEqualOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` >= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            else -> default
        }
    }

    fun greaterOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` > other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            else -> default
        }
    }

    fun EQEQOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` == other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Short -> when (other) {
                is Short -> (`this` == other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Int -> when (other) {
                is Int -> (`this` == other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Long -> when (other) {
                is Long -> (`this` == other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Float -> when (other) {
                is Float -> (`this` == other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Double -> when (other) {
                is Double -> (`this` == other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            else -> default
        }
    }

    fun lessOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` < other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            else -> default
        }
    }

    fun lessOrEqualOrDefault(`this`: Any, other: Any, default: IrExpression): IrExpression {
        return when (`this`) {
            is Byte -> when (other) {
                is Byte -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Short -> when (other) {
                is Byte -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Int -> when (other) {
                is Byte -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Long -> when (other) {
                is Byte -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Float -> when (other) {
                is Byte -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            is Double -> when (other) {
                is Byte -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Short -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Int -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Long -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Float -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                is Double -> (`this` <= other).toIrConst(pluginContext.irBuiltIns.booleanType)
                else -> default
            }

            else -> default
        }
    }

}
