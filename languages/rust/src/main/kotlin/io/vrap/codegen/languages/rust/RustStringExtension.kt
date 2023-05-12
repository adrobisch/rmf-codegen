package io.vrap.codegen.languages.rust

import io.vrap.rmf.raml.model.util.StringCaseFormat

fun String.toRelativePackageName(base: String): String {
    val partsTo = this.split(".")
    val partsFrom = base.split(".")

    var path = ""
    for (i in partsFrom.size - 1 downTo 0) {
        if (i < partsTo.size) {
            if (partsFrom.slice(0..i).joinToString(".") == partsTo.slice(0..i).joinToString(".")) {
                if (path == "") path = "."
                return path + partsTo.slice(i + 1..partsTo.size - 1).joinToString(separator = ".")
            }
        }
        path += "."
    }
    return path + this
}

fun String.rustModuleFileName(): String {
    return this.split("/")
            .map { StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(it) }
            .joinToString(separator = "/")
            .replace("platform/", "api_")
            .replace("models/", "models_")
            .replace("_+".toRegex(), "_")
}

fun String.exportName(): String {
    return this.replace(".", "_")
}

fun String.rustName(): String {
    if (this == "type") {
        return "type_hint"
    }
    return StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(this.replace(".", "_"))
}

fun String.rustEnumName(): String {
    return StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(this.replace(".", "_"))
}

fun String.snakeCase(): String {
    return StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(this.replace(".", "_"))
}
