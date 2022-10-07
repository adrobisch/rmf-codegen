package io.vrap.codegen.languages.rust.client
import io.vrap.codegen.languages.extensions.getMethodName
import io.vrap.codegen.languages.rust.*
import io.vrap.codegen.languages.rust.exportName
import io.vrap.codegen.languages.rust.goName
import io.vrap.rmf.raml.model.resources.ResourceContainer

fun ResourceContainer.subResources(structName: String): String {
    val pName = if (structName == "Client") "c" else "rb"

    return this.resources
        .map {
            var args = if (!it.relativeUri.variables.isNullOrEmpty()) {
                it.relativeUri.variables
                    .map { it.goName() }
                    .map { "$it string" }
                    .joinToString(separator = ", ")
            } else { "" }

            val assignments =
                it.relativeUri.variables
                    .map { it.goName() }
                    .map { "$it: $it," }
                    .plus(
                        (it.fullUri.variables.asList() - it.relativeUri.variables.asList())
                            .map { it.goName() }
                            .map { "$it: $pName.$it," }
                    )
                    .joinToString(separator = "\n")

            """
            |<${it.toBlockComment()}>
            |func ($pName *$structName) ${it.getMethodName().exportName()}($args) *${it.toStructName()} {
            |    return &${it.toStructName()}{
            |        <$assignments>
            |        client: ${if (structName == "Client") pName else "$pName.client"},
            |    }
            |}
             """.trimMargin()
        }.joinToString(separator = "\n")
}
