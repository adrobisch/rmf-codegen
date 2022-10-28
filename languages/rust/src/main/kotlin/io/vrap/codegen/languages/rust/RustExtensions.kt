package io.vrap.codegen.languages.rust

import io.vrap.rmf.raml.model.types.DescriptionFacet

fun DescriptionFacet.toBlockComment(): String {
    val description = this.description
    return if (description?.value.isNullOrBlank()) {
        ""
    } else description.value
        .lines()
        .joinToString(prefix = "/**\n*\t", postfix = "\n*/", separator = "\n*\t")
}

fun DescriptionFacet.toLineComment(): String {
    val description = this.description
    return if (description?.value.isNullOrBlank()) {
        ""
    } else description.value
        .lines()
        .joinToString(separator = "\n")
        .trimMargin()
        .prependIndent("// ")
}
