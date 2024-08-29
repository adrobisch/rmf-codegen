package io.vrap.codegen.languages.rust.model

import io.vrap.codegen.languages.extensions.getSuperTypes
import io.vrap.codegen.languages.extensions.namedSubTypes
import io.vrap.codegen.languages.extensions.sortedByTopology
import io.vrap.codegen.languages.rust.*
import io.vrap.rmf.codegen.di.AllAnyTypes
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.FileProducer
import io.vrap.rmf.codegen.rendering.utils.escapeAll
import io.vrap.rmf.codegen.rendering.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapEnumType
import io.vrap.rmf.codegen.types.VrapScalarType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.*

class RustModelFileProducer constructor(
    override val vrapTypeProvider: VrapTypeProvider,
    @AllAnyTypes val allAnyTypes: List<AnyType>,
) : RustObjectTypeExtensions, FileProducer {

    val modules =
        allAnyTypes
            .filter { it is ObjectType || (it is StringType && it.pattern == null) }
            .groupBy {
                it.moduleName()
            }

    override fun produceFiles(): List<TemplateFile> {
        return modules
            .map { entry: Map.Entry<String, List<AnyType>> ->
                buildModule(entry.key, entry.value)
            }
            .toList() + buildRootCargoToml() + buildModelCargoToml() + buildClientCargoToml() + buildModelsLibRs()
    }

    private fun buildModelsLibRs(): TemplateFile {
        val mods = modules.keys.map { key ->
            "pub mod ${key};"
        }.toList().joinToString("\n")
        return TemplateFile(mods, "models/src/lib.rs")
    }

    private fun buildRootCargoToml(): TemplateFile {
        val content = """
[workspace]
members = [
   "models",
   "client"
]
""".trimMargin().keepIndentation()
        return TemplateFile(content, "Cargo.toml")
    }

    private fun buildModelCargoToml(): TemplateFile {
        val content = """[package]
name = "ct-rust-sdk-models"
version = "0.1.0"
edition = "2021"

[dependencies]
serde = { version = "1", features = ["derive"] }
serde_json = "1"
chrono = { version = "0.4", features = ["serde"] }
""".trimMargin().keepIndentation()
        return TemplateFile(content, "models/Cargo.toml")
    }

    private fun buildClientCargoToml(): TemplateFile {
        val content = """[package]
name = "ct-rust-sdk-client"
version = "0.1.0"
edition = "2021"

[dependencies]
serde = { version = "1", features = ["derive"] }
serde_json = "1"
chrono = { version = "0.4", features = ["serde"] }
reqwest = { version = "0.12", features = ["json"] }
tokio = { version = "1", features = ["full"] }
thiserror = "1"
ct-rust-sdk-models = { path = "../models" }
""".trimMargin().keepIndentation()
        return TemplateFile(content, "client/Cargo.toml")
    }

    private fun buildModule(moduleName: String, types: List<AnyType>): TemplateFile {
        val sortedTypes = types.sortedByTopology(AnyType::getSuperTypes)

        val modules = types.getImportsForModule(moduleName)
            .map {
                "use $it;"
            }

        val importExpr = if (modules.size > 0) modules.joinToString(separator = "\n") else ""

        val content = """
           |$rustGeneratedComment
           |
           |#[allow(unused_imports)]
           |use serde::{Deserialize, Serialize};
           |#[allow(unused_imports)]
           |use chrono::prelude::*;
           |<$importExpr>
           |
           |${sortedTypes.map { it.renderAnyType() }.joinToString(separator = "\n")}
       """.trimMargin().keepIndentation()

        val filename = moduleName.rustModuleFileName()
        return TemplateFile(content, "models/src/" + filename + ".rs")
    }

    private fun AnyType.renderAnyType(): String {
        return when (this) {
            is ObjectType -> this.renderObjectType()
            is StringType -> this.renderStringType()
            else -> throw IllegalArgumentException("unhandled case ${this.javaClass}")
        }
    }

    private fun ObjectType.renderObjectType(): String {
        if (this.isMap()) {
            val valueType = if (allProperties.size > 0) allProperties.first().type.renderTypeExpr(listOf()) else "serde_json::Value"

            return """
            |<${toBlockComment().escapeAll()}>
            |pub type $name = HashMap\<String, $valueType\>;
            """.trimMargin()
        } else if (this.isDiscriminated()) {
            val interfaceExpr = """
                |<${toBlockComment().escapeAll()}>
                |#[derive(Serialize, Deserialize)]
                |#[serde(tag = "${discriminator}")]
                |pub enum ${name.exportName()} {
                |  <${renderSubtypeEnumStructs()}>
                |}
            """.trimMargin()

            return """
            |$interfaceExpr
            """.trimMargin()
        } else {
            val structField = """
                |<${toBlockComment().escapeAll()}>
                |#[derive(Serialize, Deserialize)]
                |pub struct ${name.exportName()} {
                |  <${renderStructFields(pubFields = true, includeDiscriminator = this.type != null && this.type.isDiscriminated())}>
                |}
            """.trimMargin()

            return """
            |$structField
            """.trimMargin()
        }
    }

    private fun ObjectType.renderSubtypeEnumStructs(): String {
        return this.namedSubTypes()
            .filterIsInstance<ObjectType>()
            .filter { !it.discriminatorValue.isNullOrEmpty() }
            .map {
                """
                    |#[serde(rename = "${it.discriminatorValue}")]
                    |${it.name.exportName()} {
                    |  <${it.renderStructFields(contextTypes = listOf(this))}>
                    |}
                    """.trimMargin()
            }
            .joinToString(",\n")
    }

    // Renders the attribute of this model as type annotations.
    private fun ObjectType.renderStructFields(pubFields: Boolean = false, contextTypes: List<ObjectType> = listOf(), includeDiscriminator: Boolean = false): String {
        val currentType = this
        val pubPrefix = when (pubFields) {
            true -> "pub "
            else -> ""
        }
        val currentContext = contextTypes.plus(currentType)
        return rustStructFields(withParentProperties = true, includeDiscriminator = includeDiscriminator)
            .map {
                val comment: String = it.type.toLineComment().escapeAll()

                val name = when (it.pattern != null) {
                    true -> "value"
                    else -> it.name
                }

                if (it.required) {
                    """
                    |<$comment>
                    |#[serde(rename = "${it.name}")]
                    |$pubPrefix${name.rustName()}: ${it.type.renderTypeExpr(currentContext)}""".trimMargin()
                } else {
                    val type = it.type

                    """
                    |<$comment>
                    |#[serde(rename = "${it.name}")]
                    |$pubPrefix${name.rustName()}: Option\<${type.renderTypeExpr(currentContext)}\>""".trimMargin()
                }
            }
            .joinToString(",\n")
    }

    private fun StringType.renderStringType(): String {
        val vrapType = this.toVrapType()

        return when (vrapType) {
            is VrapEnumType ->
                return """
                |<${toBlockComment().escapeAll()}>
                |#[derive(Serialize, Deserialize)]
                |pub enum ${vrapType.simpleClassName.exportName()} {
                |  <${this.renderEnumValues()}>
                |}
                """.trimMargin()

            is VrapScalarType -> """
                |pub type ${this.name} = ${vrapType.rustTypeName()};
            """.trimMargin()

            else -> ""
        }
    }

    private fun StringType.renderEnumValues(): String {
        return this.enumValues()
            .map { "#[serde(rename = \"${it}\")]\n${it.rustEnumName()}" }
            .joinToString(",\n")
    }

    private fun StringType.enumValues() = enum?.filter { it is StringInstance }
        ?.map { (it as StringInstance).value }
        ?.filterNotNull() ?: listOf()
}
