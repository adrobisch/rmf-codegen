package io.vrap.codegen.languages.rust.client

import io.vrap.codegen.languages.rust.*
import io.vrap.codegen.languages.rust.RustObjectTypeExtensions
import io.vrap.codegen.languages.rust.exportName
import io.vrap.codegen.languages.rust.rustName
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

    override fun render(resource: Resource): TemplateFile {
        val filename = resource.rustClientFileName()
        return TemplateFile(
            relativePath = "client/src/$filename.rs",
            content = """|$rustGeneratedComment
                |
                |<${resource.methods()}>
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

    protected fun Resource.methods(): String {
        return this.methods.map { renderMethod(it) }.joinToString(separator = "\n\n")
    }

    private fun Resource.renderMethod(method: Method): String {
        val body = method.firstBody()
        val contentType = method.firstBody()?.contentType?.lowercase()
        val jsonContent = contentType?.startsWith("application/json") == true || contentType?.startsWith("application/graphql") == true
        val useJson = body?.type !is FileType && jsonContent

        val bodyVrapType = method.vrapType()
        val hasBody = bodyVrapType != null && bodyVrapType !is VrapNilType
        val bodyParam = if (hasBody) "payload: ${bodyVrapType?.qualifiedName("ct_rust_sdk_models")}" else ""

        val assignments =
            listOf("mut context: crate::client::ClientContext") +
                    this.relativeUri.variables
                        .map { it.rustName() }
                        .map { "$it: String" }
                        .plus(
                            (this.fullUri.variables.asList() - this.relativeUri.variables.asList().toSet())
                                .map { it.rustName() }
                                .map { "$it: String" }
                        )

        val endpoint = transformUriTemplate(this.fullUri.template)
        val requestName = method.toRequestName()
        val methodName = method.methodName.exportName()

        return """
        |<${method.toBlockComment().escapeAll()}>
        |pub async fn ${methodName}_$requestName(${assignments.joinToString(", ")}${if (hasBody) ", $bodyParam" else "" }) -\> Result\<serde_json::Value, crate::errors::SdkError\> {
        |        let request = context.request(reqwest::Method::${methodName.uppercase()}, ${endpoint}).await?;
        |        let response = request
        |            <${if (useJson) ".json(&payload)" else if (hasBody) ".body(payload)" else ""}>  
        |            .send()
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
            args.add(it.rustName())
        }
        return "format!(\"${pattern}\", ${args.joinToString(", ")})"
    }

    fun Method.vrapType(): VrapType? {
        val bodyType = this.bodyType()
        if (bodyType != null) {
            return bodyType.toVrapType()
        }
        return null
    }
}
