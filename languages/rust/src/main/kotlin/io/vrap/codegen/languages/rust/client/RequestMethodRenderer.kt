package io.vrap.codegen.languages.rust.client

import io.vrap.codegen.languages.rust.*
import io.vrap.codegen.languages.rust.RustObjectTypeExtensions
import io.vrap.codegen.languages.rust.exportName
import io.vrap.codegen.languages.rust.rustName
import io.vrap.codegen.languages.rust.rustTypeName
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.ResourceRenderer
import io.vrap.rmf.codegen.rendering.utils.escapeAll
import io.vrap.rmf.codegen.rendering.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapNilType
import io.vrap.rmf.codegen.types.VrapType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.FileType

class RequestMethodRenderer constructor(
    private val clientConstants: ClientConstants,
    val api: Api,
    override val vrapTypeProvider: VrapTypeProvider,
    @BasePackageName val basePackageName: String
) : ResourceRenderer, RustObjectTypeExtensions {

    override fun render(type: Resource): TemplateFile {
        val filename = type.rustClientFileName()
        return TemplateFile(
            relativePath = "client/src/$filename.rs",
            content = """|$rustGeneratedComment
                |
                |<${type.methods()}>
            """.trimMargin().keepIndentation()
        )
    }

    protected fun Resource.constructor(): String {
        val pathArgs = if (!this.fullUri.variables.isEmpty()) {
            this
                .fullUri
                .variables
                .map { it.rustName() }
                .map { "$it   string" }
                .joinToString(separator = "\n")
        } else { "" }

        return """
            |type ${toStructName()} struct {
            |   <$pathArgs>
            |   client *Client
            |}
            """.trimMargin()
    }

    private fun Resource.importStatement(): String {
        val modules = mutableListOf<String>()

        if (this.methods.size > 0) {
            modules.add("fmt")
        }

        if (this.methods.any { it.bodyType() is FileType }) {
            modules.add("io")
        }

        return modules
            .map { "    \"$it\"" }
            .joinToString(prefix = "import(\n", separator = "\n", postfix = "\n)")
    }

    protected fun Resource.methods(): String {
        return this.methods.map { renderMethod(it) }.joinToString(separator = "\n\n")
    }


    private fun Resource.renderMethod(method: Method): String {
        val bodyVrapType = method.vrapType()
        val methodKwargs = listOf<String>()
            .plus(
                {
                    if (bodyVrapType != null && bodyVrapType !is VrapNilType) "body ${method.vrapType()?.rustTypeName()}" else ""
                }()
            )
            .filter {
                it != ""
            }
            .joinToString(separator = ", ")

        val assignments =
            this.relativeUri.variables
                .map { it.rustName() }
                .map { "$it: String"}
                .plus(
                    (this.fullUri.variables.asList() - this.relativeUri.variables.asList())
                        .map { it.rustName() }
                        .map { "$it: String"}
                )


        val endpoint = transformUriTemplate(this.fullUri.template)
        return """
        |<${method.toBlockComment().escapeAll()}>
        |pub async fn ${method.methodName.exportName()}_${method.toRequestName()}(${assignments.joinToString(", ")}) -\> Result\<serde_json::Value, SdkError\> {
        |        let response = reqwest::${method.methodName.exportName()}(${endpoint})
        |            .await?
        |            .json::\<serde_json::Value\>()
        |            .await?;
        |        Ok(response)
        |}
        """.trimMargin()
    }

    fun transformUriTemplate(template: String): String {
        val regex = "\\{([^}]+)}".toRegex()
        val matches = regex.findAll(template)

        var pattern = template
        val args = mutableListOf<String>()
        matches.map { it.groupValues[1] }.forEach {
            pattern = pattern.replace("{$it}", "{}")
            args.add("${it.rustName()}")
        }
        return "format![\"${pattern}\", ${args.joinToString(", ")}]"
    }

    fun Method.vrapType(): VrapType? {
        val bodyType = this.bodyType()
        if (bodyType != null) {
            return bodyType.toVrapType()
        }
        return null
    }
}
