package com.csp.demo

import kotlin.concurrent.thread

fun testVal() {
    val a = 1
    val b = 3

    val c = a + b + 5
    val d = c

    println(1)
    println(a)
    println(c)
    println(d)
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