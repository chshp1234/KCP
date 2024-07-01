package com.csp.demo

import kotlin.concurrent.thread

class Test {
    val mem = 123

    companion object {
        const val constMsg = "hello"
    }

    fun testVal(arg: Int) {

        val a = mem
        val b = 2
        val c = a + b
//        val d: Int? = null
////        d = ""
//        val e = Int.MIN_VALUE.toUInt()
//        val f = arg
//        val msg = "Hello World!"
        val g = a
        val h = a

//        println(msg)
//        println(constMsg)
        println(a)
//        println(b)
//        println(c)
//        println(d)
//        println(e)
//        println(f)
        println(g)
        println(h)
        println(a)
    }
}

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
        thread {
            val ggg = 7
        }
    } else {
        val hhh = 8
    }
}*/

fun testOperator() {
    val z = 1

    val a = z + 2
    val b = z - 2
    val c = z * 2
    val d = z / 2
    val e = z % 2
    val f = +z
    val g = -z
    val h = z >= 1
    val i = z > 1
    val j = z < 1
    val k = z <= 1
    val l = z shl 2
    val m = z shr 2
    val n = z ushr 2
    val o = z and 2
    val p = z or 2
    val q = z.inv()
    val r = z xor 2
    val v = z.compareTo(1)

    var s = 1
    s -= 2
    s += 2
    s *= 2
    s /= 2
    s %= 2
    val t = s++
    val u = s--

}

fun testBoolOp() {
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
}