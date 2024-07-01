package com.csp.demo

import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class LocalValTest() {

    @Test
    fun test() {
        testBoolVal()
    }
}

fun testPrimitiveVal() {
    val a = 1
    val b = 3

    val c = a + b + 5
    val d = c

    println(1)
    println(a)
    println(c)
    println(d)
}

fun testBoolVal() {
    val b = true
    val c = b and false
    val d = b or false
    val e = b.not()
    val f = b xor false
    val g = b == true
    val h = b > true
    val i = b >= true
    val j = b < true
    val k = b <= true
    println(b)
    println(c)
    println(d)
    println(e)
    println(f)
    println(g)
    println(h)
    println(i)
    println(j)
    println(k)
}

fun testPrint() {
    println(1)
    println("hi")
}

const val cmsg = "hi"

fun testString() {
    val msg = "hi"
    println(msg)
    println(cmsg)
    println("hi")
}

fun testBlock(b: Boolean): Int {
    val a = 1
    if (b) {
        val a = ""
        println(a)
        thread {
            val a = 2
            println(a)
        }
        val b = "$a!"
    }
    return a + 1
}