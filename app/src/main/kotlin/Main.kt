package com.csp.demo

fun main() {
    val t = Test()
    println(t.mem)
    Test::class.java.getDeclaredField("mem").apply {
        isAccessible = true
        set(t, 456)
    }
    //可以看到虽然编译后，mem是final修饰，但还是可以被反射修改，跟java 里的final常量还是有区别的
    println(t.mem)
}

