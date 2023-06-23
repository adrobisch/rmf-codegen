package io.vrap.codegen.languages.rust.model

import io.vrap.codegen.languages.extensions.getSuperTypes
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.extensions.namedSubTypes
import io.vrap.codegen.languages.extensions.sortedByTopology
import io.vrap.codegen.languages.rust.*
import io.vrap.rmf.codegen.di.AllAnyTypes
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.FileProducer
import io.vrap.rmf.codegen.rendering.utils.escapeAll
import io.vrap.rmf.codegen.rendering.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapEnumType
import io.vrap.rmf.codegen.types.VrapScalarType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.*

class RustFileProducer constructor(
    override val vrapTypeProvider: VrapTypeProvider,
    @AllAnyTypes val allAnyTypes: List<AnyType>,
    @BasePackageName val basePackageName: String
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
            .toList() + buildCargoToml() + buildLibRs()
    }

    private fun buildLibRs(): TemplateFile {
        val mods = modules.keys.map { key ->
            "mod ${key};"
        }.toList().joinToString("\n")
        return TemplateFile(mods, "src/lib.rs")
    }

    private fun buildCargoToml(): TemplateFile {
        val content = """[package]
name = "ct-rust-sdk"
version = "0.1.0"
edition = "2021"

[dependencies]
serde = { version = "1", features = ["derive"] }
serde_json = "1"
chrono = { version = "0.4", features = ["serde"] }
""".trimMargin().keepIndentation()

        return TemplateFile(content, "Cargo.toml")
    }

    private fun buildModule(moduleName: String, types: List<AnyType>): TemplateFile {
        val sortedTypes = types.sortedByTopology(AnyType::getSuperTypes)

        val modules = getImports(moduleName, sortedTypes)
            .map {
                "use $it;"
            }

        val importExpr = if (modules.size > 0) modules.joinToString(separator = "\n") else ""

        val content = """
           |$rustGeneratedComment
           |
           |<$importExpr>
           |
           |${sortedTypes.map { it.renderAnyType() }.joinToString(separator = "\n")}
       """.trimMargin().keepIndentation()

        val filename = moduleName.rustModuleFileName()
        return TemplateFile(content, "src/" + filename + ".rs")
    }

    private fun AnyType.renderAnyType(): String {
        return when (this) {
            is ObjectType -> this.renderObjectType()
            is StringType -> this.renderStringType()
            else -> throw IllegalArgumentException("unhandled case ${this.javaClass}")
        }
    }

    private fun getImports(moduleName: String, types: List<AnyType>): List<String> {
        val commonImports = listOf(
            "chrono::prelude::*",
            "std::collections::HashMap"
        )

        return listOf("serde::{Deserialize, Serialize}").plus(commonImports).plus(types.getImportsForModule(moduleName))
    }

    private fun ObjectType.renderObjectType(): String {
        if (this.isMap()) {
            val valueType = if (allProperties.size > 0) allProperties[0].type.renderTypeExpr() else "trait"

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
                |  <${renderStructFields(true)}>
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
                    |  <${it.renderStructFields()}>
                    |}
                    """.trimMargin()
            }
            .joinToString(",\n")
    }

    // Renders the attribute of this model as type annotations.
    private fun ObjectType.renderStructFields(pubFields: Boolean = false): String {
        val pubPrefix = when (pubFields) {
            true -> "pub "
            else -> ""
        }
        return rustStructFields(true)
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
                    |$pubPrefix${name.rustName()}: ${it.type.renderTypeExpr()}""".trimMargin()
                } else {
                    val type = it.type

                    """
                    |<$comment>
                    |#[serde(rename = "${it.name}")]
                    |$pubPrefix${name.rustName()}: Option\<${type.renderTypeExpr()}\>""".trimMargin()
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
                |pub type ${this.name} = ${vrapType.scalarType.rustName()};
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
