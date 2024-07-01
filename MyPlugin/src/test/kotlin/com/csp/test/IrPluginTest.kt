/*
 * Copyright (C) 2020 Brian Norman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalCompilerApi::class)

package com.csp.test

import com.csp.plugin.compile.MyRegister
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test


class IrPluginTest {


    @Test
    fun test() {
        println("hello world")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `IR plugin success`() {
        val result = compile(
            sourceFile = SourceFile.kotlin(
                "main.kt", """
          
import kotlin.concurrent.thread

fun testBooleanVal() {
    val b = true
    val h = b > true
    val i = b >= true
    val j = b < true
    val k = b <= true
}

/*fun testVal() {
    val a = 1
    val b = 3

    val c = a + b + 5
    val d = c

    println(1)
    println(a)
    println(c)
    println(d)

    testIL()
}*/

inline fun testIL(){
val c=1
}

//const val msg = "Hello World!"


/*fun fffffff(b: Boolean): Int {
    val aaaaaa = 1
    if (b) {
        val aaaaaa = "Hello World!"
        println(aaaaaa)
        println(msg)
        thread {
            val aaaaaa = 2
            println(aaaaaa)
        }
        val b =aaaaaa+ "!"
    }
    return aaaaaa + 1
}*/

/*fun testString(){
    val aaaaaa = "Hello World!"
    println(aaaaaa)
    println(msg)
}*/

/*fun testBlock(change: Boolean) {
    val aaa = 1
    if (change) {
        val bbb = 2
        while (change) {
            val ccc = 3
        }
        for (i in 0..3) {
            val ddd = 4
        }
        try {
            val eee = 5
        } catch (_: Exception) {
            val fff = 6
        }
    } else {
        val ggg = 7
    }
}*/

//fun testCall(){
//    val z = 1

//    val a = z + 2
//    val b = z - 2
//    val c = z * 2
//    val d = z / 2
//    val e = z % 2
//    val f = +z
//    val g = -z
//    val h = z >= 1
//    val i = z > 1
//    val j = z < 1
//    val k = z <= 1
//    val s = z == 1
//    val l = z shl 2
//    val m = z shr 2
//    val n = z ushr 2
//    val o = z and 2
//    val p = z or 2
//    val q = z.inv()
//    val r = z xor 2
//}

/*fun main() {
//    val imsg="Hello World!"
//    println(msg)
//    println(imsg)
//    println("Hello World!")
    testVal(false)
}

fun testVal(change:Boolean) {
//    val a = 1
//    val b = 1
    val c:String? = null
//    val d = 1 + 3
//    var e = if (change) 3 else -3
//    b = if (change) 2 else -2
//
//    var d = a + b +c
//    var e = 1+3
//    var f = 1+a
//    var g = 1+c
    val h = Int.MIN_VALUE.toUInt()
    *//*if (change) {
        val h=123
    }*//*

//    println(d)
//    println(e)
//    println(f)
//    println(g)
    println(h)
    println(msg)
}*/


        """
            )
        )
//        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val ktClazz = result.classLoader.loadClass("MainKt")
        val main = ktClazz.declaredMethods.find { it.name == "main" && it.parameterCount == 0 }
        main?.invoke(null)
    }
}

@OptIn(ExperimentalCompilerApi::class)
fun compile(
    sourceFiles: List<SourceFile>,
    plugin: CompilerPluginRegistrar = MyRegister(),
): JvmCompilationResult {
    return KotlinCompilation().apply {
        sources = sourceFiles
        compilerPluginRegistrars = listOf(plugin)
        inheritClassPath = true
    }.compile()
}

@OptIn(ExperimentalCompilerApi::class)
fun compile(
    sourceFile: SourceFile,
    plugin: CompilerPluginRegistrar = MyRegister(),
): JvmCompilationResult {
    return compile(listOf(sourceFile), plugin)
}
