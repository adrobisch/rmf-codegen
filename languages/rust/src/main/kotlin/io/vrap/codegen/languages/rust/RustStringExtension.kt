package io.vrap.codegen.languages.rust

import io.vrap.rmf.raml.model.util.StringCaseFormat

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
    return StringCaseFormat.UPPER_CAMEL_CASE.apply(this.replace(".", "_"))
}

fun String.snakeCase(): String {
    return StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(this.replace(".", "_"))
}
