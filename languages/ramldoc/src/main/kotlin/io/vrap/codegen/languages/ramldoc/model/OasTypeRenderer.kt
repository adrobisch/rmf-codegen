package io.vrap.codegen.languages.ramldoc.model

import com.google.common.collect.Maps
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.*
import io.vrap.codegen.languages.extensions.discriminatorProperty
import io.vrap.codegen.languages.ramldoc.extensions.*
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.Renderer
import io.vrap.rmf.codegen.rendering.utils.keepAngleIndent
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.raml.model.types.*


class OasComposedTypeRenderer constructor(override val api: OpenAPI, override val modelPackageName: String) : OasTypeRenderer<ComposedSchema>(api, modelPackageName)
class OasObjectTypeRenderer constructor(override val api: OpenAPI, override val modelPackageName: String) : OasTypeRenderer<ObjectSchema>(api, modelPackageName)

sealed class OasTypeRenderer<T: Schema<Any>> constructor(open val api: OpenAPI, open val modelPackageName: String) : Renderer<Map.Entry<String, T>> {
    override fun render(type: Map.Entry<String, T>): TemplateFile {
        val typeName = type.key
        val typeVal = type.value
//        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType
//        val properties = type.allProperties
//
//        val postmanExampleAnno = type.getAnnotation("postman-example")
//        val postmanExample = if (postmanExampleAnno != null) {
//            val example = TypesFactory.eINSTANCE.createExample()
//            val boolInstance = TypesFactory.eINSTANCE.createBooleanInstance()
//            boolInstance.value = true
//            example.name = if (type.examples.firstOrNull { e -> e.name.isNullOrEmpty() } != null) "postman" else ""
//            example.strict = boolInstance
//            example
//        } else {
//            null
//        }
//        val examples = type.examples.plus(postmanExample).filterNotNull().sortedWith(compareBy { it.name })
//
//        val content = """
//            |#%RAML 1.0 DataType
//            |displayName: ${typeName}
//            |type: ${type.type ?: "object"}
//            |(builtinType): object${if (type.discriminator != null) """
//            |discriminator: ${type.discriminatorProperty()?.name}""" else ""}${if (type.discriminatorValue.isNullOrBlank().not()) """
//            |discriminatorValue: ${type.discriminatorValue}""" else ""}${if (type.subTypes.filterNot { it.isInlineType }.isNotEmpty()) """
//            |(oneOf):
//            |${type.subTypes.filterNot { it.isInlineType }.sortedWith(compareBy { it.name }).joinToString("\n") { "- ${it.name}" }}""" else ""}${if (type.annotations.isNotEmpty()) """
//            |<<${type.annotations.filterNot { annotation -> annotation.type.name == "postman-example" }.joinToString("\n") { it.renderAnnotation() }}>>""" else ""}${if (examples.isNotEmpty()) """
//            |examples:
//            |  <<${examples.joinToString("\n") { renderExample(vrapType, it) }}>>""" else ""}${if (type.description?.value != null) """
//            |description: |-
//            |  <<${type.description.value.trim()}>>""" else ""}
//            |properties:
//            |  <<${properties.joinToString("\n") { renderProperty(type, it) }}>>
//            """.trimMargin().keepAngleIndent()

        val packageName = typeVal.extensions?.get("x-annotation-package") ?: "Common"
        val content = """
            |#%RAML 1.0 DataType
            |(package): $packageName
            |displayName: $typeName${if (typeVal.description.isNullOrEmpty().not()) """
            |description: |-
            |  <<${typeVal.description}>>
            |""".trimMargin().keepAngleIndent() else ""}
            |${if (typeVal.example != null) """
            |example:
            |  <<${typeVal.example.toJson()}>>""".trimMargin().keepAngleIndent() else ""}
            |${renderSchemaType(typeVal)}
            """.trimMargin().keepAngleIndent()

        return TemplateFile(
                relativePath = "types/$typeName.raml",
                content = content
        )
    }

    private fun Schema<Any>.renderSchemaTypeName(): String {
        val typeVal = this

        if (typeVal is ComposedSchema) {
            if (typeVal.isInheritedObject()) {
                return typeVal.allOf.find { it.`$ref`.isNullOrEmpty().not() }?.renderTypeName() ?: "object"
            }
            return typeVal.renderComposedTypeName()
        }
        return "object"
    }

    private fun renderProperty(name: String, type: Schema<Any>, required: List<String>?): String {
        val required = required?.contains(name) ?: false
        return """
            |$name:
            |${if (type.description.isNullOrEmpty().not()) """
            |  description: |-
            |    <<${type.description}>>""".trimMargin().keepAngleIndent() else ""}${if (type.example != null) """
            |  example:
            |    <<${type.example.toJson()}>>""".trimMargin().keepAngleIndent() else ""}
            |  required: $required
            |  <<${renderSchemaType(type)}>>
        """.trimMargin().keepAngleIndent()
    }
    private fun renderSchemaType(type: Schema<Any>): String {
        return when (type) {
            is ArraySchema -> """
                |(builtinType): array
                |type: array
                |items: 
                |  <<${renderSchemaType(type.items as Schema<Any>)}>>
                """.trimMargin().keepAngleIndent()
            is BinarySchema -> """
                |(builtinType): string
                |type: string
                """.trimMargin()
            is BooleanSchema -> """
                |(builtinType): boolean
                |type: boolean
                """.trimMargin()
            is ByteArraySchema -> """
                |(builtinType): string
                |type: string
                """.trimMargin()
            is ComposedSchema -> {
                if (type.isInheritedObject()) {
                    return """
                        |(builtinType): object
                        |type: ${type.renderSchemaTypeName()}
                        |properties:
                        |  <<${type.allOf.find { schema -> (schema is ObjectSchema || schema is MapSchema) }?.properties()?.joinToString("\n") { entry -> renderProperty(entry.key, entry.value, type.required) }}>>
                    """.trimMargin().keepAngleIndent()
                }
                ""
            }
            is DateSchema -> """
                |(builtinType): date-only
                |type: date-only
                """.trimMargin()
            is DateTimeSchema -> """
                |(builtinType): datetime
                |type: datetime
                """.trimMargin()
            is EmailSchema -> """
                |(builtinType): string
                |type: string
                """.trimMargin()
            is FileSchema -> """
                |type: file
                """.trimMargin()
            is IntegerSchema -> """
                |(builtinType): number
                |type: number${if (type.format != null) """
                |
                |format: ${type.format}""".trimMargin() else ""}
                """.trimMargin()
            is MapSchema -> """
                |type: ${type.renderSchemaTypeName()}
                |(builtinType): object
                |properties:
                |  <<${type.properties().joinToString("\n") { entry -> renderProperty(entry.key, entry.value, type.required) }}>>
                """.trimMargin().keepAngleIndent()
            is NumberSchema -> """
                |(builtinType): number
                |type: number${if (type.format != null) """
                |
                |format: ${type.format}""".trimMargin() else ""}
                """.trimMargin()
            is ObjectSchema -> """
                |type: ${type.renderSchemaTypeName()}
                |(builtinType): object
                |properties:
                |  <<${type.properties().joinToString("\n") { entry -> renderProperty(entry.key, entry.value, type.required) }}>>
                """.trimMargin().keepAngleIndent()
            is PasswordSchema -> """
                |type: string
                |(builtinType): string
                """.trimMargin()
            is StringSchema -> """
                |type: string
                |(builtinType): string
                """.trimMargin()
            is UUIDSchema -> """
                |type: string
                |(builtinType): string
                """.trimMargin()
            else -> {
                if (type.`$ref` != null) {
                    return "type: " + type.renderTypeName()
                } else ""
            }
        }
    }

    private fun Schema<Any>.properties(): Set<MutableMap.MutableEntry<String, Schema<Any>>> {
        val additionalProperties = this.additionalProperties
        return if (this.additionalProperties is Schema<*>) {
            (this.properties?.entries ?: emptySet()).plus(Maps.immutableEntry("//", additionalProperties as Schema<Any>))
        } else {
            this.properties?.entries ?: emptySet()
        }
    }

    private fun renderExample(type: VrapObjectType, example: Example): String {
        val t = if (type.packageDir(modelPackageName).isNotEmpty()) "../.." else ".."
        val exampleName = "${t}/examples/" + type.packageDir(modelPackageName) + type.simpleClassName + "-${if (example.name.isNotEmpty()) example.name else "default"}.json"
        return """
            |${if (example.name.isNotEmpty()) example.name else "default"}:${if (example.displayName != null) """
            |  displayName: ${example.displayName.value.trim()}""" else ""}${if (example.description != null) """
            |  description: |-
            |    <<${example.description.value.trim()}>>""" else ""}${if (example.annotations.isNotEmpty()) """
            |  <<${example.annotations.joinToString("\n") { it.renderAnnotation() }}>>""" else ""}
            |  strict: ${example.strict.value}
            |  value: !include $exampleName
        """.trimMargin()
    }

    private fun renderExample(example: Example): String {
        return """
            |${if (example.name.isNotEmpty()) example.name else "default"}: 
            |  value: ${if (example.value is ObjectInstance) """|-
            |    <<${example.value.toJson()}>>""" else example.value.toJson() }
        """.trimMargin().keepAngleIndent()
    }

//    private fun renderDiscriminatorTypeAsUnion(type: ObjectType): String {
//        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType
//        return """
//            |#%RAML 1.0 DataType
//            |displayName: ${type.displayName?.value ?: vrapType.simpleClassName}
//            |(builtinType): object
//            |type: ${type.subTypes.filterNot { it.isInlineType }.joinToString(" | ") { it.name }}${if (type.discriminatorProperty() != null) """
//            |(oneOf):
//            |${type.subTypes.filterNot { it.isInlineType }.joinToString("\n") { "- ${it.name}" }}""" else ""}
//        """.trimMargin().keepAngleIndent()
//    }

    private fun renderProperty(type: ObjectType, property: Property): String {
        var inherited = false;
        if (type.properties.none { p -> p.name == property.name }) {
            inherited = true;
        }
        val discriminatorProp = type.discriminatorProperty()?.name
        val discriminatorValue = type.discriminatorValue
        val examples = if (property.type.isInlineType) property.type.examples.filterNotNull().sortedWith(compareBy { it.name }) else listOf()
        return """
            |${property.name}:
            |  <<${property.type.renderType()}>>${if (discriminatorProp == property.name && discriminatorValue.isNullOrBlank().not()) """
            |  enum:
            |  - $discriminatorValue""" else if (property.type.isInlineType && property.type.enum.isNotEmpty()) """
            |  enum:
            |  <<${property.type.enum.joinToString("\n") { "- ${it.value}" }}>>""" else ""}${if (examples.isNotEmpty()) """
            |  examples:
            |    <<${examples.joinToString("\n") { renderExample(it) }}>>""" else ""}${if (property.type.default != null) """
            |  default: ${property.type.default.toYaml()}""" else ""}${if (property.type?.isInlineType == true && property.type?.annotations != null) """
            |  <<${property.type.annotations.joinToString("\n") { it.renderAnnotation() }}>>""" else ""}
            |  required: ${property.required}
            |  (inherited): $inherited
        """.trimMargin().keepAngleIndent()
    }

    private fun Schema<Any>.renderType(): String {
        return """
                |type: ${this.renderTypeName()}
                |(builtinType): ${this.renderBuiltinType()}${this.renderExtras()}
            """.trimMargin()
    }

    private fun Schema<Any>.renderExtras(): String {
        return when(this) {
            is StringSchema -> this.renderSchemaExtras()
            is ComposedSchema -> this.renderSchemaExtras()
            else -> ""
        }
    }

    private fun ComposedSchema.renderSchemaExtras(): String {
        var subTypes = this.anyOf?.map { it.renderTypeName() } ?: listOf()
        subTypes = subTypes.plus(this.oneOf?.map { it.renderTypeName() } ?: listOf())

        return """${if (subTypes.isNotEmpty()) """
                |
                |(oneOf):
                |  <<${subTypes.joinToString("\n") { "- $it"}}>>""" else ""}
        """.trimMargin().keepAngleIndent()
    }


    private fun StringSchema.renderSchemaExtras(): String {
        return """${if (this.enum != null && this.enum.size > 0) """
                |enum:
                |  <<${this.enum.joinToString("\n") { "- $it"}}>>""" else ""}
        """.trimMargin().keepAngleIndent()
    }

    private fun Schema<Any>.renderTypeName(): String {
        if (this.`$ref`?.isNotEmpty() == true) {
            return this.`$ref`.replace("#/components/schemas/", "")
        }
        return when (this) {
            is StringSchema -> this.type
            is ObjectSchema -> this.type
            is ArraySchema -> this.type
            is ComposedSchema -> this.renderComposedTypeName()
            else -> ""
        }
    }

    private fun Schema<Any>.renderBuiltinType(): String {
        if (this.`$ref`?.isNotEmpty() == true) {
            val typeName = this.`$ref`.replace("#/components/schemas/", "")
            return api.components.schemas[typeName]?.renderBuiltinType() ?: "any"
        }
        return when (this) {
            is StringSchema -> "string"
            is ObjectSchema -> "object"
            is ArraySchema -> "array"
            is ComposedSchema -> if (this.isInheritedObject()) "object" else "any"
            else -> "any"
        }
    }

    private fun ComposedSchema.isInheritedObject(): Boolean {
        if (this.allOf?.size == 2) {
            if (this.allOf.any { it.`$ref`.isNullOrEmpty().not() } && this.allOf.any { it is ObjectSchema || it is MapSchema }) {
                return true
            }
        }
        return false
    }

    private fun ComposedSchema.renderComposedTypeName(): String {

        return if (this.anyOf?.isNotEmpty() == true) {
            this.anyOf.joinToString(" | ") { it.renderTypeName() }
        } else if (this.allOf?.isNotEmpty() == true) {
            this.allOf.joinToString("," , "[", "]") { it.renderTypeName() }
        } else if (this.oneOf?.isNotEmpty() == true) {
            this.oneOf.joinToString(" | ") { it.renderTypeName() }
        } else {
            "any"
        }
    }
}
