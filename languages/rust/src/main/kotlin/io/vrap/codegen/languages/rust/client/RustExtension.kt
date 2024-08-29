package io.vrap.codegen.languages.rust.client

import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.toResourceName
import io.vrap.codegen.languages.rust.exportName
import io.vrap.codegen.languages.rust.snakeCase
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.AnyType

fun Resource.toRequestBuilderName(): String = "${this.toResourceName()}Request"

fun Resource.toStructName(): String {
    return this.toRequestBuilderName().exportName()
}

fun Method.toStructName(): String {
    return "${this.resource().toResourceName()}RequestMethod${this.methodName.exportName()}".exportName()
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
