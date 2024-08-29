package io.vrap.codegen.languages.rust.client

import com.damnhandy.uri.template.Expression
import com.damnhandy.uri.template.UriTemplate
import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.toResourceName
import io.vrap.codegen.languages.rust.exportName
import io.vrap.codegen.languages.rust.snakeCase
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.model.util.StringCaseFormat
import java.util.stream.Collectors

fun Method.toRequestName(): String {
    return this.resource().fullUri.toParamName("")
}

fun UriTemplate.toParamName(delimiter: String): String {
    return this.toParamName(delimiter, "")
}

fun UriTemplate.toParamName(delimiter: String, suffix: String): String {
    val snakeCase = this.components.stream().map { uriTemplatePart ->
        if (uriTemplatePart is Expression) {
            val foo = uriTemplatePart.varSpecs.stream()
                .map { s -> delimiter + "_" + s.variableName.snakeCase() + suffix }
                .collect(Collectors.joining("_"))
            return@map foo
        }
        StringCaseFormat.LOWER_UNDERSCORE_CASE.apply(uriTemplatePart.toString().replace("/", "_").replace("=", ""))
    }.collect(Collectors.joining("_")).snakeCase()
    return snakeCase
}

fun Resource.toRequestBuilderName(): String = "${this.toResourceName()}Request"

fun Resource.toStructName(): String {
    return this.toRequestBuilderName().exportName()
}

fun Resource.rustResourceName(): String {
    return resourcePathName.snakeCase()
}

fun Resource.rustClientFileName(): String {
    return listOf(
        "client",
        rustResourceName(),
        this.toResourceName().snakeCase()
    ).filter { x -> x != "" }.joinToString(separator = "_")
}

fun Method.bodyType(): AnyType? {
    if (bodies.isNotEmpty()) {
        return bodies[0].type
    }
    return null
}
