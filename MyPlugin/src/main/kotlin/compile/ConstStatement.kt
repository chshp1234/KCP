package com.csp.plugin.compile

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

interface ConstStatement : IrStatement

class DeleteStatement : ConstStatement {
  override val startOffset: Int = 0
  override val endOffset: Int = 0

  override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
    throw Exception("不做处理")
  }

  override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
    throw Exception("不做处理")
  }

  override fun <D> transform(transformer: IrElementTransformer<D>, data: D): IrElement {
    throw Exception("不做处理")
  }

  override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
    throw Exception("不做处理")
  }
}
