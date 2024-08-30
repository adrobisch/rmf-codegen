package io.vrap.codegen.languages.rust

fun String.readFromClassPath(): String? {
    return String.Companion::class.java.classLoader.getResourceAsStream(this)?.reader(Charsets.UTF_8)?.readText()
}